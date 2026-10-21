package com.tanyi.rag.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tanyi.rag.config.SaaLLMConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 验证故障查询通过多模态模型接口返回检索结果。
 */
@WebFluxTest(RagController.class)
@Import(SaaLLMConfig.class)
class RagWebTest {

    private static final HttpServer MODEL_SERVER = startServer();

    @Autowired
    private WebTestClient webClient;

    @MockitoBean
    private VectorStore vectorStore;

    /**
     * 将模型请求定向到本地替身，避免测试访问云端。
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
     * 查询成功编码时，流式响应包含知识库中的解释。
     */
    @Test
    void knownCodeReturnsText() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("00000 系统OK正确执行后的返回")));

        webClient.mutate()
                .responseTimeout(Duration.ofSeconds(15))
                .build()
                .get()
                .uri("/rag4aiops?msg=00000")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("系统OK正确执行后的返回");
    }

    /**
     * 释放本地模型服务端口。
     */
    @AfterAll
    static void stopServer() {
        MODEL_SERVER.stop(0);
    }

    /**
     * 启动只接受多模态生成请求的模型服务。
     *
     * @return 已启动的本地服务
     * @throws UncheckedIOException 端口绑定失败时抛出
     */
    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", RagWebTest::reply);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 校验模型端点及检索上下文后，返回多模态 SSE 响应。
     *
     * @param exchange 模型 HTTP 请求
     * @throws IOException 请求读取或响应写入失败时抛出
     */
    private static void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            JsonNode request = new ObjectMapper().readTree(exchange.getRequestBody());
            boolean valid = path.equals("/api/v1/services/aigc/multimodal-generation/generation")
                    && request.path("model").asText().equals("qwen3.7-plus")
                    && request.path("input").path("messages").toString().contains("00000")
                    && request.path("input").path("messages").toString().contains("系统OK正确执行后的返回")
                    && "enable".equals(exchange.getRequestHeaders().getFirst("X-DashScope-SSE"));
            String body = valid ? """
                    data: {"request_id":"test-1","output":{"choices":[{"finish_reason":"null","message":{"role":"assistant","content":[{"text":"系统OK"}]}}]},"usage":{"input_tokens":20,"output_tokens":2,"total_tokens":22}}

                    data: {"request_id":"test-1","output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":[{"text":"正确执行后的返回"}]}}]},"usage":{"input_tokens":20,"output_tokens":8,"total_tokens":28}}

                    """ : "{\"code\":\"InvalidParameter\",\"message\":\"url or request error\"}";
            exchange.getResponseHeaders().set("Content-Type",
                    valid ? "text/event-stream;charset=UTF-8" : "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 400, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
