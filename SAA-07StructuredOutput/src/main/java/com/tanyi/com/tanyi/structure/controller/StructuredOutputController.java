package com.tanyi.com.tanyi.structure.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import cn.hutool.core.util.DesensitizedUtil;
import com.tanyi.com.tanyi.structure.model.PoemOutput;
import com.tanyi.com.tanyi.structure.record.StudentRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 提供学生信息结构化提取接口。
 */
@Slf4j
@RestController
public class StructuredOutputController {

    @Resource
    @Qualifier("qwenChatClient")
    private ChatClient qwenChatClient;

    @Resource
    @Qualifier("qwenChatModel")
    private ChatModel chatModel;

    /**
     * http://localhost:8081/structuredoutput/chat?name=杨稀饭&email=601881076@qq.com
     *
     * 结构化输出基本使用
     * @param name  姓名
     * @param email 邮箱
     * @return 学生信息的结构化文本
     */
    @GetMapping("/structuredoutput/chat")
    public Mono<String> chat(@RequestParam("name") String name,
                             @RequestParam("email") String email) {
        log.info("结构化提取请求，name={}, email={}",
                DesensitizedUtil.chineseName(name), DesensitizedUtil.email(email));

        // Agent 同步调用内部会 block，必须延迟到允许阻塞的线程池执行。
        return Mono.fromCallable(() -> {
            ReactAgent agent = ReactAgent.builder()
                    .name("contact_extractor")
                    .model(chatModel)
                    .outputType(StudentRecord.class)
                    .build();

            // 调用外部模型提取学生信息。
            AssistantMessage result = agent.call("提取出学生信息，我叫" + name + " ，大学专业是计算机科学与技术，邮箱 " + email);

            return result.getText();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * http://localhost:8081/structuredoutput/chat2
     *
     * 结构化输出基本使用
     * @return 从信息中提取结构化内容
     */
    @GetMapping("/structuredoutput/chat2")
    public Mono<String> chat2() {

        // Agent 同步调用内部会 block，必须延迟到允许阻塞的线程池执行。
        return Mono.fromCallable(() -> {
            ReactAgent agent = ReactAgent.builder()
                    .name("poem_agent")
                    .model(chatModel)
                    .outputType(PoemOutput.class)
                    .build();

            // 调用外部模型提取学生信息。
            AssistantMessage result = agent.call("写一首关于春天的诗");

            return result.getText();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
