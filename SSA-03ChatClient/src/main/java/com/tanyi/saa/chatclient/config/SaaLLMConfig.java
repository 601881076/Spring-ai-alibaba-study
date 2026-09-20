package com.tanyi.saa.chatclient.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description Saa LLM 配置类
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/
@Configuration
public class SaaLLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;

    /**
     * 构建 dashScopeApi 对象
     * @return
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 构建 chatModel
     * @return
     */
    @Bean
    public ChatModel chatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .build();
    }

    /**
     * 构建统一的 chatClient 方便使用
     * @param dashScopeChatModel 指定 chatModel 为 dashScope
     * @return  chatClient
     */
    @Bean
    public ChatClient chatClient(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel).build();
    }

}





















