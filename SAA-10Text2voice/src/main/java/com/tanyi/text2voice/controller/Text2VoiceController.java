package com.tanyi.text2voice.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;

/**
 * 将文本合成为可在浏览器播放的 MP3 音频。
 */
@RestController
public class Text2VoiceController {

    private static final Logger log = LoggerFactory.getLogger(Text2VoiceController.class);

    public static final String BAILIAN_VOICE_MODEL = "cosyvoice-v2";
    public static final String BAILIAN_VOICE_TIMBER = "longyingcui";

    private final DashScopeAudioSpeechModel speechModel;

    /**
     * 注入百炼语音合成模型。
     *
     * @param speechModel 语音合成模型
     */
    public Text2VoiceController(DashScopeAudioSpeechModel speechModel) {
        this.speechModel = speechModel;
    }

    /**
     * 调用百炼合成文本，并返回完整的 MP3 音频。
     *
     * @param msg 待合成的文本，省略时使用默认示例
     * @return MP3 音频字节
     */
    @GetMapping(value = "/t2v/voice", produces = "audio/mpeg")
    public Mono<byte[]> voice(
            @RequestParam(name = "msg", defaultValue = "温馨提醒，支付宝到账100元请注意查收") String msg) {
        log.info("文生音请求，msg={}", msg);
        // 同步合成会等待 WebSocket 返回，将其放到工作线程执行。
        return Mono.fromCallable(() -> synthesize(msg))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 合成语音并按顺序合并所有音频片段。
     *
     * @param msg 待合成的文本
     * @return 完整的 MP3 音频字节
     */
    private byte[] synthesize(String msg) {
        DashScopeAudioSpeechOptions options = DashScopeAudioSpeechOptions.builder()
                .model(BAILIAN_VOICE_MODEL)
                .voice(BAILIAN_VOICE_TIMBER)
                .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.MP3)
                .build();
        TextToSpeechResponse response = speechModel.call(new TextToSpeechPrompt(msg, options));

        // 按顺序合并全部结果，避免多片段响应丢失音频。
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        for (Speech speech : response.getResults()) {
            audio.writeBytes(speech.getOutput());
        }
        return audio.toByteArray();
    }

}
