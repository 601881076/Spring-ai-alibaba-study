package com.tanyi.mcpserver.config;

import com.tanyi.mcpserver.service.WeatherService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zzyybs@126.com
 * @create 2025-07-31 21:08
 * @Description
 *
 * @Tool 注解的幕后推手：
 * 我们在 WeatherService 里写了 @Tool，但 Spring AI 怎么知道要把这个方法发给 MCP Client？就是靠这里的 MethodToolCallbackProvider。它会在启动时扫描 weatherService 对象，找到所有带有 @Tool 的方法，把它们打包、转换。
 *
 * 组装成标准的 ToolCallbackProvider：
 * MethodToolCallbackProvider.builder().toolObjects(weatherService).build() 这一句，把普通的 Java 方法转化为符合 MCP 协议的“工具定义”。底层会自动根据方法签名生成 JSON Schema。
 *
 * 打包发送给 Client：
 * 生成的 ToolCallbackProvider Bean 会被 Spring AI 的底层 MCP Server 组件拦截。当 Codex 或其他 MCP Client 连上这个 Server，并发送 tools/list 请求时，MCP Server 就会把 weatherTools 里包含的工具（即查天气的工具）的说明书推送给 Client。
 */
@Configuration
public class McpServerConfig {

    /**
     * 将工具方法暴露给外部 mcp client 调用
     *
     * @param weatherService
     * @return
     */
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService)
                .build();
    }
}
