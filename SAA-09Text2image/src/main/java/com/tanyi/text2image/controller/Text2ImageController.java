package com.tanyi.text2image.controller;

import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 接收文生图提示词并返回生成图片的 URL。
 */
@Slf4j
@RestController
public class Text2ImageController {

    /** 文生图模型名称。 */
    public static final String IMAGE_MODEL = "wanx2.1-t2i-turbo";
    @Resource
    private ImageModel imageModel;

    /**
     * http://localhost:8081/t2i/image?prompt=%E6%A3%89%E8%8A%B1
     *
     * 根据提示词生成图片，省略提示词时默认生成刺猬。
     *
     * @param prompt 图片描述
     * @return 异步返回生成图片的 URL；模型调用失败时传播错误信号
     */
    @GetMapping(value = "/t2i/image")
    public Mono<String> image(@RequestParam(name = "prompt", defaultValue = "刺猬") String prompt) {
        log.info("生成图片，prompt={}", prompt);
        DashScopeImageOptions dashScopeImageOptions = DashScopeImageOptions.builder()
                .model(IMAGE_MODEL)
                .build();

        ImagePrompt request = new ImagePrompt(prompt, dashScopeImageOptions);

        // 提交生图任务和轮询结果均会阻塞，放到弹性线程池执行。
        return Mono.fromCallable(() -> imageModel.call(request)
                        .getResult()
                        .getOutput()
                        .getUrl())
                .subscribeOn(Schedulers.boundedElastic());
    }
}
