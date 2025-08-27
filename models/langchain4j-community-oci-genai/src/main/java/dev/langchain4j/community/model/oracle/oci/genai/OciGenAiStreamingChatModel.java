package dev.langchain4j.community.model.oracle.oci.genai;

import com.oracle.bmc.generativeaiinference.model.DedicatedServingMode;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 * <p>To learn more about the service, see the <a href="https://docs.oracle.com/iaas/Content/generative-ai/home.htm">Generative AI documentation</a>
 */
public class OciGenAiStreamingChatModel extends BaseGenericChatModel<OciGenAiStreamingChatModel>
        implements StreamingChatModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(OciGenAiStreamingChatModel.class);
    private final Builder builder;

    OciGenAiStreamingChatModel(Builder builder) {
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
        var bmcChatRequest = prepareRequest(chatRequest).isStream(true).build();
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
            var streamingResponseBuilder = new GenericStreamingResponseBuilder(modelName);
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                LOGGER.debug("Partial response: {}", line);
                var chatChoice = Serializer.getDefault()
                        .readValue(
                                line.replaceFirst("data: ", ""),
                                com.oracle.bmc.generativeaiinference.model.ChatChoice.class);

                streamingResponseBuilder.append(chatChoice);

                try {
                    var aiMessage =
                            OciGenAiChatModel.map(chatChoice, modelName, null).aiMessage();

                    if (aiMessage != null) {
                        if (aiMessage.text() != null) {
                            handler.onPartialResponse(aiMessage.text());
                        }
                        var toolExecutionRequests = aiMessage.toolExecutionRequests();
                        if (toolExecutionRequests != null) {
                            for (int i = 0; i < toolExecutionRequests.size(); i++) {
                                var execReq = toolExecutionRequests.get(i);
                                if (Utils.isNotNullOrEmpty(execReq.name())
                                        && Utils.isNotNullOrEmpty(execReq.arguments())) {
                                    handler.onPartialToolCall(PartialToolCall.builder()
                                            .id(execReq.id())
                                            .name(execReq.name())
                                            .partialArguments(execReq.arguments())
                                            .index(i)
                                            .build());
                                }
                            }
                        }
                    }
                } catch (Exception userException) {
                    handler.onError(userException);
                }
            }

            final var chatResponse = streamingResponseBuilder.build();

            var aiMessage = chatResponse.aiMessage();

            if (aiMessage != null && aiMessage.toolExecutionRequests() != null) {
                for (int i = 0; i < aiMessage.toolExecutionRequests().size(); i++) {
                    var execReq = aiMessage.toolExecutionRequests().get(i);
                    if (Utils.isNotNullOrEmpty(execReq.arguments())) {
                        handler.onCompleteToolCall(new CompleteToolCall(i, execReq));
                    }
                }
            }

            handler.onCompleteResponse(chatResponse);
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
    public static class Builder extends BaseGenericChatModel.Builder<OciGenAiStreamingChatModel, Builder> {

        Builder() {}

        @Override
        Builder self() {
            return this;
        }

        public OciGenAiStreamingChatModel build() {
            return new OciGenAiStreamingChatModel(this);
        }
    }
}
