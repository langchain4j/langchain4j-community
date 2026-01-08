package dev.langchain4j.community.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.mcp.server.support.InMemoryMcpTransport;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerComplexDataIT {

    private static final int PIPE_BUFFER_SIZE = 10_240;

    @Test
    void should_handle_complex_tool_arguments() throws Exception {
        try (PipedInputStream serverInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream clientOutputStream = new PipedOutputStream(serverInputStream);
                PipedInputStream clientInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
                PipedOutputStream serverOutputStream = new PipedOutputStream(clientInputStream);
                StdioMcpServerTransport ignored = new StdioMcpServerTransport(
                        serverInputStream, serverOutputStream, new McpServer(List.of(new ComplexTool())));
                McpClient client = DefaultMcpClient.builder()
                        .transport(new InMemoryMcpTransport(clientInputStream, clientOutputStream))
                        .autoHealthCheck(false)
                        .build()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).extracting(ToolSpecification::name).contains("processOrder");

            ToolSpecification processOrderTool = tools.stream()
                    .filter(tool -> "processOrder".equals(tool.name()))
                    .findFirst()
                    .orElseThrow();

            Map<String, Object> argumentsMap = toolArgumentsFor(
                    processOrderTool, "ORD-1", List.of("apple", "banana"), Map.of("name", "John", "age", 30));

            String arguments = new ObjectMapper().writeValueAsString(argumentsMap);

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("processOrder")
                    .arguments(arguments)
                    .build();

            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Processed ORD-1 for John with 2 items");
        }
    }

    private static Map<String, Object> toolArgumentsFor(
            ToolSpecification tool, String orderId, List<String> items, Map<String, Object> customer) {
        if (tool.parameters() == null || tool.parameters().properties() == null) {
            return Map.of("orderId", orderId, "items", items, "customer", customer);
        }
        List<String> keys = tool.parameters().properties().keySet().stream().toList();
        if (keys.contains("orderId") && keys.contains("items") && keys.contains("customer")) {
            return Map.of("orderId", orderId, "items", items, "customer", customer);
        }
        if (keys.size() >= 3) {
            return Map.of(keys.get(0), orderId, keys.get(1), items, keys.get(2), customer);
        }
        return Map.of("orderId", orderId, "items", items, "customer", customer);
    }

    static class ComplexTool {
        @Tool
        public String processOrder(String orderId, List<String> items, Customer customer) {
            return "Processed " + orderId + " for " + customer.name + " with " + items.size() + " items";
        }
    }

    record Customer(String name, int age) {}
}
