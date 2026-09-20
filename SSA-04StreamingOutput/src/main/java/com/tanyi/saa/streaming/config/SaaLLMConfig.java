package com.tanyi.saa.streaming.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description 配置类
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/
@Configuration
public class SaaLLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 设置 LLM 大模型
    private final String DEEPSEEK_MODEL = "deepseek-v3";
    private final String QWEN_MODEL = "qwen-plus";

    // ************************ 使用 ChatModel 同时存在多模型 ********************
    /**
     * 注入 deepSeek ChatModel
     * @return
     */
    @Bean(name = "deepseek")
    public ChatModel deepSeek() {
        return DashScopeChatModel.builder()
                // 设置api-key
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                // 设置 chatModel 调用哪个 LLM
                .defaultOptions(DashScopeChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .build();
    }

    /**
     * 注入 qwen ChatModel
     * @return
     */
    @Bean(name = "qwen")
    public ChatModel qwen() {
        return DashScopeChatModel.builder()
                // 设置调用 apiKey
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                // 设置 ChatModel 调用哪个 LLM
                .defaultOptions(DashScopeChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }

    // ************************ 使用 ChatClient 同时存在多模型 ********************
    /**
     * 注入 deepseek chatClient
     * @return
     */
    @Bean
    public ChatClient deepSeekChatClient(@Qualifier("deepseek") ChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .defaultOptions(DashScopeChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .build();
    }

    /**
     * 注入 qwen chatClient
     * @param qwenChatModel
     * @return
     */
    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwen") ChatModel qwenChatModel) {
        return ChatClient.builder(qwenChatModel)
                .defaultOptions(DashScopeChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }
}
