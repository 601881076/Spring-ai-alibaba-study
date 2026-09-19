package tanyi.saa.hellowrld.controller;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Description 第一个ai类
 * Author
 * Date 2026/9/18
 * Version 1.0
 **/
@RestController
public class HelloWorldController {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldController.class);

    @Resource
    private ChatModel chatModel;

    /**
     * 异步执行同步模型调用，一次性返回完整回答。
     * @param msg 用户输入
     * @return 完整回答的异步结果
     */
    @GetMapping("/doChat")
    public Mono<String> doChat(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        // 用户输入可能包含敏感信息，只记录长度。
        log.info("doChat input: msgLength={}", msg.length());
        // 同步模型调用可能阻塞，必须离开 WebFlux 的事件循环线程执行。
        return Mono.fromCallable(() -> chatModel.call(msg))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 通过 SSE 逐段返回模型回答。
     * @param msg 用户输入
     * @return 回答的文本片段
     */
    // @GetMapping(value = "/doStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @GetMapping(value = "/doStream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        // 用户输入可能包含敏感信息，只记录长度。
        log.info("doStream input: msgLength={}", msg.length());
        return chatModel.stream(msg);
    }


}
