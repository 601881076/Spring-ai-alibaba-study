package com.tanyi.prompttemplate.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Description 提示词模版 基本使用
 * Author
 * Date 2026/9/22
 * Version 1.0
 **/
@RestController
public class PromptTemplateController {

    @Resource
    @Qualifier("deepseekChatClient")
    private ChatClient deepseekChatClient;

    @Resource
    @Qualifier("deepseek")
    private ChatModel deepseekChatModel;

    @Value("classpath:/prompt-template/my-prompt-template.txt")
    private org.springframework.core.io.Resource userTemplate;

    /**
     * 提示词模版基本使用
     * http://localhost:8081/prompt-template/chat?topic=孙悟空&outputFormat=HTML&wordCount=300
     * @param topic
     * @param outputFormat
     * @param wordCount
     * @return
     */
    @GetMapping("/prompt-template/chat")
    public Flux<String> chat(@RequestParam(name = "topic") String topic,
                             @RequestParam(name = "outputFormat") String outputFormat,
                             @RequestParam(name = "wordCount") String wordCount) {
        // 创建提示词模版字符串
        String templateStr = "讲一个关于{topic}的故事并以{outputFormat}格式输出,字数控制在{wordCount}左右";

        // 创建提示词模版类
        PromptTemplate promptTemplate = new PromptTemplate(templateStr);
        Prompt prompt = promptTemplate.create(Map.of("topic", topic, "outputFormat", outputFormat, "wordCount", wordCount));
        return deepseekChatClient.prompt(prompt).stream().content();
    }

    /**
     * 加载提示词模版文件创建 PromptTemplate
     *
     * http://localhost:8081/prompt-template/chat2?topic=孙悟空&outputFormat=HTML&wordCount=300
     * @param topic
     * @param outputFormat
     * @param wordCount
     * @return
     */
    @GetMapping("/prompt-template/chat2")
    public Flux<String> chat2(@RequestParam(name = "topic") String topic,
                              @RequestParam(name = "outputFormat") String outputFormat,
                              @RequestParam(name = "wordCount") String wordCount) {
        // 创建提示词模版
        PromptTemplate promptTemplate = new PromptTemplate(userTemplate);

        // PromptTemplate ->prompt
        Prompt prompt = promptTemplate.create(Map.of("topic", topic, "outputFormat", outputFormat, "wordCount", wordCount));

        // 调用大模型
        return deepseekChatClient.prompt(prompt).stream().content();
    }

    /**
     * 使用多角色提示词模版
     *
     * http://localhost:8081/prompt-template/chat3?systemTopic=法律&userTopic=火锅
     *
     * @param systemTopic
     * @return
     */
    @GetMapping("/prompt-template/chat3")
    public Flux<String> chat3(@RequestParam(name = "systemTopic") String systemTopic,
                              @RequestParam(name = "userTopic") String userTopic) {
        // 提示词模版内容
        String systemPromptTemplateStr = "你是{systemTopic}助手, 只回答{systemTopic},其他无可奉告,以HTML格式的结果展示";

        // 创建系统角色提示词模版
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptTemplateStr);

        // 提示词模版 -> message
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("systemTopic", systemTopic));

        // 创建用户提示词模版
        String userPromptTemplateStr = "解释一下{userTopic}";
        // 构建用户提示词模版类
        PromptTemplate userPromptTemplate = new PromptTemplate(userPromptTemplateStr);
        // 用户提示词模版 -> message 类
        Message userMessage = userPromptTemplate.createMessage(Map.of("userTopic", userTopic));

        // 构建提示词
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        // 调用 LLM
        return deepseekChatClient.prompt(prompt).stream().content();
    }

    /**
     * 提示词人物设定
     * 使用 chatClient 完成
     *
     * http://localhost:8081/prompt-template/chat4?question=旅游
     *
     * @param question
     * @return
     */
    @GetMapping("/prompt-template/chat4")
    public Flux<String> chat4(@RequestParam(name = "question") String question) {
        // 构建系统级角色消息
        SystemMessage systemMessage = new SystemMessage("你是一个 Java 编程助手,拒绝回答非技术问题");

        // 构建用户级角色消息
        UserMessage userMessage = new UserMessage(question);

        // 构建提示词
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        return deepseekChatClient.prompt(prompt).stream().content();
    }

    /**
     * 提示词人物设定
     * 使用 chatModel 完成
     *
     * http://localhost:8081/prompt-template/chat5?question=旅游
     *
     * @param question
     * @return
     */
    @GetMapping("/prompt-template/chat5")
    public String chat5(@RequestParam(name = "question") String question) {
        // 构建系统级角色消息
        SystemMessage systemMessage = new SystemMessage("你是一个 Java 编程助手,拒绝回答非技术问题");

        // 构建用户级角色消息
        UserMessage userMessage = new UserMessage(question);

        // 构建提示词
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        return deepseekChatModel.call(prompt).getResult().getOutput().getText();
    }
}
























