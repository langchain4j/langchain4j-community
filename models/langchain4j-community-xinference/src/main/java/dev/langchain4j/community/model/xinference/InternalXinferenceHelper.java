package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.ImageUtils.base64Image;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.chat.Function;
import dev.langchain4j.community.model.xinference.client.chat.Parameters;
import dev.langchain4j.community.model.xinference.client.chat.Tool;
import dev.langchain4j.community.model.xinference.client.chat.ToolType;
import dev.langchain4j.community.model.xinference.client.chat.message.AssistantMessage;
import dev.langchain4j.community.model.xinference.client.chat.message.Content;
import dev.langchain4j.community.model.xinference.client.chat.message.FunctionCall;
import dev.langchain4j.community.model.xinference.client.chat.message.Message;
import dev.langchain4j.community.model.xinference.client.chat.message.SystemMessage;
import dev.langchain4j.community.model.xinference.client.chat.message.ToolCall;
import dev.langchain4j.community.model.xinference.client.chat.message.ToolMessage;
import dev.langchain4j.community.model.xinference.client.chat.message.UserMessage;
import dev.langchain4j.community.model.xinference.client.chat.message.VideoUrl;
import dev.langchain4j.community.model.xinference.client.shared.CompletionUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class InternalXinferenceHelper {
    static List<Message> toXinferenceMessages(List<ChatMessage> messages) {
        if (isNullOrEmpty(messages)) {
            return null;
        }
        return messages.stream()
                .map(msg -> {
                    if (msg instanceof dev.langchain4j.data.message.SystemMessage message) {
                        return SystemMessage.of(message.text());
                    } else if (msg instanceof AiMessage message) {
                        if (!message.hasToolExecutionRequests()) {
                            return AssistantMessage.of(message.text());
                        }
                        List<ToolCall> list = message.toolExecutionRequests().stream()
                                .map(it -> ToolCall.builder()
                                        .id(it.id())
                                        .type(ToolType.FUNCTION)
                                        .function(FunctionCall.builder()
                                                .id(it.id())
                                                .name(it.name())
                                                .arguments(it.arguments())
                                                .build())
                                        .build())
                                .toList();
                        return AssistantMessage.of(message.text(), list.toArray(new ToolCall[0]));
                    } else if (msg instanceof dev.langchain4j.data.message.UserMessage message) {
                        if (message.hasSingleText()) {
                            return UserMessage.builder()
                                    .content(message.singleText())
                                    .name(message.name())
                                    .build();
                        } else {
                            List<Content> list = message.contents().stream()
                                    .map(item -> {
                                        if (item instanceof TextContent content) {
                                            return Content.text(content.text());
                                        } else if (item instanceof ImageContent content) {
                                            return Content.image(base64Image(
                                                    content.image(),
                                                    content.detailLevel().name()));
                                        } else if (item instanceof VideoContent content) {
                                            String url = null;
                                            Video video = content.video();
                                            if (Objects.nonNull(video.url())) {
                                                url = video.url().toString();
                                            } else if (isNotNullOrBlank(video.base64Data())) {
                                                url = saveDataAsTemporaryFile(video.base64Data(), video.mimeType());
                                            }
                                            return Content.video(VideoUrl.of(url));
                                        }
                                        throw illegalArgument("Unknown content type: " + item);
                                    })
                                    .toList();
                            return UserMessage.builder()
                                    .content(list)
                                    .name(message.name())
                                    .build();
                        }
                    } else if (msg instanceof ToolExecutionResultMessage message) {
                        return ToolMessage.of(message.id(), message.text());
                    }
                    throw illegalArgument("Unknown message type: " + msg.type());
                })
                .toList();
    }

    static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
        if (isNullOrEmpty(toolSpecifications)) {
            return null;
        }
        return toolSpecifications.stream().map(InternalXinferenceHelper::toTool).toList();
    }

    static Tool toTool(ToolSpecification toolSpecification) {
        return Tool.builder()
                .function(Function.builder()
                        .description(toolSpecification.description())
                        .name(toolSpecification.name())
                        .parameters(toParameters(toolSpecification))
                        .build())
                .build();
    }

    static Tool toToolChoice(ToolSpecification toolSpecification) {
        return Tool.builder()
                .function(Function.builder().name(toolSpecification.name()).build())
                .build();
    }

    static AiMessage aiMessageFrom(AssistantMessage assistantMessage) {
        String text = assistantMessage.getContent();
        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (!isNullOrEmpty(toolCalls)) {
            List<ToolExecutionRequest> toolExecutionRequests = toolCalls.stream()
                    .filter(toolCall -> toolCall.getType() == ToolType.FUNCTION)
                    .map(InternalXinferenceHelper::toToolExecutionRequest)
                    .toList();
            return isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(text, toolExecutionRequests);
        }
        return AiMessage.from(text);
    }

    private static ToolExecutionRequest toToolExecutionRequest(ToolCall toolCall) {
        FunctionCall functionCall = toolCall.getFunction();
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(functionCall.getName())
                .arguments(functionCall.getArguments())
                .build();
    }

    private static Parameters toParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            return Parameters.builder()
                    .properties(toMap(parameters.properties()))
                    .required(parameters.required())
                    .build();
        } else {
            return null;
        }
    }

    public static TokenUsage tokenUsageFrom(CompletionUsage usage) {
        if (Objects.isNull(usage)) {
            return null;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    public static FinishReason finishReasonFrom(String finishReason) {
        if (isNullOrBlank(finishReason)) {
            return null;
        }
        return switch (finishReason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "tool_calls", "function_call" -> FinishReason.TOOL_EXECUTION;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> null;
        };
    }

    static String saveDataAsTemporaryFile(String base64Data, String mimeType) {
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String tmpFileName = UUID.randomUUID().toString();
        if (isNotNullOrBlank(mimeType)) {
            // e.g. "image/png", "image/jpeg"...
            int lastSlashIndex = mimeType.lastIndexOf("/");
            if (lastSlashIndex >= 0 && lastSlashIndex < mimeType.length() - 1) {
                String fileSuffix = mimeType.substring(lastSlashIndex + 1);
                tmpFileName = tmpFileName + "." + fileSuffix;
            }
        }

        Path tmpFilePath = Paths.get(tmpDir, tmpFileName);
        byte[] data = Base64.getDecoder().decode(base64Data);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpFilePath.toAbsolutePath().toUri().toString();
    }
}
