package com.tanyi.mcpclient.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author zzyybs@126.com
 * @create 2025-07-31 21:14
 * @Description MCP 客户端控制器：对比演示“带有 MCP 工具调用能力”与“普通大模型调用”的区别
 */
@RestController
public class McpClientController {

    /**
     * 注入了我们在 SaaLLMConfig 中配置的 ChatClient。
     * 这个对象内部已经绑定了从 MCP Server 拉取到的工具（如查天气），具备 Tool Calling 决策能力。
     */
    @Resource
    private ChatClient chatClient; // 使用 mcp 支持

    /**
     * 直接注入底层的 ChatModel（如通义千问）。
     * 这个对象只具备基础的自然语言处理能力，没有绑定任何 MCP 工具，无法调用外部资源。
     */
    @Resource
    private ChatModel chatModel; // 没有纳入 tool 支持，普通调用

    /**
     * 接口 1：使用 MCP 协议支持的流式对话
     * 测试 URL: http://localhost:8015/mcpclient/chat?msg=上海
     *
     * @param msg 用户输入的提示词
     * @return 流式响应结果
     */
    @GetMapping("/mcpclient/chat")
    public Flux<String> chat(@RequestParam(name = "msg", defaultValue = "北京") String msg) {
        System.out.println("=== 使用了 MCP（具备调用工具能力） ===");

        // 逻辑解析：
        // 1. 用户输入 "上海天气"。
        // 2. 大模型通过 ChatClient 绑定的 Tool Calling 能力，识别出需要调用 MCP 工具。
        // 3. 底层自动通过 MCP Client 向 8014 端口（MCP Server）发起请求。
        // 4. Server 执行 WeatherService 的方法，返回天气数据。
        // 5. 大模型整合数据，通过 Flux 流式返回给前端。
        return chatClient.prompt(msg)
                .stream()
                .content();
    }

    /**
     * 接口 2：普通的大模型流式对话（无 MCP 工具支持）
     * 测试 URL: http://localhost:8015/mcpclient/chat2?msg=上海
     *
     * @param msg 用户输入的提示词
     * @return 流式响应结果
     */
    @RequestMapping("/mcpclient/chat2")
    public Flux<String> chat2(@RequestParam(name = "msg", defaultValue = "北京") String msg) {
        System.out.println("=== 未使用 MCP（纯大模型对话） ===");

        // 逻辑解析：
        // 直接调用 chatModel 的 stream 方法，大模型没有任何工具说明书（没有绑定 ToolCallbackProvider）。
        // 如果问它 "上海天气怎么样"，它只能靠训练数据里的记忆去编造，或者直接回答不知道，
        // 无法实时去查询 WeatherService 中定义的假数据。
        return chatModel.stream(msg);
    }
}
