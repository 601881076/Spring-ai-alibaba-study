package com.tanyi.toolcalling.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @auther zzyybs@126.com
 * @create 2025-07-31 20:26
 * @Description
 */
@RestController
public class NoToolCallingController {

    @Resource
    private ChatModel chatModel;

    /**
     * localhost:8081/notoolcall/chat
     * @param msg
     * @return
     */
    @GetMapping("/notoolcall/chat")
    public Flux<String> chat(
            @RequestParam(name = "msg", defaultValue = "你是谁现在几点") String msg) {
        return chatModel.stream(msg);
    }
}
