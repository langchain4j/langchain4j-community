package dev.langchain4j.community.model.cohere;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.community.model.client.chat.response.CohereLogprobs;
import dev.langchain4j.community.model.util.CohereChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;


@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereLogprobsIT {

    private static final String MODEL_NAME = "command-r7b-12-2024";
    private static final String API_KEY = System.getenv("CO_API_KEY");

    private static final ChatModel CHAT_MODEL = CohereChatModel.builder()
            .modelName(MODEL_NAME)
            .authToken(API_KEY)
            .maxOutputTokens(10)
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final StreamingChatModel STREAMING_CHAT_MODEL = CohereStreamingChatModel.builder()
            .modelName(MODEL_NAME)
            .apiKey(API_KEY)
            .maxOutputTokens(20)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_include_logprobs_metadata_if_enabled_for_chat_model() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(CohereChatRequestParameters.builder()
                        .logprobs(true)
                        .build())
                .build();

        // when
        ChatResponse response = CHAT_MODEL.chat(chatRequest);

        // then
        assertThat(response.metadata()).isOfAnyClassIn(CohereChatResponseMetadata.class);

        CohereChatResponseMetadata metadata = (CohereChatResponseMetadata) response.metadata();

        assertThat(metadata.logprobs()).isNotNull();
        assertThat(metadata.logprobs()).isNotEmpty();

        CohereLogprobs logprobs = metadata.logprobs().get(0);
        assertThat(logprobs.getTokenIds()).hasSizeGreaterThan(1);
        assertThat(logprobs.getText()).isNotEmpty();
        assertThat(logprobs.getLogprobs()).isNotEmpty();
    }

    @Test
    void should_NOT_include_logprobs_metadata_if_NOT_enabled_for_chat_model() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(CohereChatRequestParameters.builder()
                        .logprobs(false)
                        .build())
                .build();

        // when
        ChatResponse response = CHAT_MODEL.chat(chatRequest);

        // then
        assertThat(response.metadata()).isOfAnyClassIn(CohereChatResponseMetadata.class);
        CohereChatResponseMetadata metadata = (CohereChatResponseMetadata) response.metadata();
        assertThat(metadata.logprobs()).isNull();
    }

    @Test
    void should_include_logprobs_metadata_if_enabled_for_streaming_model() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(CohereChatRequestParameters.builder()
                        .logprobs(true)
                        .build())
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        STREAMING_CHAT_MODEL.chat(chatRequest, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.metadata()).isOfAnyClassIn(CohereChatResponseMetadata.class);

        CohereChatResponseMetadata metadata = (CohereChatResponseMetadata) response.metadata();

        assertThat(metadata.logprobs()).isNotNull();
        assertThat(metadata.logprobs()).isNotEmpty();

        CohereLogprobs logprobs = metadata.logprobs().get(0);
        assertThat(logprobs.getTokenIds()).hasSizeGreaterThan(1);
        assertThat(logprobs.getText()).isNotEmpty();
        assertThat(logprobs.getLogprobs()).isNotEmpty();
    }

    @Test
    void should_NOT_include_logprobs_metadata_if_NOT_enabled_for_streaming_model() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(CohereChatRequestParameters.builder()
                        .logprobs(false)
                        .build())
                .build();
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        // when
        STREAMING_CHAT_MODEL.chat(chatRequest, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.metadata()).isOfAnyClassIn(CohereChatResponseMetadata.class);
        CohereChatResponseMetadata metadata = (CohereChatResponseMetadata) response.metadata();
        assertThat(metadata.logprobs()).isNull();
    }

}
