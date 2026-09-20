package com.tanyi.saa.chatclient.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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

    /**
     * 方法一:
     * 使用构造方法的方式创建 chatClient
     */
    private final ChatClient chatClient;
    public ChatClientController(ChatModel dashScopeChatModel) {
        this.chatClient = ChatClient.builder(dashScopeChatModel).build();
    }

    @GetMapping("/chatClientDoStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatClient.prompt().user(msg).stream().content();
    }
}
