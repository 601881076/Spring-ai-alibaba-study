package com.tanyi.persistent.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * Description 使用 redis 存储对话记忆
 * Author
 * Date 2026/10/19
 * Version 1.0
 **/
@RestController
public class ChatMemory4RedisController {
    @Resource(name = "qwenChatClient")
    private ChatClient qwenChatClient;

    /**
     * http://localhost:8081/chatmemory/chat?msg=2加5等于多少&userId=7
     *
     * @param msg
     * @param userId
     * @return
     */
    @GetMapping("/chatmemory/chat")
    public String chat(String msg, String userId) {
        return qwenChatClient.prompt(msg).advisors(new Consumer<ChatClient.AdvisorSpec>() {
            @Override
            public void accept(ChatClient.AdvisorSpec advisorSpec) {
                advisorSpec.param(CONVERSATION_ID, userId);
            }
        }).call().content();

        // return qwenChatClient.prompt(msg).advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, userId)).call().content();
    }
}
