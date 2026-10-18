package dev.langchain4j.community.mcp.server;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Map;

/**
 * One message of a prompt returned by a {@link Prompt @Prompt} method.
 *
 * <p>Serializes to the MCP wire format {@code {"role": "...", "content": {"type": "text", "text":
 * "..."}}}.
 */
public class PromptMessage {

    private final String role;
    private final Map<String, Object> content;

    private PromptMessage(String role, String text) {
        this.role = ensureNotBlank(role, "role");
        this.content = Map.of("type", "text", "text", ensureNotBlank(text, "text"));
    }

    public static PromptMessage user(String text) {
        return new PromptMessage("user", text);
    }

    public static PromptMessage assistant(String text) {
        return new PromptMessage("assistant", text);
    }

    public String getRole() {
        return role;
    }

    public Map<String, Object> getContent() {
        return content;
    }
}
