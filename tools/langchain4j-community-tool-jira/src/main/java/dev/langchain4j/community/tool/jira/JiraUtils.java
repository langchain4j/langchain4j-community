package dev.langchain4j.community.tool.jira;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class JiraUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DESCRIPTION_LIMIT = 500;

    private JiraUtils() {}

    static JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            return OBJECT_MAPPER.nullNode();
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException e) {
            throw new JiraClientException("Failed to parse JSON response from Jira", e);
        }
    }

    static String writeJson(JsonNode payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new JiraClientException("Failed to serialize JSON request for Jira", e);
        }
    }

    static URI normalizeBaseUrl(String baseUrl) {
        String value = ensureNotBlank(baseUrl, "baseUrl");
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return URI.create(trimmed);
    }

    static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String extractDescription(JsonNode descriptionNode) {
        if (descriptionNode == null || descriptionNode.isNull()) {
            return "";
        }
        String extracted = extractAdfText(descriptionNode).trim();
        if (extracted.isEmpty()) {
            return "";
        }
        return truncate(extracted, DESCRIPTION_LIMIT);
    }

    static String buildDescriptionLine(JsonNode descriptionNode, String descriptionText) {
        if (descriptionNode == null || descriptionNode.isNull()) {
            return "Description: (none)";
        }
        if (descriptionText.isEmpty()) {
            return "Description: (ADF content available)";
        }
        return "Description: " + descriptionText;
    }

    static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    static String extractAdfText(JsonNode adfNode) {
        if (adfNode == null || adfNode.isNull()) {
            return "";
        }
        if (adfNode.has("content") && adfNode.get("content").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode child : adfNode.get("content")) {
                String childText = extractAdfText(child).trim();
                if (!childText.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(System.lineSeparator());
                    }
                    sb.append(childText);
                }
            }
            return sb.toString();
        }
        if (adfNode.has("text")) {
            return adfNode.get("text").asText("");
        }
        return "";
    }

    static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    static String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    static JsonNode getNode(JsonNode root, String... path) {
        JsonNode current = root;
        for (String segment : path) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    static String formatError(RuntimeException exception) {
        if (exception instanceof JiraClientException jiraException) {
            String message = extractErrorMessage(jiraException.getResponseBody());
            if (message == null || message.isBlank()) {
                if (jiraException.getStatusCode() > 0) {
                    message = "Jira API request failed with status " + jiraException.getStatusCode();
                } else {
                    message = "Jira API request failed";
                }
            }
            return "Error: " + message;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Unexpected error while calling Jira";
        }
        return "Error: " + message;
    }

    static String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(responseBody);
            JsonNode errorMessages = node.get("errorMessages");
            if (errorMessages != null && errorMessages.isArray() && errorMessages.size() > 0) {
                return errorMessages.get(0).asText();
            }
            JsonNode errors = node.get("errors");
            if (errors != null && errors.isObject()) {
                var fields = errors.fieldNames();
                if (fields.hasNext()) {
                    String field = fields.next();
                    return field + ": " + errors.get(field).asText();
                }
            }
            JsonNode message = node.get("message");
            if (message != null && !message.isNull()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            return truncate(responseBody, 200);
        }
        return truncate(responseBody, 200);
    }

    static ObjectNode toADF(String text) {
        String safeText = text == null ? "" : text;
        ObjectNode doc = OBJECT_MAPPER.createObjectNode();
        doc.put("version", 1);
        doc.put("type", "doc");
        ArrayNode content = doc.putArray("content");
        ObjectNode paragraph = content.addObject();
        paragraph.put("type", "paragraph");
        ArrayNode paragraphContent = paragraph.putArray("content");
        ObjectNode textNode = paragraphContent.addObject();
        textNode.put("type", "text");
        textNode.put("text", safeText);
        return doc;
    }
}

