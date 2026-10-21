package com.tanyi.rag.config;

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
 * @auther zzyybs@126.com
 * @create 2025-07-25 18:53
 * @Description ChatModel+ChatClient+多模型共存
 */
@Configuration
public class SaaLLMConfig {

    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    // 模型名称常量定义
    private final String DEEPSEEK_MODEL = "deepseek-v3";
    @Value("${spring.ai.dashscope.chat.options.model}")
    private String qwenModel;

    @Value("${spring.ai.dashscope.chat.options.multi-model:false}")
    private boolean multiModel;

    @Bean(name = "deepseek")
    public ChatModel deepSeek() {
        return DashScopeChatModel.builder()
                .dashScopeApi(
                        DashScopeApi.builder()
                                .baseUrl(baseUrl)
                                .apiKey(apiKey)
                                .build()
                )
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .model(DEEPSEEK_MODEL)
                                .build()
                )
                .build();
    }

    /**
     * 按配置创建通义模型，选择对应的生成端点。
     *
     * @return 通义对话模型
     */
    @Bean(name = "qwen")
    public ChatModel qwen() {
        DashScopeApi api = DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(qwenOptions())
                .build();
    }

    @Bean(name = "deepseekChatClient")
    public ChatClient deepseekChatClient(
            @Qualifier("deepseek") ChatModel deepSeek) {
        return ChatClient.builder(deepSeek)
                .defaultOptions(
                        ChatOptions.builder()
                                .model(DEEPSEEK_MODEL)
                                .build()
                )
                .build();
    }

    /**
     * 创建保留 DashScope 专有参数的通义客户端。
     *
     * @param qwen 通义对话模型
     * @return 故障查询使用的客户端
     */
    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(
            @Qualifier("qwen") ChatModel qwen) {
        // 通用 ChatOptions 在当前版本合并时会将 multiModel 覆盖为 false。
        return ChatClient.builder(qwen)
                .defaultOptions(qwenOptions())
                .build();
    }

    /**
     * 读取通义模型名称与端点类型，供模型和客户端使用。
     *
     * @return 通义模型请求参数
     */
    private DashScopeChatOptions qwenOptions() {
        return DashScopeChatOptions.builder()
                .model(qwenModel)
                .multiModel(multiModel)
                .build();
    }
}
