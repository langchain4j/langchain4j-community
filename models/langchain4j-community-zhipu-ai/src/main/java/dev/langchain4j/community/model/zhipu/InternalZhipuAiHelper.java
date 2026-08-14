package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.zhipu.chat.AssistantMessage;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.zhipu.chat.Content;
import dev.langchain4j.community.model.zhipu.chat.Function;
import dev.langchain4j.community.model.zhipu.chat.FunctionCall;
import dev.langchain4j.community.model.zhipu.chat.Message;
import dev.langchain4j.community.model.zhipu.chat.Parameters;
import dev.langchain4j.community.model.zhipu.chat.ResponseFormat;
import dev.langchain4j.community.model.zhipu.chat.Tool;
import dev.langchain4j.community.model.zhipu.chat.ToolCall;
import dev.langchain4j.community.model.zhipu.chat.ToolChoiceMode;
import dev.langchain4j.community.model.zhipu.chat.ToolMessage;
import dev.langchain4j.community.model.zhipu.chat.ToolType;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.zhipu.shared.ErrorResponse;
import dev.langchain4j.community.model.zhipu.shared.Usage;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Internal
class InternalZhipuAiHelper {

    private static final String FINISH_REASON_SENSITIVE = "sensitive";
    private static final String FINISH_REASON_OTHER = "other";

    static List<Embedding> toEmbed(List<EmbeddingResponse> response) {
        return response.stream()
                .map(zhipuAiEmbedding -> Embedding.from(zhipuAiEmbedding.getEmbedding()))
                .collect(Collectors.toList());
    }

