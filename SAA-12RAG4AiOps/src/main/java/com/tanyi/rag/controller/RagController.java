package com.tanyi.rag.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @auther zzyybs@126.com
 * @create 2025-07-30 12:21
 * @Description TODO
 */
@RestController
public class RagController {

    @Resource(name = "qwenChatClient")
    private ChatClient chatClient;

    @Resource
    private VectorStore vectorStore;

    /**
     * http://localhost:8012/rag4aiops?msg=00000
     * http://localhost:8012/rag4aiops?msg=C2222
     *
     * @param msg
     * @return
     */
    @GetMapping("/rag4aiops")
    public Flux<String> rag(@RequestParam(name = "msg") String msg) {
        // 系统提示词：约束模型角色为运维工程师，要求依据检索到的编码给出故障解释，找不到时明确回复"找不到信息"
        // 该约束可防止模型在没有检索到相关内容时胡编乱造，降低幻觉风险
        String systemInfo = """
                你是一个运维工程师,按照给出的编码给出对应故障解释,否则回复找不到信息。
                """;

        // 构建 RAG（检索增强生成）增强器，负责在调用大模型前自动完成"检索相关文档 → 拼接到提示词"的流程
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                // 指定文档检索器：告诉增强器从哪里、以何种方式检索上下文
                .documentRetriever(
                        VectorStoreDocumentRetriever.builder()
                                // 绑定向量数据库，将用户问题向量化后在其中做相似度检索
                                .vectorStore(vectorStore)
                                .build()
                )
                .build();

        // 组装并执行一次对话请求
        return chatClient.prompt()
                // 设置系统角色，约束模型行为与回答口径
                .system(systemInfo)
                // 传入用户的实际问题（如故障编码 C2222）
                .user(msg)
                // 挂载 RAG 增强器：请求发送前会先去向量数据库检索相关文档，并将结果作为上下文注入提示词
                .advisors(advisor) // RAG功能,向量数据库查询
                // 以流式方式调用模型，逐个 token 返回，适合前端 SSE 实时展示
                .stream()
                // 从流式响应中提取纯文本内容，返回 Flux<String> 给前端
                .content();
    }
}
