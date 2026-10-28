package com.tanyi.saa.streaming.controller;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 提供 DeepSeek、千问的流式聊天接口，演示 ChatModel 和 ChatClient 两种调用方式。
 *
 * <p>chatflux4 使用具名 SSE 事件：delta 携带正文片段，done 表示模型已正常结束。
 * 其余接口直接返回文字流，便于比较两种响应格式。
 */
@RestController
public class StreamOutputController {
    private static final Logger log = LoggerFactory.getLogger(StreamOutputController.class);

    // ************************ 使用 ChatModel 同时存在多模型 ********************

    /** DeepSeek 模型，用于演示直接通过 ChatModel 获取文字流。 */
    @Resource(name = "deepseek")
    private ChatModel deepseekChatModel;
    /** 千问模型，与 DeepSeek 使用不同的 Bean 名称以区分注入对象。 */
    @Resource(name = "qwen")
    private ChatModel qwenChatModel;


    /**
     * 通过 DeepSeek ChatModel 逐段返回回答。
     *
     * @param question 用户问题，未提供时使用“你是谁”
     * @return 模型回答的文字片段流
     */
    @GetMapping(value = "/stream/chatflux1", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return deepseekChatModel.stream(question);
    }

    /**
     * 通过千问 ChatModel 逐段返回回答。
     *
     * @param question 用户问题，未提供时使用“你是谁”
     * @return 模型回答的文字片段流
     */
    @GetMapping(value = "/stream/chatflux2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux2(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return qwenChatModel.stream(question);
    }

    // ************************ 使用 ChatClient 同时存在多模型 ********************


    /** 封装 DeepSeek 模型的聊天客户端。 */
    @Resource
    @Qualifier("deepSeekChatClient")
    private ChatClient deepseekChatClient;

    /** 封装千问模型的聊天客户端，供具名 SSE 接口调用。 */
    @Resource
    @Qualifier("qwenChatClient")
    private ChatClient qwenChatClient;

    /**
     * 通过 DeepSeek ChatClient 提取并流式返回正文。
     *
     * @param question 用户问题，未提供时使用“你是谁”
     * @return 模型回答的文字片段流
     */
    @GetMapping(value = "/stream/chatflux3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatflux3(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        return deepseekChatClient.prompt(question).stream().content();
    }

    /**
     * 流式发送回答，在模型正常结束且上游流完成后发送 done 事件。
     * 演示页面：localhost:8081/index.html。
     *
     * <p>完成判断需要同时满足两个条件：模型返回 STOP，且响应流正常完成。
     * HTTP 连接结束本身不能证明回答完整；LENGTH 或缺失结束标志都不发送 done。
     * done 的 data 保存模型结束原因，delta 的 data 保存原始正文片段。
     *
     * @param question 用户问题
     * @return 回答片段和正常完成事件；异常信号继续向下游传递
     */
    @GetMapping(value = "/stream/chatflux4", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatflux4(@RequestParam(name = "question", defaultValue = "你是谁") String question) {
        // 只记录问题长度，避免把用户完整正文写入日志。
        log.info("chatflux4 input: questionLength={}", question.length());

        return Flux.defer(() -> {
            // defer 的函数由 WebFlux 订阅时执行，而不是 Controller 构建返回值时执行。
            // 每次订阅重新创建状态；即使同一个 Flux 被重复订阅，也不会复用之前的结束原因。
            // AtomicReference 便于在响应处理函数中更新状态，初始空串表示尚未收到有效标志。
            AtomicReference<String> finishReason = new AtomicReference<>("");
            Flux<ServerSentEvent<String>> events = qwenChatClient.prompt(question)
                    .stream()
                    // 保留 Generation 中的结束原因；content() 只提供正文，无法完成此处的判断。
                    .chatResponse()
                    // 按上游顺序处理片段；保存结束原因和发送正文保持同一顺序。
                    .concatMap(response -> toDeltaEvent(response, finishReason));
            // concatWith 等正文流正常完成后，才订阅后面的 Mono；异常和取消不会进入该完成分支。
            // Mono.defer 把 get() 和完成判断推迟到此时，读取最终原因，而不是构建链路时的空串。
            // 完成事件统一在这里生成一次，避免多个片段携带 STOP 时重复发送 done。
            return events.concatWith(Mono.defer(() -> completeEvent(finishReason.get())));
        });
    }

    /**
     * 保留模型结束原因，将非空正文转换为 delta 事件。
     *
     * <p>先保存结束原因，再检查正文。模型的最后一个片段可能只有 STOP 而没有文字，
     * 如果先丢弃空正文，就会丢失用于判断完成的标志。
     *
     * @param response 模型响应片段
     * @param finishReason 当前订阅最后收到的有效结束原因，会在此更新
     * @return 正文事件；仅包含元数据的片段返回空序列
     */
    private Mono<ServerSentEvent<String>> toDeltaEvent(ChatResponse response, AtomicReference<String> finishReason) {
        Generation generation = response.getResult();
        // 用量等元数据尾包可能没有候选回答，保留此前保存的结束原因即可。
        if (generation == null) {
            log.debug("chatflux4 received metadata-only response");
            return Mono.empty();
        }
        String reason = generation.getMetadata().getFinishReason();
        // 生成中的空标志不覆盖已收到的 STOP/LENGTH，兼容 SDK 空值与字符串 null 的表示。
        if (reason != null && !reason.isBlank() && !"null".equalsIgnoreCase(reason)) {
            finishReason.set(reason);
            log.debug("chatflux4 received finishReason={}", reason);
        }
        String content = generation.getOutput().getText();
        // 这里只过滤 null 和空串。空格、换行仍是有效正文，不能用 isBlank() 把它们删除。
        if (content == null || content.isEmpty()) {
            log.debug("chatflux4 received response without text");
            return Mono.empty();
        }
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                // 浏览器需通过 addEventListener("delta", ...) 接收这个具名事件。
                .event("delta")
                .data(content)
                .build();
        return Mono.just(event);
    }

    /**
     * 仅将正常结束标志转换为完成事件，截断和缺失标志保留为未完成。
     *
     * <p>此方法由正文流完成后的 Mono.defer 调用。Mono.empty() 只表示不追加事件，
     * 不表示回答成功；调用方应以是否收到 done 作为完整回答的判断依据。
     *
     * @param finishReason 模型结束原因，SDK 将 DashScope 标志映射为大写名称
     * @return 携带结束原因的 done 事件；非正常结束时返回空序列
     */
    private Mono<ServerSentEvent<String>> completeEvent(String finishReason) {
        // 采用明确的正常结束标志；LENGTH 表示截断，未知或缺失原因同样不能当成成功。
        if (!"STOP".equalsIgnoreCase(finishReason)) {
            log.warn("chatflux4 incomplete: finishReason={}", finishReason);
            return Mono.empty();
        }
        log.info("chatflux4 completed: finishReason={}", finishReason);
        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .event("done")
                // 发送 SDK 提供的结束原因，便于客户端和调试工具检查完成依据。
                .data(finishReason)
                .build();
        return Mono.just(event);
    }
}
