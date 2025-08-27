package dev.langchain4j.community.model.oracle.oci.genai;

import com.oracle.bmc.generativeaiinference.model.CohereChatResponse;
import com.oracle.bmc.generativeaiinference.model.CohereToolCall;
import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.model.StreamOptions;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat models hosted on OCI GenAI.
 * <p>OCI Generative AI is a fully managed service that provides a set of state-of-the-art,
 * customizable large language models (LLMs) that cover a wide range of use cases for text
 * generation, summarization, and text embeddings.
 *
 * <p>For Cohere models use {@link OciGenAiCohereChatModel}.
 *
 * <p>To learn more about the service, see the <a href="https://docs.oracle.com/iaas/Content/generative-ai/home.htm">Generative AI documentation</a>
 */
public class OciGenAiCohereStreamingChatModel extends BaseCohereChatModel<OciGenAiCohereStreamingChatModel>
        implements StreamingChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiCohereStreamingChatModel.class);
    private final Builder builder;

    OciGenAiCohereStreamingChatModel(Builder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return this.builder.listeners();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        var bmcChatRequest = prepareRequest(chatRequest)
                .isStream(true)
                .streamOptions(StreamOptions.builder().isIncludeUsage(true).build())
                .build();
        var modelName = Optional.ofNullable(chatRequest.modelName())
                .orElse(defaultRequestParameters().modelName());

        var servingMode =
                switch (builder.servingType()) {
                    case OnDemand ->
                        OnDemandServingMode.builder().modelId(modelName).build();
                    case Dedicated ->
                        DedicatedServingMode.builder().endpointId(modelName).build();
                };

        try (var isr = new InputStreamReader(
                        super.ociChat(bmcChatRequest, servingMode).getEventStream());
                var reader = new BufferedReader(isr)) {
            String line;
            com.oracle.bmc.generativeaiinference.model.CohereChatResponse lastCohereChatResponse = null;
            StringBuilder partialContent = new StringBuilder();

            List<CohereToolCall> mergedToolCalls = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                LOGGER.debug("Partial response: {}", line);

                lastCohereChatResponse = Serializer.getDefault()
                        .readValue(
                                line.replaceFirst("data: ", ""),
                                com.oracle.bmc.generativeaiinference.model.CohereChatResponse.class);

                if (lastCohereChatResponse.getToolCalls() == null && lastCohereChatResponse.getChatHistory() == null) {
                    // Cohere repeats streamed text content in partial messages with tool-calls and chat history
                    try {
                        handler.onPartialResponse(lastCohereChatResponse.getText());
                    } catch (Exception userException) {
                        handler.onError(userException);
                    }
                    partialContent.append(lastCohereChatResponse.getText());
                }
            }

            if (lastCohereChatResponse != null) {
                // Use only tool calls from the last message (Cohere repeats all stream content summarized in the last
                // part)
                if (lastCohereChatResponse.getToolCalls() != null) {
                    mergedToolCalls.addAll(lastCohereChatResponse.getToolCalls());
                }

                var toolCalls = lastCohereChatResponse.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    var lc4jResponse = map(
                                    CohereChatResponse.builder()
                                            .copy(lastCohereChatResponse)
                                            .toolCalls(mergedToolCalls)
                                            .build(),
                                    modelName,
                                    FinishReason.TOOL_EXECUTION)
                            .build();
                    var toolExecutionRequests = lc4jResponse.aiMessage().toolExecutionRequests();
                    if (toolExecutionRequests != null) {
                        for (int i = 0; i < toolExecutionRequests.size(); i++) {
                            var toolExecutionRequest = toolExecutionRequests.get(i);
                            handler.onCompleteToolCall(new CompleteToolCall(i, toolExecutionRequest));
                        }
                    }
                    handler.onCompleteResponse(lc4jResponse);
                } else {
                    // Finish
                    handler.onCompleteResponse(map(
                                    CohereChatResponse.builder()
                                            .copy(lastCohereChatResponse)
                                            .text(partialContent.toString())
                                            .toolCalls(null)
                                            .finishReason(null)
                                            .build(),
                                    modelName,
                                    BaseCohereChatModel.map(lastCohereChatResponse.getFinishReason()))
                            .build());
                }
            } else {
                // Empty response
                handler.onCompleteResponse(map(
                                CohereChatResponse.builder()
                                        .text(partialContent.toString())
                                        .build(),
                                modelName,
                                FinishReason.STOP)
                        .build());
            }
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                LOGGER.error("Error in user error handler", userException);
            }
        }
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Model builder.
     */
    public static class Builder extends BaseCohereChatModel.Builder<OciGenAiCohereStreamingChatModel, Builder> {

        Builder() {}

        @Override
        Builder self() {
            return this;
        }

        public OciGenAiCohereStreamingChatModel build() {
            return new OciGenAiCohereStreamingChatModel(this);
        }
    }
}
