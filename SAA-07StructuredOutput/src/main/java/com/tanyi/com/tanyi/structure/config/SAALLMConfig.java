package com.tanyi.com.tanyi.structure.config;

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

@Configuration
public class SAALLMConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;

    // 定义大模型名称
    private final String DEEPSEEK_MODEL = "deepseek-v3";
    private final String QWEN_MODEL = "qwen3.7-plus";

    /**
     * 注入 DashScopeApi
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
     * 注入 DeepseekChatModel
     * @return
     */
    @Bean("deepseekChatModel")
    public ChatModel deepseekChatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .build();
    }

    /**
     * 注入 qwenChatModel
     * @return
     */
    @Bean("qwenChatModel")
    public ChatModel qwenChatModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }


    /**
     * 注入 deepseekChatClient
     * @param deepseekChatModel
     * @return
     */
    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(@Qualifier("deepseekChatModel") ChatModel deepseekChatModel) {
        return ChatClient.builder(deepseekChatModel)
                .defaultOptions(ChatOptions.builder().model(DEEPSEEK_MODEL).build())
                .build();
    }

    /**
     * 注入 qwenChatClient
     * @param qwenChatModel
     * @return
     */
    @Bean("qwenChatClient")
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") ChatModel qwenChatModel) {
        return ChatClient.builder(qwenChatModel)
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build())
                .build();
    }
}


























