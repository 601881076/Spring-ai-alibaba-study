package com.tanyi.saa.chatclient.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Description chatModel
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/
@RestController
public class ChatModelController {

    @Resource
    private ChatModel chatModel;


    @GetMapping("/doStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.stream(msg);
    }
}
