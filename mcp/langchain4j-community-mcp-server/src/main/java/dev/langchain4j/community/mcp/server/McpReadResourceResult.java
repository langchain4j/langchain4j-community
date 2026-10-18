package dev.langchain4j.community.mcp.server;

import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;
import java.util.Map;

/**
 * Response to {@code resources/read}: the content of one resource.
 */
public class McpReadResourceResult extends McpJsonRpcMessage {

    private final Result result;

    public McpReadResourceResult(Long id, List<Map<String, Object>> contents) {
        super(id);
        this.result = new Result(contents);
    }

    public Result getResult() {
        return result;
    }

    public static class Result {

        private final List<Map<String, Object>> contents;

        public Result(List<Map<String, Object>> contents) {
            this.contents = contents;
        }

        public List<Map<String, Object>> getContents() {
            return contents;
        }
    }
}
