/**
 * Description TODO
 * Author
 * Date 2026/9/20
 * Version 1.0
 **/

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class PromptController {
    @Resource(name = "deepseek")
    private ChatModel deepseekChatModel;
    @Resource(name = "qwen")
    private ChatModel qwenChatModel;
    @Resource(name = "deepseekChatClient")
    private
    ChatClient deepseekChatClient;
    @Resource(name = "qwenChatClient")
    private ChatClient qwenChatClient;

    @GetMapping("/prompt/chat")
    public Flux<String> chat(String question) {
        return deepseekChatClient.prompt().system("你是一个法律助手，只回答法律问题，其它问题回复，我只能回答法律相关问题，其它无可奉告").user(question).stream().content();
    }

    @GetMapping("/prompt/chat2")
    public Flux<ChatResponse> chat2(String question) {
        UserMessage userMessage = new UserMessage(question);
        SystemMessage systemMessage = new SystemMessage("你是一个讲故事的助手,每个故事控制在300字以内");
        Prompt prompt = new Prompt(userMessage, systemMessage);
        return deepseekChatModel.stream(prompt);
    }

    @GetMapping("/prompt/chat3")
    public Flux<String> chat3(String question) {
        UserMessage userMessage = new UserMessage(question);
        SystemMessage systemMessage = new SystemMessage("你是一个讲故事的助手,每个故事控制在300字以内");
        Prompt prompt = new Prompt(userMessage, systemMessage);
        return deepseekChatModel.stream(prompt).map(response -> response.getResults().get(0).getOutput().getText());
    }

    @GetMapping("/prompt/chat4")
    public String chat4(String question) {
        AssistantMessage assistantMessage = deepseekChatClient.prompt().user(question).call().chatResponse().getResult().getOutput();
        return assistantMessage.getText();
    }
}
