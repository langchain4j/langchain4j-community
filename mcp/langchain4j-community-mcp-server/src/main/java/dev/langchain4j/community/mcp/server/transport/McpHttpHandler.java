package dev.langchain4j.community.mcp.server.transport;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Server-agnostic handler for the
 * <a href="https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#streamable-http">Streamable HTTP</a>
 * transport, in its stateless form: every {@code POST} carries one JSON-RPC message and, if that message is a
 * request, receives a single {@code application/json} response. No session is created and no SSE stream is opened,
 * so {@code GET} and {@code DELETE} are answered with {@code 405 Method Not Allowed}, as the specification allows.
 *
 * <p>This class does not depend on any HTTP server. Mount it on whatever server the application already runs,
 * for example from a servlet or a Spring MVC controller:
 *
 * <pre>{@code
 * McpHttpHandler handler = new McpHttpHandler(new McpServer(List.of(new MyTools())));
 *
 * @PostMapping("/mcp")
 * ResponseEntity<String> mcp(HttpServletRequest request, @RequestBody String body) {
 *     McpHttpHandler.Response response = handler.handle(request.getMethod(), request::getHeader, body);
 *     return ResponseEntity.status(response.statusCode()).headers(h -> response.headers().forEach(h::set))
 *             .body(response.body());
 * }
 * }</pre>
 *
 * or use {@link JdkHttpMcpServerTransport} to serve it with the HTTP server built into the JDK.
 */
public class McpHttpHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String APPLICATION_JSON = "application/json";
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;

    private final McpServer server;
    private final Set<String> allowedOrigins;

    /**
     * Creates a handler that rejects every request carrying an {@code Origin} header, i.e. every request sent by
     * a browser. Non-browser MCP clients do not send this header.
     */
    public McpHttpHandler(McpServer server) {
        this(server, Set.of());
    }

    /**
     * @param allowedOrigins origins (e.g. {@code https://example.com}) whose browser requests are accepted. The
     *                       specification requires servers to validate {@code Origin} to prevent DNS rebinding
     *                       attacks, so requests from any other origin are answered with {@code 403 Forbidden}.
     */
    public McpHttpHandler(McpServer server, Collection<String> allowedOrigins) {
        this.server = ensureNotNull(server, "server");
        this.allowedOrigins = Set.copyOf(ensureNotNull(allowedOrigins, "allowedOrigins"));
    }

    /**
     * Handles one HTTP request sent to the MCP endpoint.
     *
     * @param httpMethod the HTTP method, e.g. {@code POST}
     * @param headers    looks up a request header by name, case-insensitively; returns {@code null} when absent
     * @param body       the request body, may be {@code null}
     * @return the response to send back
     */
    public Response handle(String httpMethod, Function<String, String> headers, String body) {
        String origin = headers.apply("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            return Response.empty(403);
        }
        if (!"POST".equalsIgnoreCase(httpMethod)) {
            return new Response(405, Map.of("Allow", "POST"), null);
        }
        if (!isJson(headers.apply("Content-Type"))) {
            return Response.empty(415);
        }
        if (!acceptsJson(headers.apply("Accept"))) {
            return Response.empty(406);
        }

        JsonNode message;
        try {
            message = OBJECT_MAPPER.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            return error(400, null, PARSE_ERROR, "Parse error");
        }
        if (message == null || !message.isObject()) {
            return error(400, null, INVALID_REQUEST, "Invalid Request: expected a single JSON-RPC message");
        }

        JsonNode id = message.get("id");
        if (!message.has("method") || id == null || id.isNull()) {
            // notifications and responses need no reply; McpServer does not act on them either
            return Response.empty(202);
        }
        if (!id.isNumber() && !id.isTextual()) {
            return error(400, null, INVALID_REQUEST, "Invalid Request: id must be a string or a number");
        }

        // McpServer works with numeric ids only. Each HTTP request is answered on its own response, so the
        // original id can be swapped for a placeholder and restored without any risk of mixing up requests.
        ObjectNode request = ((ObjectNode) message).deepCopy();
        request.put("id", 0L);
        McpJsonRpcMessage reply = server.handle(request);
        if (reply == null) {
            return Response.empty(202);
        }
        ObjectNode json = OBJECT_MAPPER.valueToTree(reply);
        json.set("id", id);
        return json(200, json);
    }

    private static boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(APPLICATION_JSON);
    }

    private static boolean acceptsJson(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        for (String range : accept.split(",")) {
            String mediaType = range.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (mediaType.equals(APPLICATION_JSON) || mediaType.equals("application/*") || mediaType.equals("*/*")) {
                return true;
            }
        }
        return false;
    }

    private static Response error(int status, JsonNode id, int code, String message) {
        ObjectNode json = OBJECT_MAPPER.createObjectNode().put("jsonrpc", "2.0");
        json.set("id", id);
        json.putObject("error").put("code", code).put("message", message);
        return json(status, json);
    }

    private static Response json(int status, JsonNode json) {
        try {
            return new Response(
                    status, Map.of("Content-Type", APPLICATION_JSON), OBJECT_MAPPER.writeValueAsString(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * An HTTP response produced by {@link McpHttpHandler}.
     *
     * @param statusCode the HTTP status code
     * @param headers    response headers to set
     * @param body       the response body, or {@code null} when the response has no body
     */
    public record Response(int statusCode, Map<String, String> headers, String body) {

        static Response empty(int statusCode) {
            return new Response(statusCode, Map.of(), null);
        }
    }
}