    static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> Tool.from(toFunction(toolSpecification)))
                .collect(Collectors.toList());
    }

    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toFunctionParameters(toolSpecification))
                .build();
    }

    private static Parameters toFunctionParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return Parameters.builder()
                    .properties(toMap(parameters.properties()))
                    .required(parameters.required())
                    .build();
        } else {
            return Parameters.builder().build();
        }
    }

    static List<Message> toZhipuAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(InternalZhipuAiHelper::toZhipuAiMessage).collect(Collectors.toList());
    }

    static ResponseFormat toZhipuResponseFormat(dev.langchain4j.model.chat.request.ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }

        if (responseFormat.jsonSchema() != null) {
            throw new UnsupportedFeatureException("JSON response format with json schema does not support.");
        }

        return ResponseFormat.builder()
                .type(responseFormat.type().toString().toLowerCase())
                .build();
    }

    static ToolChoiceMode toToolChoice(ToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        if (ToolChoice.AUTO != toolChoice) {
            throw new UnsupportedFeatureException("ToolChoice." + toolChoice.name() + " does not support");
        }

        return ToolChoiceMode.AUTO;
    }

    private static Message toZhipuAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage systemMessage) {
            return dev.langchain4j.community.model.zhipu.chat.SystemMessage.builder()
                    .content(systemMessage.text())
                    .build();
        }

        if (message instanceof UserMessage userMessage) {
            if (userMessage.hasSingleText()) {
                return dev.langchain4j.community.model.zhipu.chat.UserMessage.from(userMessage.singleText());
            }
            List<Content> contents = new ArrayList<>(userMessage.contents().size());
            userMessage.contents().forEach(content -> {
                if (content instanceof TextContent textContent) {
                    contents.add(dev.langchain4j.community.model.zhipu.chat.TextContent.builder()
                            .text(textContent.text())
                            .build());
                }
                if (content instanceof ImageContent imageContent) {
                    Image image = imageContent.image();
                    contents.add(dev.langchain4j.community.model.zhipu.chat.ImageContent.builder()
                            .imageUrl(dev.langchain4j.community.model.zhipu.chat.Image.builder()
                                    .url(image.url() != null ? image.url().toString() : image.base64Data())
                                    .build())
                            .build());
                }
            });
            return dev.langchain4j.community.model.zhipu.chat.UserMessage.from(contents);
        }

        if (message instanceof AiMessage aiMessage) {
            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.builder()
                        .content(aiMessage.text())
                        .reasoningContent(aiMessage.thinking())
                        .build();
            }
            List<ToolCall> toolCallArrayList = new ArrayList<>();
            for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
                toolCallArrayList.add(ToolCall.builder()
                        .function(FunctionCall.builder()
                                .name(executionRequest.name())
                                .arguments(executionRequest.arguments())
                                .build())
                        .type(ToolType.FUNCTION)
                        .id(executionRequest.id())
                        .build());
            }
            return AssistantMessage.builder()
                    .content(aiMessage.text())
                    .reasoningContent(aiMessage.thinking())
                    .toolCalls(toolCallArrayList)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage resultMessage) {
            if (resultMessage.hasSingleText()) {
                return ToolMessage.builder()
                        .toolCallId(resultMessage.id())
                        .content(resultMessage.text())
                        .build();
            }
            // ZhipuAI does not support multi-modal content (e.g., image) in tool results
            boolean hasNonTextContent = resultMessage.contents().stream().anyMatch(c -> !(c instanceof TextContent));
            if (hasNonTextContent) {
                throw new UnsupportedFeatureException(
                        "ZhipuAI does not support non-text content in tool execution results. "
                                + "Tool '" + resultMessage.toolName() + "' returned content types: "
                                + resultMessage.contents().stream()
                                        .map(c -> c.getClass().getSimpleName())
                                        .toList());
            }
            // Multi-text content without images - extract text content
            String textContent = resultMessage.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .findFirst()
                    .orElse(null);
            return ToolMessage.builder()
                    .toolCallId(resultMessage.id())
                    .content(textContent)
                    .build();
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage message = response.getChoices().get(0).getMessage();
        String text = message.getContent();
        String reasoningContent = message.getReasoningContent();

        AiMessage.Builder aiMessageBuilder = AiMessage.builder()
                .text(isNullOrBlank(text) ? null : text)
                .thinking(isNullOrBlank(reasoningContent) ? null : reasoningContent);

        if (isNullOrEmpty(message.getToolCalls())) {
            return aiMessageBuilder.build();
        }

        return aiMessageBuilder
                .toolExecutionRequests(specificationsFrom(message.getToolCalls()))
                .build();
    }

    static List<ToolExecutionRequest> specificationsFrom(List<ToolCall> toolCalls) {
        List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            specifications.add(ToolExecutionRequest.builder()
                    .id(toolCall.getId())
                    .name(toolCall.getFunction().getName())
                    .arguments(toolCall.getFunction().getArguments())
                    .build());
        }
        return specifications;
    }

    static Usage getEmbeddingUsage(List<EmbeddingResponse> responses) {
        Usage tokenUsage = Usage.builder()
                .completionTokens(0)
                .promptTokens(0)
                .totalTokens(0)
                .build();

        for (EmbeddingResponse response : responses) {
            tokenUsage.add(response.getUsage());
        }
        return tokenUsage;
    }

    static TokenUsage tokenUsageFrom(Usage zhipuUsage) {
        if (zhipuUsage == null) {
            return null;
        }
        return new TokenUsage(
                zhipuUsage.getPromptTokens(), zhipuUsage.getCompletionTokens(), zhipuUsage.getTotalTokens());
    }

    /**
     * error code see <a href="https://open.bigmodel.cn/dev/api/error-code/error-code-v4">error codes document</a>
     */
    private static ChatCompletionChoice toChatErrorChoice(Throwable throwable) {
        if (throwable instanceof HttpException httpException) {
            String message = httpException.getMessage();
            if (Utils.isNullOrBlank(message)) {
                return ChatCompletionChoice.builder()
                        .finishReason(FINISH_REASON_OTHER)
                        .build();
            }
            ErrorResponse errorResponse = Json.fromJson(message, ErrorResponse.class);
            String code = errorResponse.getError().get("code");
            return ChatCompletionChoice.builder()
                    .message(AssistantMessage.builder()
                            .content(errorResponse.getError().get("message"))
                            .build())
                    .finishReason(getFinishReason(code))
                    .build();
        }
        return ChatCompletionChoice.builder()
                .message(AssistantMessage.builder()
                        .content(throwable.getMessage())
                        .build())
                .finishReason(FINISH_REASON_OTHER)
                .build();
    }

    public static ZhipuAiException toZhipuAiException(HttpException httpException) {
        String message = httpException.getMessage();
        if (Utils.isNullOrBlank(message)) {
            return new ZhipuAiException(httpException.getMessage());
        }
        ErrorResponse errorResponse = Json.fromJson(message, ErrorResponse.class);
        String code = errorResponse.getError().get("code");
        String errorMessage = errorResponse.getError().get("message");
        return new ZhipuAiException(code, errorMessage);
    }

    static String getFinishReason(Object o) {
        if (o instanceof String errCode && "1301".equals(errCode)) {
            // 1301: 系统检测到输入或生成内容可能包含不安全或敏感内容，请您避免输入易产生敏感内容的提示语，感谢您的配合
            return FINISH_REASON_SENSITIVE;
        }
        if (o instanceof ZhipuAiException exception && "1301".equals(exception.getCode())) {
            return FINISH_REASON_SENSITIVE;
        }
        return FINISH_REASON_OTHER;
    }

    static boolean isSuccessFinishReason(FinishReason finishReason) {
        return !CONTENT_FILTER.equals(finishReason) && !OTHER.equals(finishReason);
    }

    static FinishReason finishReasonFrom(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case "stop" -> STOP;
            case "length" -> LENGTH;
            case "tool_calls" -> TOOL_EXECUTION;
            case "sensitive" -> CONTENT_FILTER;
            default -> OTHER;
        };
    }
}
