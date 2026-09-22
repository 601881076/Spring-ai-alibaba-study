package com.tanyi.prompttemplate.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description TODO
 * Author
 * Date 2026/9/22
 * Version 1.0
 **/
@Configuration
public class SAALLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 设置模型变量
    private final String DEEPSEEK_MODEL = "deepseek-v3";
    private final String QWEN_MODEL = "qwen-plus";

    /**
     * 注入 dashScopeApi 配置类
     * @return
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

    /**
     * 注入 deepseek chatModel
     * @return
     */
    @Bean(name = "deepseek")
    public ChatModel deepseek() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .build();
    }

    /**
     * 注入 qwen chatModel
     * @return
     */
    @Bean(name = "qwen")
    public ChatModel qwen() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }

    /**
     * 注入 deepseek chatClient
     * @return
     */
    @Bean
    public ChatClient deepseekChatClient(@Qualifier("deepseek") ChatModel deepseekChatModel) {
        return ChatClient.builder(deepseekChatModel)
                .defaultOptions(ChatOptions.builder().model(DEEPSEEK_MODEL).build())
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
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }









}
