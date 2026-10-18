package dev.langchain4j.community.mcp.server;

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

        /** Always {@code null}: this server does not paginate prompts yet. */
        public String getNextCursor() {
            return null;
        }
    }
}
