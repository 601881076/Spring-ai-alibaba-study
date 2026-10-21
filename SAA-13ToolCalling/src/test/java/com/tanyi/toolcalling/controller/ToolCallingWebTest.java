package com.tanyi.toolcalling.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.FluxExchangeResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证时间查询通过多模态接口完成工具调用与最终回答。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ToolCallingWebTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpServer MODEL_SERVER = startServer();
    private static final CountDownLatch FIRST_TEXT = new CountDownLatch(1);

    @Autowired
    private WebTestClient webClient;

    /**
     * 将模型请求定向到本地服务，使用与应用一致的多模态模型配置。
     *
     * @param registry 测试配置注册器
     */
    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.dashscope.base-url",
                () -> "http://127.0.0.1:" + MODEL_SERVER.getAddress().getPort());
        registry.add("spring.ai.dashscope.api-key", () -> "test-key");
        registry.add("spring.ai.dashscope.chat.options.model", () -> "qwen3.7-plus");
        registry.add("spring.ai.dashscope.chat.options.multi-model", () -> true);
    }

    /**
     * 默认问题触发时间工具，首段文本在模型结束前送达，响应仅包含回答文本。
     */
    @Test
    void defaultChatUsesTimeTool() {
        FluxExchangeResult<String> response = webClient.mutate()
                .responseTimeout(Duration.ofSeconds(15))
                .build()
                .get()
                .uri("/toolcall/chat")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        Iterator<String> texts = response.getResponseBody()
                .timeout(Duration.ofSeconds(10))
                .toIterable().iterator();
        try {
            assertThat(texts.next()).isEqualTo("当前时间：");
        } finally {
            FIRST_TEXT.countDown();
        }
        assertThat(texts.next()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*");
        assertThat(texts.hasNext()).isFalse();
    }

    /**
     * 释放本地模型服务端口。
     */
    @AfterAll
    static void stopServer() {
        MODEL_SERVER.stop(0);
    }

    /**
     * 启动模拟云端模型的 HTTP 服务。
     *
     * @return 已启动的本地模型服务
     * @throws UncheckedIOException 端口绑定失败时抛出
     */
    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ToolCallingWebTest::reply);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 多模态端点接收工具声明或执行结果，错误端点返回与云端一致的 400。
     *
     * @param exchange 模型 HTTP 请求
     * @throws IOException 请求读取或响应写入失败时抛出
     */
    private static void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            boolean valid = exchange.getRequestURI().getPath()
                    .equals("/api/v1/services/aigc/multimodal-generation/generation")
                    && request.path("model").asText().equals("qwen3.7-plus")
                    && request.path("parameters").path("tools").toString().contains("getCurrentTime")
                    && "enable".equals(exchange.getRequestHeaders().getFirst("X-DashScope-SSE"));
            String body = valid ? modelResponse(request)
                    : "{\"code\":\"InvalidParameter\",\"message\":\"url error, please check url\"}";
            exchange.getResponseHeaders().set("Content-Type",
                    valid ? "text/event-stream;charset=UTF-8" : "application/json");
            exchange.sendResponseHeaders(valid ? 200 : 400, 0);
            for (String event : body.stripTrailing().split("\n\n")) {
                exchange.getResponseBody().write((event + "\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                if (event.contains("\"text\":\"当前时间：\"")) {
                    awaitFirstText();
                }
            }
        }
    }

    /**
     * 等待客户端收到首段文本后再发送剩余内容，验证接口没有聚合整个响应。
     *
     * @throws IOException 客户端未及时收到首段文本或等待被中断时抛出
     */
    private static void awaitFirstText() throws IOException {
        try {
            if (!FIRST_TEXT.await(5, TimeUnit.SECONDS)) {
                throw new IOException("客户端未在模型结束前收到首段文本");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(exception);
        }
    }

    /**
     * 首轮请求调用时间工具，后续请求根据工具结果返回回答。
     *
     * @param request 模型请求体
     * @return 模型的 SSE 响应
     * @throws IOException 工具结果不是有效 JSON 时抛出
     */
    private static String modelResponse(JsonNode request) throws IOException {
        for (JsonNode message : request.path("input").path("messages")) {
            if (message.path("role").asText().equals("tool")) {
                assertThat(message.path("tool_call_id").asText()).isEqualTo("clock-1");
                String time = JSON.readTree(message.path("content").asText()).asText();
                assertThat(LocalDateTime.parse(time)).isNotNull();
                return """
                        data: {"request_id":"answer-1","output":{"choices":[{"finish_reason":"null","message":{"role":"assistant","content":[{"text":"当前时间："}]}}]},"usage":{"input_tokens":20,"output_tokens":2,"total_tokens":22}}

                        data: {"request_id":"answer-1","output":{"choices":[{"finish_reason":"null","message":{"role":"assistant","content":[{"text":"%s"}]}}]},"usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}}

                        data: {"request_id":"answer-1","output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":[]}}]},"usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}}

                        """.formatted(time);
            }
        }
        return """
                data: {"request_id":"call-1","output":{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":[],"tool_calls":[{"index":0,"id":"clock-1","type":"function","function":{"name":"getCurrentTime","arguments":"{}"}}]}}]},"usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}}

                """;
    }
}
