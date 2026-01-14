package dev.langchain4j.community.mcp.server;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.protocol.McpCallToolResult;
import dev.langchain4j.mcp.protocol.McpErrorResponse;
import dev.langchain4j.mcp.protocol.McpImplementation;
import dev.langchain4j.mcp.protocol.McpInitializeParams;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.protocol.McpListToolsResult;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal Model Context Protocol (MCP) server that exposes existing {@code @Tool}-annotated methods
 * over JSON-RPC.
 *
 * <p>Currently this server supports:
 *
 * <ul>
 *   <li>{@code initialize}
 *   <li>{@code tools/list}
 *   <li>{@code tools/call}
 * </ul>
 *
 * <p>This module is intentionally transport-agnostic. See {@code StdioMcpServerTransport} for a
 * process-based (stdin/stdout) transport implementation.
 */
public class McpServer {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final String DEFAULT_SERVER_NAME = "LangChain4j";
    private static final int ERROR_CODE_INVALID_REQUEST = -32600;
    private static final int ERROR_CODE_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_CODE_INVALID_PARAMS = -32602;
    private static final String PARAMS_FIELD = "params";
    private static final String ARGUMENTS_FIELD = "arguments";

    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Map<String, Object>> mcpTools;
    private final McpImplementation serverInfo;

    public McpServer(List<Object> tools) {
        this(tools, defaultServerInfo());
    }

    public McpServer(List<Object> tools, McpImplementation serverInfo) {
        ensureNotNull(tools, "tools");
        this.serverInfo = ensureNotNull(serverInfo, "serverInfo");

        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();
        for (Object tool : tools) {
            ensureNotNull(tool, "tool");
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(tool));
            addExecutors(tool, executors);
        }

        ToolSpecifications.validateSpecifications(specs);

