package dev.langchain4j.community.model.zhipu;

import dev.langchain4j.community.model.zhipu.assistant.AssistantKeyValuePair;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationId;
import dev.langchain4j.community.model.zhipu.assistant.problem.Problems;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAssistantChatModelIT {
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");
    private static final String appId = "";

    ZhipuAssistantChatModel chatModel = ZhipuAssistantChatModel.builder()
            .apiKey(apiKey)
            .appId(appId)
            .connectTimeout(ofSeconds(60))
            .writeTimeout(ofSeconds(60))
            .readTimeout(ofSeconds(60))
            .callTimeout(ofSeconds(60))
            .build();

    @Test
    void should_generate_answer_and_return_token_usage() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();

        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                fail("onNext() must not be called");
            }

            @Override
            public void onError(Throwable error) {
                fail("OnError() must not be called");
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                future.complete(response);
            }
        };

        // given
        AssistantKeyValuePair keyValuePair = chatModel.initMessage("中国首都在哪里");
        //消息入库
        chatModel.generate(getConversationId(), List.of(keyValuePair), handler);

        Response<AiMessage> response = future.get(5, SECONDS);
        // then
        assertThat(response.content().text()).contains("北京");
    }


    @Test
    void recommend_problems() {
        String conversationId = getConversationId();
        Problems problems = chatModel.sessionRecord(conversationId);
        assertThat(problems.getProblems()).isNotEmpty();
    }


    /**
     * create conversationId
     * @return conversationId
     */
    public String getConversationId(){
        ConversationId conversationId = chatModel.conversation();
        return conversationId.getConversationId();
    }

}
