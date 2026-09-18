package tanyi.saa.hellowrld.controller;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Description 第一个ai类
 * Author
 * Date 2026/9/18
 * Version 1.0
 **/
@RestController
public class HelloWorldController {

    @Resource
    private ChatModel chatModel;

    /**
     * 一次性打印输出
     * @param msg
     * @return
     */
    @GetMapping("/doChat")
    public String doChat(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.call(msg);
    }

    /**
     * 使用流式输出
     * @param msg
     * @return
     */
    @GetMapping("/doStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.stream(msg);
    }


}
