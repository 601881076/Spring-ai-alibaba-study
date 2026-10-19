package com.tanyi.persistent.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * Description 使用 redis 存储对话记忆
 * Author
 * Date 2026/10/19
 * Version 1.0
 **/
@RestController
@Slf4j
public class ChatMemory4RedisController {
    @Resource(name = "qwenChatClient")
    private ChatClient qwenChatClient;

    /**
     * 流式生成回复，并通过 Redis 保存用户的对话记忆。
     *
     * @param msg 用户消息
     * @param userId 用于区分对话记忆的用户标识
     * @return 通过 SSE 逐段发送的回复内容
     */
    @GetMapping(value = "/chatmemory/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam("msg") String msg, @RequestParam("userId") String userId) {
        log.info("chat params: msg={}, userId={}", msg, userId);
        return qwenChatClient.prompt(msg)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, userId))
                .stream()
                .content();
    }
}
