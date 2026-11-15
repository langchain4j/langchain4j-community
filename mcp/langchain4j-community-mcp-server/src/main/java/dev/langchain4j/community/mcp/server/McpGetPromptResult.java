package dev.langchain4j.community.mcp.server;

import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;

/**
 * Response to {@code prompts/get}: the messages of one prompt, with the client's arguments filled
 * in.
 */
public class McpGetPromptResult extends McpJsonRpcMessage {

    private final Result result;

    public McpGetPromptResult(Long id, String description, List<PromptMessage> messages) {
        super(id);
        this.result = new Result(description, messages);
    }

    public Result getResult() {
        return result;
    }

    public static class Result {

        private final String description;
        private final List<PromptMessage> messages;

        public Result(String description, List<PromptMessage> messages) {
            this.description = description;
            this.messages = messages;
        }

        public String getDescription() {
            return description;
        }

        public List<PromptMessage> getMessages() {
            return messages;
        }
    }
}
