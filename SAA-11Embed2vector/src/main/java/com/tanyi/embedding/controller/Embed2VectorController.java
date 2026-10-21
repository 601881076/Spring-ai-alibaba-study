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
     * 文本向量化
     * http://localhost:8011/text2embed?msg=射雕英雄传
     *
     * @param msg
     * @return
     */
    @GetMapping("/text2embed")
    public EmbeddingResponse text2Embed(String msg) {
        // EmbeddingResponse embeddingResponse = embeddingModel.call(new EmbeddingRequest(List.of(msg), null));
        EmbeddingResponse embeddingResponse = embeddingModel.call(
                new EmbeddingRequest(
                        List.of(msg),
                        DashScopeEmbeddingOptions.builder()
                                .model("qwen3.7-text-embedding")
                                .build()
                )
        );

        System.out.println(Arrays.toString(embeddingResponse.getResult().getOutput()));
        return embeddingResponse;
    }

    /**
     * 将向量添加到 向量数据库中
     */
    @GetMapping("/embed2vector/add")
    public void add() {
        List<Document> documents = List.of(
                new Document("i study LLM"),
                new Document("i love java")
        );

        vectorStore.add(documents);
    }

    /**
     * 从向量数据库中获取向量
     * @param msg
     * @return
     */
    @GetMapping("/embed2vector/get")
    public List getAll(@RequestParam(name = "msg") String msg) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(msg)
                .topK(2)
                .build();

        List<Document> list = vectorStore.similaritySearch(searchRequest);
        System.out.println(list);

        return list;
    }
}
