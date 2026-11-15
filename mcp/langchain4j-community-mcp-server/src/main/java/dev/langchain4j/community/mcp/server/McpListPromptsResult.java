package dev.langchain4j.community.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;
import java.util.Map;

/**
 * Response to {@code prompts/list}: the registered prompts with their argument schemas.
 */
public class McpListPromptsResult extends McpJsonRpcMessage {

    private final Result result;

    public McpListPromptsResult(Long id, List<Map<String, Object>> prompts) {
        super(id);
        this.result = new Result(prompts);
    }

    public Result getResult() {
        return result;
    }

    public static class Result {

        private final List<Map<String, Object>> prompts;

        public Result(List<Map<String, Object>> prompts) {
            this.prompts = prompts;
        }

        public List<Map<String, Object>> getPrompts() {
            return prompts;
        }

        /**
         * Always {@code null}: this server does not paginate prompts yet.
         *
         * <p>{@code nextCursor} is an optional string in the MCP schema; when there is no next page
         * the field must be omitted, not serialized as {@code null}, otherwise strict clients reject
         * the whole response.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getNextCursor() {
            return null;
        }
    }
}
