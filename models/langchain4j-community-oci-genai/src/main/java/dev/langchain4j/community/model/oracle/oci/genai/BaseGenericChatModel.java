package dev.langchain4j.community.model.oracle.oci.genai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatContent;
import com.oracle.bmc.generativeaiinference.model.FunctionCall;
import com.oracle.bmc.generativeaiinference.model.FunctionDefinition;
import com.oracle.bmc.generativeaiinference.model.GenericChatRequest;
import com.oracle.bmc.generativeaiinference.model.ImageContent;
import com.oracle.bmc.generativeaiinference.model.ImageUrl;
import com.oracle.bmc.generativeaiinference.model.Message;
import com.oracle.bmc.generativeaiinference.model.SystemMessage;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.model.ToolCall;
import com.oracle.bmc.generativeaiinference.model.ToolChoice;
import com.oracle.bmc.generativeaiinference.model.ToolChoiceAuto;
import com.oracle.bmc.generativeaiinference.model.ToolChoiceFunction;
import com.oracle.bmc.generativeaiinference.model.ToolChoiceNone;
import com.oracle.bmc.generativeaiinference.model.ToolChoiceRequired;
import com.oracle.bmc.generativeaiinference.model.ToolDefinition;
import com.oracle.bmc.generativeaiinference.model.ToolMessage;
import com.oracle.bmc.generativeaiinference.model.UserMessage;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class BaseGenericChatModel<T extends BaseGenericChatModel<T>> extends BaseChatModel<T> {

    private final Builder<?, ?> builder;

    BaseGenericChatModel(Builder<?, ?> builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        var modelBuilderParams = ChatRequestParameters.builder()
                .modelName(builder.modelName())
                .frequencyPenalty(builder.frequencyPenalty())
                .maxOutputTokens(builder.maxTokens())
                .presencePenalty(builder.presencePenalty())
                .stopSequences(builder.stop())
                .temperature(builder.temperature())
                .topK(builder.topK())
                .topP(builder.topP())
                .build();

        return ChatRequestParameters.builder()
                .overrideWith(modelBuilderParams)
                .overrideWith(builder.defaultRequestParameters())
                .build();
    }

    /**
     * Maps lc4j chat request and sets configured properties.
     *
     * @param lc4jReq lc4j chat request
     * @return OCI BMC generic chat request
     */
    protected GenericChatRequest.Builder prepareRequest(ChatRequest lc4jReq) {
        validateRequest(lc4jReq);

        var bmcBuilder = GenericChatRequest.builder();

        setIfNotNull(lc4jReq.messages(), m -> m.stream().map(this::map).toList(), bmcBuilder::messages);

        var defaultParams = this.defaultRequestParameters();
        setIfNotNull(defaultParams.stopSequences(), bmcBuilder::stop);
        setIfNotNull(defaultParams.topK(), bmcBuilder::topK);
        setIfNotNull(defaultParams.topP(), bmcBuilder::topP);
        setIfNotNull(defaultParams.temperature(), bmcBuilder::temperature);
        setIfNotNull(defaultParams.frequencyPenalty(), bmcBuilder::frequencyPenalty);
        setIfNotNull(defaultParams.presencePenalty(), bmcBuilder::presencePenalty);
        setIfNotNull(defaultParams.maxOutputTokens(), bmcBuilder::maxTokens);
        setIfNotNull(defaultParams.toolSpecifications(), this::map, bmcBuilder::tools);
        setIfNotNull(
                defaultParams.toolChoice(), c -> map(c, defaultParams.toolSpecifications()), bmcBuilder::toolChoice);

        // Common for Generic and Cohere
        setIfNotNull(builder.maxTokens(), bmcBuilder::maxTokens);
        setIfNotNull(builder.topK(), bmcBuilder::topK);
        setIfNotNull(builder.topP(), bmcBuilder::topP);
        setIfNotNull(builder.temperature(), bmcBuilder::temperature);
        setIfNotNull(builder.frequencyPenalty(), bmcBuilder::frequencyPenalty);
        setIfNotNull(builder.presencePenalty(), bmcBuilder::presencePenalty);
        setIfNotNull(builder.seed(), bmcBuilder::seed);
        setIfNotNull(builder.stop(), bmcBuilder::stop);

        // Generic specific
        setIfNotNull(builder.numGenerations(), bmcBuilder::numGenerations);
        setIfNotNull(builder.logProbs(), bmcBuilder::logProbs);
        setIfNotNull(builder.logitBias(), bmcBuilder::logitBias);

        // Per-request overrides
        var params = lc4jReq.parameters();
        setIfNotNull(params.maxOutputTokens(), bmcBuilder::maxTokens);
        setIfNotNull(params.topK(), bmcBuilder::topK);
        setIfNotNull(params.topP(), bmcBuilder::topP);
        setIfNotNull(params.temperature(), bmcBuilder::temperature);
        setIfNotNull(params.frequencyPenalty(), bmcBuilder::frequencyPenalty);
        setIfNotNull(params.presencePenalty(), bmcBuilder::presencePenalty);
        setIfNotNull(params.stopSequences(), bmcBuilder::stop);
        setIfNotNull(params.toolSpecifications(), this::map, bmcBuilder::tools);
        setIfNotNull(params.toolChoice(), c -> map(c, params.toolSpecifications()), bmcBuilder::toolChoice);

        return bmcBuilder;
    }

    protected void validateRequest(ChatRequest lc4jReq) {
        Stream.of(lc4jReq.responseFormat(), lc4jReq.parameters().responseFormat())
                .filter(Objects::nonNull)
                .map(ResponseFormat::type)
                .filter(r -> r == ResponseFormatType.JSON)
                .findFirst()
                .ifPresent(r -> {
                    throw new UnsupportedFeatureException("Generic chat models do not support JSON response format.");
                });
    }

    private ToolChoice map(
            dev.langchain4j.model.chat.request.ToolChoice choice, List<ToolSpecification> toolSpecifications) {
        return switch (choice) {
            case NONE -> ToolChoiceNone.builder().build();
            case AUTO -> ToolChoiceAuto.builder().build();
            case REQUIRED -> {
                if (toolSpecifications.size() == 1) {
                    // Some GenAi models don't support required but do support named tool choice
                    // use named directly when exactly one tool is specified
                    yield ToolChoiceFunction.builder()
                            .name(toolSpecifications.get(0).name())
                            .build();
                }
                yield ToolChoiceRequired.builder().build();
            }
        };
    }

    private Message map(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case USER -> {
                var userMessage = (dev.langchain4j.data.message.UserMessage) chatMessage;
                yield UserMessage.builder()
                        .content(userMessage.contents().stream()
                                .map(BaseGenericChatModel::map)
                                .toList())
                        .build();
            }
            case SYSTEM -> {
                var systemMessage = (dev.langchain4j.data.message.SystemMessage) chatMessage;
                yield SystemMessage.builder()
                        .content(List.of(
                                TextContent.builder().text(systemMessage.text()).build()))
                        .build();
            }
            case AI -> {
                var aiMessage = (dev.langchain4j.data.message.AiMessage) chatMessage;

                var assistantMessageBuilder = AssistantMessage.builder();

                if (aiMessage.hasToolExecutionRequests()) {
                    var toolCalls = new ArrayList<ToolCall>();
                    for (ToolExecutionRequest toolExecReq : aiMessage.toolExecutionRequests()) {
                        toolCalls.add(FunctionCall.builder()
                                .name(toolExecReq.name())
                                .id(toolExecReq.id())
                                .arguments(toolExecReq.arguments())
                                .build());
                    }
                    assistantMessageBuilder.toolCalls(toolCalls);
                }

                yield assistantMessageBuilder
                        .content(List.of(
                                TextContent.builder().text(aiMessage.text()).build()))
                        .build();
            }
            case TOOL_EXECUTION_RESULT -> {
                var toolMessage = (dev.langchain4j.data.message.ToolExecutionResultMessage) chatMessage;
                yield ToolMessage.builder()
                        .content(List.of(
                                TextContent.builder().text(toolMessage.text()).build()))
                        .toolCallId(toolMessage.id())
                        .build();
            }
            default -> throw new IllegalStateException("Unsupported chat message: " + chatMessage.type());
        };
    }

    private static ChatContent map(Content lc4jContent) {
        return switch (lc4jContent.type()) {
            case TEXT -> {
                var textContent = (dev.langchain4j.data.message.TextContent) lc4jContent;
                yield TextContent.builder().text(textContent.text()).build();
            }
            case IMAGE -> {
                var imageContent = (dev.langchain4j.data.message.ImageContent) lc4jContent;
                Image image = imageContent.image();
                var ociImageBuilder = ImageUrl.builder();
                if (image.url() != null) {
                    ociImageBuilder.url(image.url().toString());
                } else {
                    // rfc2397
                    ociImageBuilder.url("data:" + image.mimeType() + ";base64," + image.base64Data());
                }

                yield ImageContent.builder()
                        .imageUrl(ociImageBuilder
                                .detail(ImageUrl.Detail.create(
                                        imageContent.detailLevel().name()))
                                .build())
                        .build();
            }
            default ->
                throw new IllegalStateException("Unsupported content type: "
                        + lc4jContent.type()
                        + " only TEXT and IMAGE content types are supported.");
        };
    }

    private List<ToolDefinition> map(List<ToolSpecification> toolSpecification) {
        return toolSpecification.stream().map(this::map).collect(Collectors.toList());
    }

    private ToolDefinition map(ToolSpecification toolSpecification) {
        var b = FunctionDefinition.builder();

        if (toolSpecification.parameters() != null) {
            final JsonObjectSchema lc4jParams = toolSpecification.parameters();

            ToolFunctionParameters result = new ToolFunctionParameters();

            for (var entry : lc4jParams.properties().entrySet()) {
                Map<String, Object> map = JsonSchemaElementUtils.toMap(entry.getValue());
                result.setProperties(Map.of(entry.getKey(), map));
                result.required.add(entry.getKey());
            }
            b.parameters(result);
        }

        return b.name(toolSpecification.name())
                .description(toolSpecification.description())
                .build();
    }

    /**
     * <pre>{@code
     * {
     *     "type": "function",
     *     "function": {
     *         "name": "currentTime",
     *         "description": "Returns current local time now at provided location.",
     *         "parameters": {
     *             "type": "object",
     *             "properties": {
     *                 "location": {
     *                     "type": "string",
     *                     "description": "The location where the time will be determined."
     *                 }
     *             },
     *             "required": [
     *                 "location"
     *             ]
     *         }
     *     }
     * }
     * }</pre>
     */
    @JsonPropertyOrder({"type", "properties", "required"})
    static class ToolFunctionParameters {

        @JsonProperty("type")
        private String type = "object";

        @JsonProperty("properties")
        private Map<String, Object> properties = new HashMap<>();

        @JsonProperty("required")
        private List<String> required = new ArrayList<>();

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        @Override
        public String toString() {
            try {
                return Serializer.getDefault().writeValueAsString(this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    abstract static class Builder<T extends BaseGenericChatModel<T>, B extends Builder<T, B>>
            extends BaseChatModel.Builder<T, B> {

        private Integer numGenerations;
        private Integer logProbs;
        private Object logitBias;

        protected Builder() {}

        /**
         * The number of generated texts that will be returned.
         *
         * @param numGenerations Value to set
         * @return builder
         */
        public B numGenerations(Integer numGenerations) {
            this.numGenerations = numGenerations;
            return self();
        }

        Integer numGenerations() {
            return numGenerations;
        }

        /**
         * Includes the logarithmic probabilities for the most likely output tokens and the chosen tokens.
         * For example, if the log probability is 5, the API returns a list of the 5 most likely tokens.
         * The API returns the log probability of the sampled token,
         * so there might be up to logprobs+1 elements in the response.
         *
         * @param logProbs Value to set
         * @return builder
         */
        public B logProbs(Integer logProbs) {
            this.logProbs = logProbs;
            return self();
        }

        Integer logProbs() {
            return logProbs;
        }

        /**
         * Modifies the likelihood of specified tokens that appear in the completion.
         * Example: '{"6395": 2, "8134": 1, "21943": 0.5, "5923": -100}'
         *
         * @param logitBias Value to set
         * @return builder
         */
        public B logitBias(Object logitBias) {
            this.logitBias = logitBias;
            return self();
        }

        Object logitBias() {
            return logitBias;
        }

        /**
         * Build new instance.
         *
         * @return the instance
         */
        public abstract T build();
    }
}
