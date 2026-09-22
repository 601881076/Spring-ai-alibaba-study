package com.tanyi.com.tanyi.structure.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.tanyi.com.tanyi.structure.record.StudentRecord;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Consumer;

@RestController
public class StructuredOutputController {

    @Resource
    @Qualifier("qwenChatClient")
    private ChatClient qwenChatClient;

    @Resource
    @Qualifier("qwenChatModel")
    private ChatModel chatModel;

    /**
     * 结构化输出基本使用
     * @param name  姓名
     * @param email 邮箱
     * @return
     */
    @GetMapping("/structuredoutput/chat")
    public String chat(@RequestParam("name") String name,
                              @RequestParam("email") String email) throws GraphRunnerException {
        // 使用 BeanOutputConverter 生成 outputSchema
        BeanOutputConverter<StudentRecord> outputConverter = new BeanOutputConverter<>(StudentRecord.class);
        String format = outputConverter.getFormat();

        ReactAgent agent = ReactAgent.builder()
                .name("contact_extractor")
                .model(chatModel)
                .outputSchema(format)
                .build();

        AssistantMessage result = agent.call("提取出学生信息，我叫" + name + " ，大学专业是计算机科学与技术，邮箱 " + email);

        return result.getText();
    }
}
