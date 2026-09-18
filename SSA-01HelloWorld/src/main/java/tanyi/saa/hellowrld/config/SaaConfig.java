package tanyi.saa.hellowrld.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
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

    @Value("${}")
    private String apiKey;

    /**
     * 初始化 dashScope 协议
     * @return DashScopeApi
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();
    }

    /**
     * 初始化聊天模型
     * 使用 dashScope 的聊天模型.
     * @return  ChatModel
     */
    @Bean
    public ChatModel chatModel() {
        return DashScopeChatModel.builder().dashScopeApi(dashScopeApi()).build();
    }
}



















