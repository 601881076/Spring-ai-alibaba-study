package tanyi.saa.hellowrld.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description config
 * Author
 * Date 2026/9/18
 * Version 1.0
 **/
@Configuration
public class SaaConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;
    @Value("${spring.ai.dashscope.chat.options.model}")
    private String model;
    @Value("${spring.ai.dashscope.chat.options.multi-model:false}")
    private boolean multiModel;

    /**
     * 初始化 dashScope 协议
     * @return DashScopeApi
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * 初始化聊天模型
     * 使用 dashScope 的聊天模型.
     * @return  ChatModel
     */
    @Bean
    public ChatModel chatModel() {
        // 手动创建模型时需显式传入配置，否则会使用 SDK 默认的 qwen-plus。
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                // 多模态模型需要匹配的端点和消息格式，即使本次只输入文本。
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(model)
                        .multiModel(multiModel)
                        .build())
                .build();
    }
}
















