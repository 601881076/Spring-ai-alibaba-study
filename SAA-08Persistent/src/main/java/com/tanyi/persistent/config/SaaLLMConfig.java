package com.tanyi.persistent.config;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 持续对话与持久化 配置类
 */
@Slf4j
@Configuration
public class SaaLLMConfig {

    @Value("${spring.ai.dashscope.base-url}")
    private String baseUrl;
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    // 定义大模型名称
    private final String DEEPSEEK_MODEL = "deepseek-v3";
    private final String QWEN_MODEL = "qwen3.7-plus";

    /**
     * 创建 dashScopeApi
     * @return  DashScopeApi
     */
    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    /**
     * 注入 deepseek ChatModel
     * @return
     */
    @Bean(name = "deepseek")
    public ChatModel deepseek() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(DEEPSEEK_MODEL).build()) // 指定LLM模型
                .build();
    }


    /**
     * 注入 qwen ChatModel
     * @return  ChatModel
     */
    @Bean(name = "qwen")
    public ChatModel qwen() {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi())
                .defaultOptions(DashScopeChatOptions.builder().model(QWEN_MODEL).build()) // 指定 LLM 模型位 qwen
                .build();
    }

    /**
     * 注入 qwen chat client
     *
     * 给 client 中配置 MessageChatMemory 记忆存储，以实现连续对话
     * @param qwen 使用 Qwen 模型的 ChatModel
     * @param JedisRedisChatMemoryRepository 使用 Redis 持久化对话内容的仓库
     * @return 配置了模型参数和持久化记忆能力的 ChatClient
     */
    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(@Qualifier("qwen") ChatModel qwen, JedisRedisChatMemoryRepository JedisRedisChatMemoryRepository) {


        // 构建基于 Redis 的窗口记忆，用于保存连续对话上下文
        MessageWindowChatMemory windowChatMemory = MessageWindowChatMemory.builder() // 创建窗口记忆构造器
                .chatMemoryRepository(JedisRedisChatMemoryRepository) // 指定 Redis 对话记忆仓库
                .maxMessages(10) // 限制单个会话最多保留 10 条消息
                .build(); // 完成窗口记忆对象构建

        // 构建 Qwen ChatClient，并注册默认模型参数和对话记忆顾问
        return ChatClient.builder(qwen) // 使用注入的 Qwen ChatModel 创建客户端构造器
                .defaultOptions(ChatOptions.builder().model(QWEN_MODEL).build()) // 设置 ChatClient 默认使用的 Qwen 模型， 如果qwenChatModel已经指定了模型，则此处不需要设置。
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(windowChatMemory).build()) // 为请求添加持久化对话记忆能力
                .build(); // 完成 ChatClient 对象构建
    }




}