        this.toolExecutors = Map.copyOf(executors);
        List<ToolSpecification> toolSpecifications = List.copyOf(specs);
        McpToolSchemaMapper toolSchemaMapper = new McpToolSchemaMapper();
        this.mcpTools = List.copyOf(toolSchemaMapper.toMcpTools(toolSpecifications));
    }

    public McpJsonRpcMessage handle(JsonNode message) {
        if (message == null) {
            return null;
        }

        Long id = extractId(message);
        if (id == null) {
            return null;
        }

        JsonNode methodNode = message.get("method");
        if (methodNode == null || methodNode.isNull() || !methodNode.isTextual()) {
            return invalidRequest(id, "Missing method");
        }

        String method = methodNode.asText();
        if (method.isBlank()) {
            return invalidRequest(id, "Missing method");
        }

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(parseInitializeRequest(id, message));
                case "tools/list" -> handleListTools(parseListToolsRequest(id, message));
                case "tools/call" -> handleCallTool(parseCallToolRequest(id, message));
                default ->
                    new McpErrorResponse(
                            id,
                            new McpErrorResponse.Error(
                                    ERROR_CODE_METHOD_NOT_FOUND, "Method not found: " + method, null));
            };
        } catch (Exception e) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, safeMessage(e), null));
        }
    }

    private McpErrorResponse invalidRequest(Long id, String message) {
        String safeMessage = (message == null || message.isBlank()) ? "Invalid Request" : "Invalid Request: " + message;
        return new McpErrorResponse(id, new McpErrorResponse.Error(ERROR_CODE_INVALID_REQUEST, safeMessage, null));
    }

    private McpInitializeResult handleInitialize(McpInitializeRequest request) {
        String protocolVersion = DEFAULT_PROTOCOL_VERSION;
        if (request.getParams() != null && request.getParams().getProtocolVersion() != null) {
            protocolVersion = request.getParams().getProtocolVersion();
        }
        McpInitializeResult.Capabilities capabilities =
                new McpInitializeResult.Capabilities(new McpInitializeResult.Capabilities.Tools(null));
        McpInitializeResult.Result result = new McpInitializeResult.Result(protocolVersion, capabilities, serverInfo);
        return new McpInitializeResult(request.getId(), result);
    }

    private McpListToolsResult handleListTools(McpListToolsRequest request) {
        McpListToolsResult.Result result = new McpListToolsResult.Result(mcpTools, null);
        return new McpListToolsResult(request.getId(), result);
    }

    private McpJsonRpcMessage handleCallTool(McpCallToolRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name") || params.get("name") == null) {
            return new McpErrorResponse(
                    request.getId(), new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Missing tool name", null));
        }

        String toolName = String.valueOf(params.get("name"));
        ToolExecutor toolExecutor = toolExecutors.get(toolName);
        if (toolExecutor == null) {
            return toCallToolError(request.getId(), "Unknown tool: " + toolName);
        }

        String arguments = null;
        Object args = params.get(ARGUMENTS_FIELD);
        if (args != null) {
            try {
                arguments = OBJECT_MAPPER.writeValueAsString(args);
            } catch (JsonProcessingException e) {
                return toCallToolError(request.getId(), "Failed to serialize tool arguments");
            }
        }

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id(String.valueOf(request.getId()))
                .name(toolName)
                .arguments(arguments)
                .build();

        try {
            ToolExecutionResult result = toolExecutor.executeWithContext(toolRequest, null);
            return toCallToolResult(request.getId(), result);
        } catch (Exception e) {
            return toCallToolError(request.getId(), safeMessage(e));
        }
    }

    private McpCallToolResult toCallToolResult(Long id, ToolExecutionResult result) {
        String text = result.resultText();
        McpCallToolResult.Content content = new McpCallToolResult.Content("text", text);
        Boolean isError = result.isError() ? Boolean.TRUE : null;
        McpCallToolResult.Result response = new McpCallToolResult.Result(List.of(content), null, isError);
        return new McpCallToolResult(id, response);
    }

    private McpCallToolResult toCallToolError(Long id, String message) {
        McpCallToolResult.Content content = new McpCallToolResult.Content("text", message);
        McpCallToolResult.Result response = new McpCallToolResult.Result(List.of(content), null, true);
        return new McpCallToolResult(id, response);
    }

    private void addExecutors(Object tool, Map<String, ToolExecutor> executors) {
        for (Method method : tool.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Tool.class)) {
                continue;
            }
            ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
            String toolName = specification.name();
            if (executors.containsKey(toolName)) {
                throw new IllegalArgumentException("Duplicated tool name: " + toolName);
            }
            ToolExecutor executor = DefaultToolExecutor.builder()
                    .object(tool)
                    .originalMethod(method)
                    .methodToInvoke(method)
                    .wrapToolArgumentsExceptions(true)
                    .propagateToolExecutionExceptions(true)
                    .build();
            executors.put(toolName, executor);
        }
    }

    private McpInitializeRequest parseInitializeRequest(Long id, JsonNode message) {
        McpInitializeRequest request = new McpInitializeRequest(id);
        JsonNode paramsNode = message.get(PARAMS_FIELD);
        if (paramsNode != null && paramsNode.isObject()) {
            McpInitializeParams params = OBJECT_MAPPER.convertValue(paramsNode, McpInitializeParams.class);
            request.setParams(params);
        }
        return request;
    }

    private McpListToolsRequest parseListToolsRequest(Long id, JsonNode message) {
        McpListToolsRequest request = new McpListToolsRequest(id);
        JsonNode paramsNode = message.get(PARAMS_FIELD);
        if (paramsNode != null && paramsNode.isObject() && paramsNode.has("cursor")) {
            request.setCursor(paramsNode.get("cursor").asText());
        }
        return request;
    }

    private McpCallToolRequest parseCallToolRequest(Long id, JsonNode message) {
        JsonNode paramsNode = message.get(PARAMS_FIELD);

        String toolName = null;
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();

        if (paramsNode != null && paramsNode.isObject()) {
            JsonNode nameNode = paramsNode.get("name");
            if (nameNode != null && !nameNode.isNull()) {
                toolName = nameNode.asText();
            }

            JsonNode argumentsNode = paramsNode.get(ARGUMENTS_FIELD);
            if (argumentsNode != null && !argumentsNode.isNull()) {
                if (argumentsNode.isObject()) {
                    arguments = (ObjectNode) argumentsNode;
                } else {
                    arguments = OBJECT_MAPPER.createObjectNode();
                }
            }
        }
        return new McpCallToolRequest(id, toolName, arguments);
    }

    private Long extractId(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        return idNode.isNumber() ? idNode.asLong() : null;
    }

    private String safeMessage(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return e.getClass().getName();
    }

    private static McpImplementation defaultServerInfo() {
        String version = null;
        Package pkg = McpServer.class.getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
        }
        if (version == null || version.isBlank()) {
            version = "dev";
        }
        McpImplementation info = new McpImplementation();
        info.setName(DEFAULT_SERVER_NAME);
        info.setVersion(version);
        return info;
    }
}
