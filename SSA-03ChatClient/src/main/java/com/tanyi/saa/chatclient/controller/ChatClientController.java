package com.tanyi.saa.chatclient.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Description 如何构建 chatClient
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/
@RestController
public class ChatClientController {

    private static final Logger log = LoggerFactory.getLogger(ChatClientController.class);

    /**
     * 方法一:
     * 使用构造方法的方式创建 chatClient
    */
    private final ChatClient chatClient;

    /**
     * 使用自动配置的 DashScope 模型构建 ChatClient。
     *
     * @param dashScopeChatModel DashScope 聊天模型
     */
    public ChatClientController(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        this.chatClient = ChatClient.builder(dashScopeChatModel).build();
    }

    /**
     * 流式返回模型回答。
     *
     * @param msg 用户输入
     * @return 回答文本流
     */
    @GetMapping("/chatClientDoStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        log.info("chatClientDoStream input: msgLength={}", msg.length());

        return chatClient.prompt().user(msg).stream().content();
    }
}
