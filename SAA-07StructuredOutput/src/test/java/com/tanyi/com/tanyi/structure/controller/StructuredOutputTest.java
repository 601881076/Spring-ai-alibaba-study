package com.tanyi.com.tanyi.structure.controller;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanyi.com.tanyi.structure.config.SAALLMConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 验证结构化输出接口在真实 Netty 请求线程中可以正常响应。
 */
class StructuredOutputTest {

    private static final String STUDENT_JSON = """
            {"id":"1","name":"测试学生","major":"计算机科学与技术","email":"student@example.com"}
            """;

    /**
     * 经由 HTTP 请求运行真实 Agent 和模型配置，仅替换外部 DashScope 服务。
     */
    @Test
    void chatReturnsStudent() throws JsonProcessingException {
        DisposableServer provider = startProvider();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.ai.dashscope.api-key", "test-key",
                    "spring.ai.dashscope.base-url", "http://127.0.0.1:" + provider.port(),
                    "spring.ai.dashscope.chat.options.multi-model", true)));
            context.register(WebConfig.class);
            context.refresh();
            var handler = WebHttpHandlerBuilder.applicationContext(context).build();
            // 使用真实 Netty 事件循环，避免普通测试线程掩盖阻塞调用问题。
            DisposableServer server = HttpServer.create().host("127.0.0.1").port(0)
                    .handle(new ReactorHttpHandlerAdapter(handler)).bindNow();
            try {
                WebTestClient.bindToServer()
                        .baseUrl("http://127.0.0.1:" + server.port())
                        .responseTimeout(Duration.ofSeconds(10))
                        .build()
                        .get()
                        .uri(builder -> builder.path("/structuredoutput/chat")
                                .queryParam("name", "测试学生")
                                .queryParam("email", "student@example.com")
                                .build())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody().json(STUDENT_JSON);
            } finally {
                server.disposeNow();
            }
        } finally {
            provider.disposeNow();
        }
    }

    /**
     * 按 Qwen 多模态 API 契约响应，文本端点返回与线上一致的 URL 错误。
     */
    private DisposableServer startProvider() throws JsonProcessingException {
        String response = new ObjectMapper().writeValueAsString(Map.of(
                "request_id", "test-request",
                "output", Map.of("choices", List.of(Map.of(
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant",
                                "content", List.of(Map.of("text", STUDENT_JSON)))))),
                "usage", Map.of("input_tokens", 10, "output_tokens", 20, "total_tokens", 30)));
        return HttpServer.create().host("127.0.0.1").port(0)
                .route(routes -> routes
                        .post("/api/v1/services/aigc/multimodal-generation/generation",
                                (request, reply) -> reply.header("Content-Type", "application/json")
                                        .sendString(Mono.just(response)))
                        .post("/api/v1/services/aigc/text-generation/generation",
                                (request, reply) -> reply.status(400)
                                        .header("Content-Type", "application/json")
                                        .sendString(Mono.just("""
                                                {"code":"InvalidParameter","message":"url error, please check url！"}
                                                """))))
                .bindNow();
    }

    /**
     * 加载接口及生产模型配置，将服务地址指向本地模拟服务。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import({StructuredOutputController.class, SAALLMConfig.class})
    static class WebConfig {
    }
}
