package com.tanyi.toolcalling.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.tanyi.toolcalling.tools.DateTimeTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 使用时间工具回答用户问题的流式对话接口。
 */
@Slf4j
@RestController
public class ToolCallingController {

    @Resource
    private ChatModel chatModel;

    @Resource
    private ChatClient chatClient;

    /**
     * 将时间工具提供给模型，并流式返回工具调用后的回答。
     *  http://localhost:8081/toolcall/chat
     *
     * @param msg 用户问题，默认询问模型身份和当前时间
     * @return 仅包含回答文本的 SSE 流
     */
    @GetMapping(value = "/toolcall/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam(name = "msg", defaultValue = "你是谁，现在几点了") String msg) {

        log.info("工具对话请求，msg={}", msg);
        ToolCallback[] tools = ToolCallbacks.from(new DateTimeTools());

        // getDefaultOptions 返回参数副本，保留多模态端点设置后追加本次请求的工具。
        DashScopeChatOptions options = (DashScopeChatOptions) chatModel.getDefaultOptions();
        options.setToolCallbacks(List.of(tools));

        Prompt prompt = new Prompt(msg, options);

        return chatModel.stream(prompt)
                .mapNotNull(ChatResponse::getResult)
                .mapNotNull(result -> result.getOutput().getText())
                .filter(text -> !text.isEmpty());
    }

    /**
     * 将时间工具提供给模型，并流式返回工具调用后的回答。
     * http://localhost:8081/toolcall/chat2
     *
     * @param msg
     * @return
     */
    @GetMapping("/toolcall/chat2")
    public Flux<String> chat2(
            @RequestParam(name = "msg", defaultValue = "你是谁现在几点") String msg) {
        return chatClient.prompt(msg)
                .tools(new DateTimeTools())
                .stream()
                .content();
    }
}
