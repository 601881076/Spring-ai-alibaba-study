package com.tanyi.mcpclient.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zzyybs@126.com
 * @create 2025-07-31 20:47
 * @Description 配置大模型客户端并注入 MCP 工具
 */
@Configuration
public class SaaLLMConfig {

    /**
     * 配置并初始化 ChatClient（AI 聊天客户端）
     *
     * @param chatModel 底层的大语言模型实例（如通义千问），负责理解人类语言和进行 Tool Calling 决策
     * @param tools 工具回调提供者。注意：这里是由 Spring AI MCP Client 自动注入的，
     *              它包含了我们在 application.yml 中配置的、从 8014 端口（MCP Server）拉取回来的所有工具（如天气查询）
     * @return 构建好的 ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ToolCallbackProvider tools) {
        return ChatClient.builder(chatModel)
                // 核心逻辑：将 MCP Server 提供的工具，转换为大模型能理解的 Tool Calling 格式，并设为默认可用。
                // 这样大模型在对话时，就“知道”自己拥有了查天气的能力（配置见 application.yml 的 mcp-client 部分）
                .defaultToolCallbacks(tools.getToolCallbacks())
                .build();
    }
}
