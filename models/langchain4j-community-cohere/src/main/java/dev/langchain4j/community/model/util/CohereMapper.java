package dev.langchain4j.community.model.util;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.chat.CohereResponseFormat;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.message.CohereAiMessage;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.message.CohereSystemMessage;
import dev.langchain4j.community.model.client.chat.message.CohereToolMessage;
import dev.langchain4j.community.model.client.chat.message.CohereUserMessage;
import dev.langchain4j.community.model.client.chat.message.content.CohereImageUrl;
import dev.langchain4j.community.model.client.chat.response.CohereResponseMessage;
import dev.langchain4j.community.model.client.chat.tool.CohereFunction;
import dev.langchain4j.community.model.client.chat.tool.CohereFunctionCall;
import dev.langchain4j.community.model.client.chat.tool.CohereTool;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.output.FinishReason;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.community.model.client.CohereResponseFormatType.JSON_OBJECT;
import static dev.langchain4j.community.model.client.chat.content.CohereContent.image;
import static dev.langchain4j.community.model.client.chat.content.CohereContent.text;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.THINKING;
import static dev.langchain4j.community.model.client.chat.tool.CohereToolType.FUNCTION;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

@Internal
public class CohereMapper {

    private static final String TOOL_PLAN_KEY = "tool_plan";

    private static final Map<String, Object> EMPTY_SCHEMA = toMap(JsonObjectSchema.builder().build());
    private static final CohereResponseFormat JSON_MODE_SCHEMA = CohereResponseFormat.builder()
            .type(JSON_OBJECT)
            .build();

    private CohereMapper() {}

    public static List<CohereMessage> toCohereChatMessages(List<ChatMessage> chatMessages) {
        return chatMessages.stream()
                .map(CohereMapper::toCohereChatMessage)
                .toList();
    }

    private static CohereMessage toCohereChatMessage(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return CohereSystemMessage.from(systemMessage.text());
        }

        if (chatMessage instanceof UserMessage userMessage) {
            return CohereUserMessage.from(userMessage.contents()
                    .stream()
                    .map(CohereMapper::toCohereContent)
                    .toList());
        }

        if (chatMessage instanceof AiMessage aiMessage) {
            CohereAiMessage.Builder builder = CohereAiMessage.builder();

            if (aiMessage.text() != null) {
                builder.content(text(aiMessage.text()));
            }

            if (aiMessage.hasToolExecutionRequests()) {
                builder.toolCalls(aiMessage.toolExecutionRequests().stream()
                        .map(tc -> CohereToolCall.from(
                                tc.id(),
                                CohereFunctionCall.from(tc.name(), tc.arguments())
                        ))
                        .toList());
            }

            return builder.build();
        }

