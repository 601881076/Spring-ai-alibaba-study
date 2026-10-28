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
 */
class StreamOutputWebTest {

    /**
     * 正文逐段返回后，正常结束标志产生唯一的 done 事件。
     *
     * @throws IOException 本地模型服务启动失败时抛出
     */
    @Test
    void stopSendsDone() throws IOException {
        List<ServerSentEvent<String>> events = requestEvents(
                modelChunk("你好 ", "null") + modelChunk("世界", "null") + modelChunk("", "stop"));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("delta", "delta", "done");
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
     * @param body 模型服务的 SSE 响应体
     * @return 聊天接口发送的事件
     * @throws IOException 本地模型服务启动失败时抛出
     */
    private List<ServerSentEvent<String>> requestEvents(String body) throws IOException {
        CountDownLatch firstDelta = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);
                for (String event : body.stripTrailing().split("\n\n")) {
                    exchange.getResponseBody().write((event + "\n\n").getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    if (event.contains("\"content\":\"你好 \"")) {
                        awaitFirstDelta(firstDelta);
                    }
                }
            }
        });
        server.start();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
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
            context.registerBean("qwen", ChatModel.class, () -> model);
            context.registerBean("deepseek", ChatModel.class, () -> model);
            context.registerBean("qwenChatClient", ChatClient.class, () -> ChatClient.builder(model).build());
            context.registerBean("deepSeekChatClient", ChatClient.class, () -> ChatClient.builder(model).build());
            context.register(DelegatingWebFluxConfiguration.class, StreamOutputController.class);
            context.refresh();
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
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
                            firstDelta.countDown();
                        }
                    })
                    .collectList()
                    .block(Duration.ofSeconds(10));
        } finally {
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
            if (!firstDelta.await(5, TimeUnit.SECONDS)) {
                throw new IOException("客户端未在模型结束前收到正文");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    /**
     * 构造 DashScope 原生流式响应，结束原因由测试用例指定。
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
