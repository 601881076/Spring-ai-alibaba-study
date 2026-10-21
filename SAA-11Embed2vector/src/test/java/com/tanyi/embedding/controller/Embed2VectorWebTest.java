package com.tanyi.embedding.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 验证文本向量接口可在 WebFlux 请求中调用同步模型和向量存储。
 */
@WebFluxTest(Embed2VectorController.class)
class Embed2VectorWebTest {

    @Autowired
    private WebTestClient webClient;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private VectorStore vectorStore;

    /**
     * 同步模型调用完成后，接口返回生成的向量。
     */
    @Test
    void textEmbedReturnsVector() {
        EmbeddingResponse response = new EmbeddingResponse(
                List.of(new Embedding(new float[]{0.25f, 0.5f}, 0)));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation ->
                Mono.just(response).delayElement(Duration.ofMillis(1)).block());

        webClient.get()
                .uri(builder -> builder.path("/text2embed").queryParam("msg", "射雕英雄传").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.output[0]").isEqualTo(0.25)
                .jsonPath("$.result.output[1]").isEqualTo(0.5);
    }

    /**
     * 同步向量存储完成写入后，接口正常结束响应。
     */
    @Test
    void addCompletesWithBlocking() {
        doAnswer(invocation -> {
            Mono.delay(Duration.ofMillis(1)).block();
            return null;
        }).when(vectorStore).add(anyList());

        webClient.get()
                .uri("/embed2vector/add")
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();
    }

    /**
     * 同步向量检索完成后，接口返回匹配文档。
     */
    @Test
    void searchReturnsDocuments() {
        List<Document> documents = List.of(new Document("i love java"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation ->
                Mono.just(documents).delayElement(Duration.ofMillis(1)).block());

        webClient.get()
                .uri(builder -> builder.path("/embed2vector/get").queryParam("msg", "java").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].text").isEqualTo("i love java");
    }
}
