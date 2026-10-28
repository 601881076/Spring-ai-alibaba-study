package com.tanyi.saa.streaming.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.DelegatingWebFluxConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 DashScope 客户端和本地模型服务验证 SSE 完成事件。
 *
 * <p>调用链为 WebTestClient → Controller → ChatClient → DashScopeChatModel → 本地 HTTP 服务。
 * 测试使用真实 SDK 解析模型尾包，再检查接口输出，覆盖结束原因的映射与 SSE 编码。
 * 本地服务监听随机端口，模型请求不会发往百炼，也不需要真实密钥。
 */
class StreamOutputWebTest {

    /**
     * 模型响应解析失败时保留已有正文，并以失败事件结束回答。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void errorSendsFailure() throws IOException {
        String body = modelChunk("部分回答", "null") + "data: {invalid-json}\n\n";
        List<ServerSentEvent<String>> events = requestEvents(body);

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta", "failure");
        assertThat(events).extracting(ServerSentEvent::data)
                .containsExactly("部分回答", "模型调用失败，回答未完成，请重新尝试");
    }

    /**
     * 正文逐段返回后，正常结束标志产生唯一的 done 事件。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void stopSendsDone() throws IOException {
        // 前两包仍在生成，最后一包仅携带 stop；即使尾包没有正文，也必须识别正常结束。
        List<ServerSentEvent<String>> events = requestEvents(
                modelChunk("你好 ", "null") + modelChunk("世界", "null") + modelChunk("", "stop"));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta", "delta", "done");
        // 正文空格应原样保留；SDK 把原生 stop 映射为大写 STOP，并由 done 事件带回。
        assertThat(events).extracting(ServerSentEvent::data).containsExactly("你好 ", "世界", "STOP");
    }

    /**
     * 输出长度达到上限时保留部分正文，不发送完成事件。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void lengthDoesNotSendDone() throws IOException {
        List<ServerSentEvent<String>> events = requestEvents(modelChunk("部分回答", "length"));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta");
        assertThat(events.getFirst().data()).isEqualTo("部分回答");
    }

    /**
     * HTTP 响应结束但缺少模型结束标志时，不误报完成。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void missingStopDoesNotSendDone() throws IOException {
        // 服务端正常关闭 HTTP 响应，但最后仍是生成中标志，用来区分传输结束与模型完成。
        List<ServerSentEvent<String>> events = requestEvents(modelChunk("部分回答", "null"));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta");
    }

    /**
     * 重复出现正常结束尾包时，也只发送一次完成事件。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void repeatedStopSendsOneDone() throws IOException {
        // 正文包和空尾包都带 stop，完成事件仍应由整条流的完成分支统一发送一次。
        List<ServerSentEvent<String>> events = requestEvents(
                modelChunk("回答", "stop") + modelChunk("", "stop"));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta", "done");
    }

    /**
     * 正常结束后的用量尾包不会清除先前保存的结束原因。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void usageKeepsFinishReason() throws IOException {
        // choices 为空的尾包只有用量，不应清除前一个回答片段已经提供的 STOP。
        String usage = """
                data: {"request_id":"test-1","output":{"choices":[]},"usage":{"input_tokens":5,"output_tokens":2,"total_tokens":7}}

                """;
        List<ServerSentEvent<String>> events = requestEvents(modelChunk("回答", "stop") + usage);

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta", "done");
        assertThat(events.getLast().data()).isEqualTo("STOP");
    }

    /**
     * 完全没有响应片段时，不虚构正常结束事件。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void emptyDoesNotSendDone() throws IOException {
        assertThat(requestEvents("")).isEmpty();
    }

    /**
     * 在 HTTP 边界提供模拟模型响应，收集聊天接口返回的 SSE 事件。
     *
     * <p>每个用例独立创建服务和 Spring 上下文；实际模型 Bean 指向本地服务。
     * WebTestClient 绑定内存中的 WebFlux 处理链，不启动应用监听端口。
     * collectList 仅用于测试汇总断言，生产接口仍按片段发送事件。
     *
     * @param body 模型服务的 SSE 响应体
     * @return 聊天接口发送的事件
     * @throws IOException 本地模型服务启动失败时抛出
     */
    private List<ServerSentEvent<String>> requestEvents(String body) throws IOException {
        // 由客户端消费 delta 时释放，供服务端确认首段正文已实际到达响应下游。
        CountDownLatch firstDelta = new CountDownLatch(1);
        // 端口 0 由系统分配空闲端口，减少测试之间的端口冲突。
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                // 消费请求体后再写响应，避免未读取的请求数据干扰本次 HTTP 交互。
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                for (String event : body.stripTrailing().split("\n\n")) {
                    // SSE 用空行分隔事件；每段发送后刷新，模拟逐段生成而不是一次性返回。
                    exchange.getResponseBody().write((event + "\n\n").getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    if (event.contains("\"content\":\"你好 \"")) {
                        // 正常用例暂停在首包，客户端收到正文后才允许模型继续发送剩余包和 STOP。
                        awaitFirstDelta(firstDelta);
                    }
                }
            }
        });
        server.start();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 使用测试密钥和本地地址，仍经过真实 DashScope API 的请求构造与响应解析。
            DashScopeApi api = DashScopeApi.builder()
                    .apiKey("test-key")
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .build();
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .model("qwen-plus")
                    .build();
            DashScopeChatModel model = DashScopeChatModel.builder()
                    .dashScopeApi(api)
                    .defaultOptions(options)
                    .build();
            // Controller 同时声明多个模型和客户端依赖，测试中全部绑定到同一个本地模型。
            context.registerBean("qwen", ChatModel.class, () -> model);
            context.registerBean("deepseek", ChatModel.class, () -> model);
            context.registerBean("qwenChatClient", ChatClient.class, () -> ChatClient.builder(model).build());
            context.registerBean("deepSeekChatClient", ChatClient.class, () -> ChatClient.builder(model).build());
            // 加载 WebFlux 的路由和编解码支持，验证接口实际发出的具名 SSE 事件。
            context.register(DelegatingWebFluxConfiguration.class, StreamOutputController.class);
            context.refresh();
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            // 泛型类型信息让客户端把响应解码为事件对象，保留 event 和 data 供断言。
            ParameterizedTypeReference<ServerSentEvent<String>> eventType = new ParameterizedTypeReference<>() { };
            return client.get()
                    .uri("/stream/chatflux4?question=hello")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                    .returnResult(eventType)
                    .getResponseBody()
                    .doOnNext(event -> {
                        if ("delta".equals(event.event())) {
                            // 只在正文事件到达时通知服务端，不能让仅有响应头的情况通过流式验证。
                            firstDelta.countDown();
                        }
                    })
                    .collectList()
                    // 测试设置等待上限，防止异常链路导致用例一直挂起；这不是生产超时配置。
                    .block(Duration.ofSeconds(10));
        } finally {
            // try-with-resources 先关闭 Spring 上下文，再释放本地服务端口。
            server.stop(0);
        }
    }

    /**
     * 在发送模型尾包前等待首段文字抵达客户端，验证接口没有聚合整段回答。
     *
     * @param firstDelta 客户端首段正文到达信号
     * @throws IOException 首段正文未及时到达或等待被中断时抛出
     */
    private void awaitFirstDelta(CountDownLatch firstDelta) throws IOException {
        try {
            // 五秒是测试中的首段到达上限；超时代表正文被缓冲或响应链路没有推进。
            if (!firstDelta.await(5, TimeUnit.SECONDS)) {
                throw new IOException("客户端未在模型结束前收到正文");
            }
        } catch (InterruptedException exception) {
            // 恢复线程的中断标志，再将中断转换为 HTTP 处理函数可以抛出的 IOException。
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    /**
     * 构造 DashScope 原生流式响应，结束原因由测试用例指定。
     *
     * <p>reason 使用原生协议的 null、stop 或 length 字符串，由真实 SDK 转换为元数据。
     * text 直接填入 JSON 字符串，仅适用于当前不含引号和控制字符的固定测试文本。
     *
     * @param text 本段回答正文
     * @param reason 模型结束原因
     * @return 一条模型 SSE 事件
     */
    private String modelChunk(String text, String reason) {
        return """
                data: {"request_id":"test-1","output":{"choices":[{"finish_reason":"%s","message":{"role":"assistant","content":"%s"}}]},"usage":{"input_tokens":5,"output_tokens":2,"total_tokens":7}}

                """.formatted(reason, text);
    }
}
