package com.tanyi.embedding.controller;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;

/**
 * Description 文本向量化
 * Author
 * Date 2026/10/21
 * Version 1.0
 **/
@RestController
@Slf4j
public class Embed2VectorController {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private VectorStore vectorStore;

    /**
     * 将请求文本转换为向量。
     * http://localhost:8081/text2embed?msg=%E5%B0%84%E9%9B%95%E8%8B%B1%E9%9B%84%E4%BC%A0
     *
     * @param msg 待向量化的文本
     * @return 包含模型生成向量的异步响应
     */
    @GetMapping("/text2embed")
    public Mono<EmbeddingResponse> text2Embed(@RequestParam(name = "msg") String msg) {
        log.info("文本向量化，文本长度={}", msg.length());
        // 同步模型请求使用工作线程，避免阻塞 WebFlux 事件线程。
        Mono<EmbeddingResponse> embeddingResponseMono = Mono.fromCallable(() -> {
            DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                    .model("qwen3.7-text-embedding")
                    .build();
            return embeddingModel.call(new EmbeddingRequest(List.of(msg), options));
        }).subscribeOn(Schedulers.boundedElastic());
        return embeddingResponseMono.doOnNext(response -> {
            float[] vector = response.getResult().getOutput();
            log.info("向量数据：{}", Arrays.toString(vector));
        });
    }

    /**
     * 将请求文本转换为向量并写入 Redis。
     * http://localhost:8081/embed2vector/add?msg=射雕英雄传
     *
     * @param msg 待保存的文本
     * @return 写入完成的异步信号
     */
    @GetMapping("/embed2vector/add")
    public Mono<Void> add(@RequestParam(name = "msg") String msg) {
        log.info("写入向量文档，文本长度={}", msg.length());
        // 向量生成和 Redis 写入均为同步调用，使用工作线程执行。
        return Mono.<Void>fromRunnable(() -> {
            List<Document> documents = List.of(new Document(msg));
            vectorStore.add(documents);
            log.info("向量文档写入完成，文档数={}", documents.size());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 根据查询文本检索相似文档。
     * http://localhost:8081/embed2vector/get?msg=牛蛙
     * @param msg 用于语义检索的文本
     * @return 按相似度匹配的文档列表
     */
    @GetMapping("/embed2vector/get")
    public Mono<List<Document>> getAll(@RequestParam(name = "msg") String msg) {
        log.info("检索相似文档，查询文本长度={}", msg.length());
        // 查询向量生成和 Redis 检索使用工作线程，避免阻塞事件线程。
        return Mono.fromCallable(() -> {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(msg)
                    .topK(2)
                    .build();
            return vectorStore.similaritySearch(searchRequest);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
