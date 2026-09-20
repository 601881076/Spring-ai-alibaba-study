package com.tanyi.saa.chatclient.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Description chatClient 和 chatModel 混合使用
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/
@RestController
@Slf4j
public class ChatClientControllerV2 {

    @Resource
    private ChatClient dashScopeChatClient;

    @Resource
    private ChatModel chatModel;


    @GetMapping("/v2/doStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.stream(msg);
    }

    /**
     * 流式返回模型回答。
     *
     * @param msg 用户输入
     * @return 回答文本流
     */
    @GetMapping("/v2/chatClientDoStream")
    public Flux<String> chatClientDoStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        log.info("chatClientDoStream input: msgLength={}", msg.length());

        return dashScopeChatClient.prompt().user(msg).stream().content();
    }

}