        if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return CohereToolMessage.from(
                    toolExecutionResultMessage.id(),
                    toolExecutionResultMessage.text());
        }

        throw illegalArgument("Unsupported chat message type: " + chatMessage);
    }

    public static CohereContent toCohereContent(Content content) {
        if (content instanceof TextContent textContent) {
            return text(textContent.text());
        }

        if (content instanceof ImageContent imageContent) {
            return image(toCohereImage(imageContent.image(), imageContent.detailLevel()));
        }

        throw illegalArgument("Unsupported message content type: " + content);
    }

    private static CohereImageUrl toCohereImage(Image image, ImageContent.DetailLevel detail) {
        String url;

        if (image.url() != null) {
            url = image.url().toString();
        } else {
            url = "data:%s;base64,%s".formatted(image.mimeType(), image.base64Data());
        }

        return CohereImageUrl.builder().url(url).detail(detail).build();
    }

    public static List<CohereTool> toCohereTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(CohereMapper::toCohereTool)
                .toList();
    }

    private static CohereTool toCohereTool(ToolSpecification toolSpecification) {
        Map<String, Object> parameters = toolSpecification.parameters() == null
                ? EMPTY_SCHEMA // Argumentless tools
                : toMap(toolSpecification.parameters());

        return CohereTool.builder()
                .type(FUNCTION)
                .function(CohereFunction.builder()
                        .name(toolSpecification.name())
                        .description(toolSpecification.description())
                        .parameters(parameters)
                        .build())
                .build();
    }

    public static CohereResponseFormat toCohereResponseFormat(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == ResponseFormatType.TEXT) {
            return null;
        }

        if (responseFormat.jsonSchema() == null) {
            return JSON_MODE_SCHEMA;
        }

        return CohereResponseFormat.builder()
                .type(JSON_OBJECT)
                .jsonSchema(toCohereSchema(responseFormat.jsonSchema().rootElement()))
                .build();
    }

    public static Map<String, Object> toCohereSchema(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonObjectSchema objectSchema) {
            Map<String, Object> map = new LinkedHashMap<>();

            map.put("type", "object");

            if (objectSchema.description() != null) {
                map.put("description", objectSchema.description());
            }

            Map<String, Object> properties = new LinkedHashMap<>();
            objectSchema.properties()
                    .forEach((property, value) -> properties.put(property, toCohereSchema(value)));

            map.put("properties", properties);

            // Set all properties as required if not specified
            // (an object schema may not have zero required properties, as specified in the Cohere API docs).
            map.put("required", objectSchema.required().isEmpty()
                    ? objectSchema.properties().keySet()
                    : objectSchema.required());

             if (objectSchema.additionalProperties() != null) {
                 map.put("additionalProperties", objectSchema.additionalProperties());
            }

            if (!objectSchema.definitions().isEmpty()) {
                map.put("$defs", mapDefs(objectSchema.definitions()));
            }

            return map;
        }

        if (jsonSchemaElement instanceof JsonArraySchema arraySchema) {
            Map<String, Object> map = new LinkedHashMap<>();

            map.put("type", "array");

            if (arraySchema.description() != null) {
                map.put("description", arraySchema.description());
            }

            if (arraySchema.items() != null) {
                map.put("items", toCohereSchema(arraySchema.items()));
            } else {
                map.put("items", Collections.emptyMap());
            }

            return map;
        }

        return toMap(jsonSchemaElement);
    }

    private static Map<String, Map<String, Object>> mapDefs(Map<String, JsonSchemaElement> defs) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        defs.forEach((property, schema) -> map.put(property, toCohereSchema(schema)));

        return map;
    }

    public static AiMessage toAiMessage(CohereResponseMessage responseMessage) {
        String text = null;
        String thinking = null;

        if (!isNullOrEmpty(responseMessage.getContent())) {
            text = responseMessage.getContent().stream()
                    .filter(content -> content.getType() == TEXT)
                    .map(CohereContent::getText)
                    .collect(collectingAndThen(joining("\n"), s -> s.isEmpty() ? null : s));

            thinking = responseMessage.getContent().stream()
                    .filter(content -> content.getType() == THINKING)
                    .map(CohereContent::getThinking)
                    .collect(collectingAndThen(joining("\n"), s -> s.isEmpty() ? null : s));
        }

        return AiMessage.builder()
                .text(text)
                .thinking(thinking)
                .toolExecutionRequests(isNullOrEmpty(responseMessage.getToolCalls())
                        ? null
                        : responseMessage.getToolCalls().stream()
                                .map(CohereMapper::toToolExecutionRequest)
                                .toList())
                .attributes(isNullOrEmpty(responseMessage.getToolPlan())
                        ? null
                        : toAiMessageAttributes(responseMessage.getToolPlan()))
                .build();
    }

    public static ToolExecutionRequest toToolExecutionRequest(CohereToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(toolCall.getFunction().getName())
                .arguments(toolCall.getFunction().getArguments())
                .build();
    }

    public static Map<String, Object> toAiMessageAttributes(String toolPlan) {
        return Map.of(TOOL_PLAN_KEY, toolPlan);
    }

    public static FinishReason toFinishReason(String finishReason) {
        if (isNullOrBlank(finishReason)) return null;
        return switch (finishReason) {
            case "COMPLETE", "STOP_SEQUENCE" -> STOP;
            case "MAX_TOKENS" -> LENGTH;
            case "TOOL_CALL" -> TOOL_EXECUTION;
            default -> OTHER;
        };
    }
}
