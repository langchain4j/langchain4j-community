package dev.langchain4j.community.mcp.server;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.protocol.McpCallToolParams;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model Context Protocol (MCP) server that exposes existing {@code @Tool}-annotated methods over
 * JSON-RPC, together with read-only data ({@code @Resource}) and reusable prompt templates
 * ({@code @Prompt}).
 *
 * <p>This server supports:
 *
 * <ul>
 *   <li>{@code initialize}
 *   <li>{@code tools/list}, {@code tools/call}
 *   <li>{@code resources/list}, {@code resources/read}
 *   <li>{@code prompts/list}, {@code prompts/get}
 * </ul>
 *
 * <p>Capabilities are negotiated in {@code initialize}: {@code resources} and {@code prompts} are
 * only advertised when at least one resource or prompt is registered.
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
    private static final String URI_FIELD = "uri";
    private static final String NAME_FIELD = "name";

    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Map<String, Object>> mcpTools;
    private final Map<String, ResourceSupplier> resourceSuppliers;
    private final List<Map<String, Object>> mcpResources;
    private final Map<String, PromptExecutor> promptExecutors;
    private final List<Map<String, Object>> mcpPrompts;
    private final McpImplementation serverInfo;

    public McpServer(List<Object> tools) {
        this(tools, List.of(), List.of(), defaultServerInfo());
    }

    public McpServer(List<Object> tools, McpImplementation serverInfo) {
        this(tools, List.of(), List.of(), serverInfo);
    }

    private McpServer(List<Object> tools, List<Object> resources, List<Object> prompts, McpImplementation serverInfo) {
        ensureNotNull(tools, "tools");
        ensureNotNull(resources, "resources");
        ensureNotNull(prompts, "prompts");
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
        this.mcpTools = List.copyOf(McpToolSchemaMapper.toMcpTools(toolSpecifications));

        Map<String, ResourceSupplier> resourceSuppliers = new LinkedHashMap<>();
        List<Map<String, Object>> resourceMetas = new ArrayList<>();
        for (Object resource : resources) {
            ensureNotNull(resource, "resource");
            addResources(resource, resourceSuppliers, resourceMetas);
        }
        this.resourceSuppliers = Map.copyOf(resourceSuppliers);
        this.mcpResources = List.copyOf(resourceMetas);

        Map<String, PromptExecutor> promptExecutors = new LinkedHashMap<>();
        List<Map<String, Object>> promptMetas = new ArrayList<>();
        for (Object prompt : prompts) {
            ensureNotNull(prompt, "prompt");
            addPrompts(prompt, promptExecutors, promptMetas);
        }
        this.promptExecutors = Map.copyOf(promptExecutors);
        this.mcpPrompts = List.copyOf(promptMetas);
    }

    public static Builder builder() {
        return new Builder();
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
                case "resources/list" -> handleListResources(id);
                case "resources/read" -> handleReadResource(id, message);
                case "prompts/list" -> handleListPrompts(id);
                case "prompts/get" -> handleGetPrompt(id, message);
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
        if (request.getParams() != null
                && (request.getParams() instanceof McpInitializeParams mcpInitializeParams)
                && mcpInitializeParams.getProtocolVersion() != null) {
            protocolVersion = mcpInitializeParams.getProtocolVersion();
        }
        McpInitializeResult.Capabilities capabilities =
                new McpInitializeResult.Capabilities(new McpInitializeResult.Capabilities.Tools(null));
        if (!mcpResources.isEmpty() || !mcpPrompts.isEmpty()) {
            // Negotiate what is actually implemented: the shared Capabilities type only knows
            // "tools", so the extra entries are carried by this subclass and omitted when unused.
            capabilities = new CapabilitiesWithExtras(
                    capabilities,
                    mcpResources.isEmpty() ? null : Map.of(),
                    mcpPrompts.isEmpty() ? null : Map.of());
        }
        McpInitializeResult.Result result = new McpInitializeResult.Result(protocolVersion, capabilities, serverInfo);
        return new McpInitializeResult(request.getId(), result);
    }

    private McpListToolsResult handleListTools(McpListToolsRequest request) {
        McpListToolsResult.Result result = new McpListToolsResult.Result(mcpTools, null);
        return new McpListToolsResult(request.getId(), result);
    }

    private McpJsonRpcMessage handleCallTool(McpCallToolRequest request) {
        McpCallToolParams params = (McpCallToolParams) request.getParams();
        if (params == null || params.getName() == null) {
            return new McpErrorResponse(
                    request.getId(), new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Missing tool name", null));
        }

        ToolExecutor toolExecutor = toolExecutors.get(params.getName());
        if (toolExecutor == null) {
            return toCallToolError(request.getId(), "Unknown tool: " + params.getName());
        }

        String arguments = null;
        Map<String, Object> args = params.getArguments();
        if (args != null) {
            try {
                arguments = OBJECT_MAPPER.writeValueAsString(args);
            } catch (JsonProcessingException e) {
                return toCallToolError(request.getId(), "Failed to serialize tool arguments");
            }
        }

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id(String.valueOf(request.getId()))
                .name(params.getName())
                .arguments(arguments)
                .build();

        try {
            ToolExecutionResult result = toolExecutor.executeWithContext(toolRequest, null);
            return toCallToolResult(request.getId(), result);
        } catch (Exception e) {
            return toCallToolError(request.getId(), safeMessage(e));
        }
    }

    private McpListResourcesResult handleListResources(Long id) {
        return new McpListResourcesResult(id, mcpResources);
    }

    private McpJsonRpcMessage handleReadResource(Long id, JsonNode message) {
        String uri = extractTextParam(message, URI_FIELD);
        if (uri == null || uri.isBlank()) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Missing resource uri", null));
        }

        ResourceSupplier supplier = resourceSuppliers.get(uri);
        if (supplier == null) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Unknown resource: " + uri, null));
        }

        try {
            String text = supplier.read();
            Map<String, Object> content = new LinkedHashMap<>();
            content.put(URI_FIELD, uri);
            content.put("mimeType", supplier.mimeType());
            content.put("text", text);
            return new McpReadResourceResult(id, List.of(content));
        } catch (Exception e) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, safeMessage(e), null));
        }
    }

    private McpListPromptsResult handleListPrompts(Long id) {
        return new McpListPromptsResult(id, mcpPrompts);
    }

    private McpJsonRpcMessage handleGetPrompt(Long id, JsonNode message) {
        String name = extractTextParam(message, NAME_FIELD);
        if (name == null || name.isBlank()) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Missing prompt name", null));
        }

        PromptExecutor executor = promptExecutors.get(name);
        if (executor == null) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, "Unknown prompt: " + name, null));
        }

        Map<String, Object> args = extractArguments(message);

        Object[] values = new Object[executor.arguments().size()];
        for (PromptArgumentSpec spec : executor.arguments()) {
            Object value = args.get(spec.name());
            if (value == null && spec.required()) {
                return new McpErrorResponse(
                        id,
                        new McpErrorResponse.Error(
                                ERROR_CODE_INVALID_PARAMS, "Missing required prompt argument: " + spec.name(), null));
            }
            values[spec.index()] = value == null ? null : OBJECT_MAPPER.convertValue(value, spec.type());
        }

        try {
            Object out = executor.invoke(values);
            if (!(out instanceof List<?> list)) {
                throw new IllegalStateException("Prompt method must return List<PromptMessage>");
            }
            List<PromptMessage> messages = new ArrayList<>();
            for (Object element : list) {
                if (!(element instanceof PromptMessage message1)) {
                    throw new IllegalStateException("Prompt method must return List<PromptMessage>");
                }
                messages.add(message1);
            }
            return new McpGetPromptResult(id, executor.description(), messages);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new McpErrorResponse(
                    id,
                    new McpErrorResponse.Error(
                            ERROR_CODE_INVALID_PARAMS,
                            cause.getMessage() != null && !cause.getMessage().isBlank()
                                    ? cause.getMessage()
                                    : cause.getClass().getName(),
                            null));
        } catch (Exception e) {
            return new McpErrorResponse(
                    id, new McpErrorResponse.Error(ERROR_CODE_INVALID_PARAMS, safeMessage(e), null));
        }
    }

    private McpCallToolResult toCallToolResult(Long id, ToolExecutionResult result) {
        String text = result.resultText();
        Map<String, Object> content = Map.of("type", "text", "text", text);
        Boolean isError = result.isError() ? Boolean.TRUE : null;
        McpCallToolResult.Result response = new McpCallToolResult.Result(List.of(content), null, isError);
        return new McpCallToolResult(id, response);
    }

    private McpCallToolResult toCallToolError(Long id, String message) {
        Map<String, Object> content = Map.of("type", "text", "text", message);
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

    private void addResources(
            Object resource,
            Map<String, ResourceSupplier> suppliers,
            List<Map<String, Object>> metas) {
        for (Method method : resource.getClass().getDeclaredMethods()) {
            Resource annotation = method.getAnnotation(Resource.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                throw new IllegalArgumentException(
                        "Resource method must not take parameters: " + annotation.uri());
            }
            if (suppliers.containsKey(annotation.uri())) {
                throw new IllegalArgumentException("Duplicated resource uri: " + annotation.uri());
            }
            suppliers.put(annotation.uri(), new ResourceSupplier(annotation.mimeType()) {

                @Override
                public String read() throws Exception {
                    return invokeResource(resource, method);
                }
            });
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(URI_FIELD, annotation.uri());
            meta.put(NAME_FIELD, annotation.name());
            meta.put("description", annotation.description());
            meta.put("mimeType", annotation.mimeType());
            metas.add(meta);
        }
    }

    private void addPrompts(
            Object prompt,
            Map<String, PromptExecutor> executors,
            List<Map<String, Object>> metas) {
        for (Method method : prompt.getClass().getDeclaredMethods()) {
            Prompt annotation = method.getAnnotation(Prompt.class);
            if (annotation == null) {
                continue;
            }
            if (!List.class.isAssignableFrom(method.getReturnType())) {
                throw new IllegalArgumentException(
                        "Prompt method must return List<PromptMessage>: " + annotation.name());
            }

            List<PromptArgumentSpec> arguments = new ArrayList<>();
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                PromptArg arg = parameters[i].getAnnotation(PromptArg.class);
                if (arg == null) {
                    throw new IllegalArgumentException(
                            "Prompt method parameter is missing @PromptArg: " + annotation.name());
                }
                arguments.add(new PromptArgumentSpec(
                        arg.name(), arg.description(), arg.required(), parameters[i].getType(), i));
            }

            if (executors.containsKey(annotation.name())) {
                throw new IllegalArgumentException("Duplicated prompt name: " + annotation.name());
            }
            executors.put(annotation.name(), new PromptExecutor(annotation.description(), arguments, prompt, method));

            List<Map<String, Object>> argumentMetas = new ArrayList<>();
            for (PromptArgumentSpec argument : arguments) {
                Map<String, Object> argumentMeta = new LinkedHashMap<>();
                argumentMeta.put(NAME_FIELD, argument.name());
                argumentMeta.put("description", argument.description());
                argumentMeta.put("required", argument.required());
                argumentMetas.add(argumentMeta);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(NAME_FIELD, annotation.name());
            meta.put("description", annotation.description());
            meta.put("arguments", argumentMetas);
            metas.add(meta);
        }
    }

    private String invokeResource(Object resource, Method method) throws Exception {
        try {
            Object out = method.invoke(resource);
            if (out instanceof String text) {
                return text;
            }
            return OBJECT_MAPPER.writeValueAsString(out);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
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
        String cursor = null;
        JsonNode paramsNode = message.get(PARAMS_FIELD);
        if (paramsNode != null && paramsNode.isObject() && paramsNode.has("cursor")) {
            cursor = paramsNode.get("cursor").asText();
        }
        return new McpListToolsRequest(id, cursor);
    }

    private McpCallToolRequest parseCallToolRequest(Long id, JsonNode message) {
        JsonNode paramsNode = message.get(PARAMS_FIELD);

        String toolName = null;
        Map<String, Object> arguments = Map.of();

        if (paramsNode != null && paramsNode.isObject()) {
            JsonNode nameNode = paramsNode.get(NAME_FIELD);
            if (nameNode != null && !nameNode.isNull()) {
                toolName = nameNode.asText();
            }

            JsonNode argumentsNode = paramsNode.get(ARGUMENTS_FIELD);
            if (argumentsNode != null && !argumentsNode.isNull() && argumentsNode.isObject()) {
                arguments = OBJECT_MAPPER.convertValue(argumentsNode, new TypeReference<Map<String, Object>>() {});
            }
        }
        return new McpCallToolRequest(id, toolName, arguments);
    }

    private String extractTextParam(JsonNode message, String field) {
        JsonNode paramsNode = message.get(PARAMS_FIELD);
        if (paramsNode == null || !paramsNode.isObject()) {
            return null;
        }
        JsonNode valueNode = paramsNode.get(field);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }

    private Map<String, Object> extractArguments(JsonNode message) {
        JsonNode paramsNode = message.get(PARAMS_FIELD);
        if (paramsNode == null || !paramsNode.isObject()) {
            return Map.of();
        }
        JsonNode argumentsNode = paramsNode.get(ARGUMENTS_FIELD);
        if (argumentsNode == null || !argumentsNode.isObject()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(argumentsNode, new TypeReference<Map<String, Object>>() {});
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

    /** Reads the content of one {@link Resource @Resource} method. */
    private abstract static class ResourceSupplier {

        private final String mimeType;

        private ResourceSupplier(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }

        public abstract String read() throws Exception;
    }

    private record PromptExecutor(
            String description, List<PromptArgumentSpec> arguments, Object instance, Method method) {

        public Object invoke(Object[] values) throws Exception {
            try {
                return method.invoke(instance, values);
            } catch (InvocationTargetException e) {
                throw e;
            }
        }
    }

    private record PromptArgumentSpec(String name, String description, boolean required, Class<?> type, int index) {}

    /**
     * Carries the {@code resources} / {@code prompts} capability entries that the shared
     * {@link McpInitializeResult.Capabilities} type cannot represent. Entries are omitted while no
     * resource or prompt is registered.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CapabilitiesWithExtras extends McpInitializeResult.Capabilities {

        private final Object resources;
        private final Object prompts;

        private CapabilitiesWithExtras(McpInitializeResult.Capabilities delegate, Object resources, Object prompts) {
            super(delegate.getTools());
            this.resources = resources;
            this.prompts = prompts;
        }

        public Object getResources() {
            return resources;
        }

        public Object getPrompts() {
            return prompts;
        }
    }

    /**
     * Builder for {@link McpServer}.
     *
     * <p>Example:
     *
     * <pre>{@code
     * McpServer server = McpServer.builder()
     *         .tools(List.of(bookmarkTools, systemTools))
     *         .resources(List.of(configResource, docsResource))
     *         .prompts(List.of(promptTemplates))
     *         .serverInfo(serverInfo)
     *         .build();
     * }</pre>
     */
    public static class Builder {

        private List<Object> tools = List.of();
        private List<Object> resources = List.of();
        private List<Object> prompts = List.of();
        private McpImplementation serverInfo;

        public Builder tools(List<Object> tools) {
            this.tools = ensureNotNull(tools, "tools");
            return this;
        }

        public Builder resources(List<Object> resources) {
            this.resources = ensureNotNull(resources, "resources");
            return this;
        }

        public Builder prompts(List<Object> prompts) {
            this.prompts = ensureNotNull(prompts, "prompts");
            return this;
        }

        public Builder serverInfo(McpImplementation serverInfo) {
            this.serverInfo = ensureNotNull(serverInfo, "serverInfo");
            return this;
        }

        public McpServer build() {
            return new McpServer(
                    tools, resources, prompts, serverInfo != null ? serverInfo : defaultServerInfo());
        }
    }
}
