package com.tanyi.saa.prompt.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Slf4j
public class PromptController {
    @Resource(name = "deepseek")
    private ChatModel deepseekChatModel;
    @Resource(name = "qwen")
    private ChatModel qwenChatModel;

    @Resource(name = "deepseekChatClient")
    private ChatClient deepseekChatClient;
    @Resource(name = "qwenChatClient")
    private ChatClient qwenChatClient;

    // http://localhost:8081/prompt/chat?question=火锅介绍下
    @GetMapping("/prompt/chat")
    public Flux<String> chat(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return deepseekChatClient.prompt()
                .system("你是一个法律助手，只回答法律问题，其它问题回复，我只能回答法律相关问题，其它无可奉告")
                .user(question)
                .stream().content();

    }

    // http://localhost:8081/prompt/chat2?question=葫芦娃
    @GetMapping("/prompt/chat2")
    public Flux<ChatResponse> chat2(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        // 系统消息
        SystemMessage systemMessage = new SystemMessage("你是一个讲故事的助手，每个故事控制在300字以内");

        // 用户消息
        UserMessage userMessage = new UserMessage(question);

        // 构建提示词
        Prompt prompt = new Prompt(systemMessage, userMessage);

        return deepseekChatModel.stream(prompt);
    }

    // http://localhost:8081/prompt/chat3?question=葫芦娃
    @GetMapping("/prompt/chat3")
    public Flux<String> chat3(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        // 系统消息
        SystemMessage systemMessage = new SystemMessage("你是一个讲故事的助手，每个故事控制在300字以内");
        // 用户消息
        UserMessage userMessage = new UserMessage(question);
        // 构建提示词
        Prompt prompt = new Prompt(systemMessage, userMessage);

        // 构建响应
        return deepseekChatModel.stream(prompt).mapNotNull(response -> response.getResults().getFirst().getOutput().getText());
    }

    /**
     * 获取 chatResponse : LLM 响应的原数据
     * http://localhost:8081/prompt/chat4?question=葫芦娃
     * @param question
     * @return
     */
    @GetMapping("/prompt/chat4")
    public Flux<ChatResponse> chat4(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        // AssistantMessage assistantMessage = deepseekChatClient.prompt().user(question).stream();
        return deepseekChatClient.prompt().user(question).stream().chatResponse();
    }

    /**
     * 如何遍历 stream 的数据
     * http://localhost:8081/prompt/chat5?question=葫芦娃
     * @param question
     * @return
     */
    @GetMapping("/prompt/chat5")
    public Flux<String> chat5(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        // deepseekChatClient.prompt().user(question).stream();

        return deepseekChatClient.prompt()
                .user(question)
                .stream()
                .chatResponse()
                .mapNotNull(response -> response.getResults().getFirst().getOutput().getText());
    }

    /**
     * 搭配 Assistant 提示词角色
     * http://localhost:8081/prompt/chat6?question=葫芦娃
     * @param question
     * @return
     */
    @GetMapping("/prompt/chat6")
    public String chat6(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        AssistantMessage assistantMessage = deepseekChatClient.prompt().user(question).call().chatResponse().getResult().getOutput();
        return assistantMessage.getText();
    }

}
