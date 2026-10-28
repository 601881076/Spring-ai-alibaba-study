package com.tanyi.saa.streaming.controller;

/**
 * Description TODO
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class StreamOutputController {
    private static final Logger log = LoggerFactory.getLogger(StreamOutputController.class);

    // ************************ 使用 ChatModel 同时存在多模型 ********************

    //V1 通过ChatModel实现stream实现流式输出
    @Resource(name = "deepseek")
    private ChatModel deepseekChatModel;
    @Resource(name = "qwen")
    private ChatModel qwenChatModel;


    @GetMapping(value = "/stream/chatflux1", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return deepseekChatModel.stream(question);
    }

    @GetMapping(value = "/stream/chatflux2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux2(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return qwenChatModel.stream(question);
    }

    // ************************ 使用 ChatClient 同时存在多模型 ********************


    @Resource
    @Qualifier("deepSeekChatClient")
    private ChatClient deepseekChatClient;

    @Resource
    @Qualifier("qwenChatClient")
    private ChatClient qwenChatClient;

    @GetMapping(value = "/stream/chatflux3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux3(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return deepseekChatClient.prompt(question).stream().content();
    }

    @GetMapping(value = "/stream/chatflux4", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatflux4(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        log.info("chatflux4 input: questionLength={}", question.length());

        Flux<ServerSentEvent<String>> result = qwenChatClient.prompt(question)
                .stream()
                .content()
                .map(content -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .data(content)
                        .build());
        return result;
    }
}
