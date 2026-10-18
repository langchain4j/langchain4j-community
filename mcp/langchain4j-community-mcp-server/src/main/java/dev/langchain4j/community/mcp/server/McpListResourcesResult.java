package dev.langchain4j.community.mcp.server;

import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.List;
import java.util.Map;

/**
 * Response to {@code resources/list}: the registered resources with their metadata.
 */
public class McpListResourcesResult extends McpJsonRpcMessage {

    private final Result result;

    public McpListResourcesResult(Long id, List<Map<String, Object>> resources) {
        super(id);
        this.result = new Result(resources);
    }

    public Result getResult() {
        return result;
    }

    public static class Result {

        private final List<Map<String, Object>> resources;

        public Result(List<Map<String, Object>> resources) {
            this.resources = resources;
        }

        public List<Map<String, Object>> getResources() {
            return resources;
        }

        /** Always {@code null}: this server does not paginate resources yet. */
        public String getNextCursor() {
            return null;
        }
    }
}
