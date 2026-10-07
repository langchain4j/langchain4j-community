package dev.langchain4j.community.mcp.server.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(45)
class McpServerProcessIT {

    @TempDir
    Path temp;

    @Test
    void initializesListsCallsAndExitsOnEofWithoutStdoutContamination() throws Exception {
        Path stderr = temp.resolve("stderr.log");
        Process process = launch(stderr);
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        try {
            awaitMain(process, stderr);
            assertThat(process.isAlive()).isTrue();
            writer.write("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                    """);
            writer.flush();
            JsonNode initialize = readResponse(reader, readerExecutor);
            assertThat(initialize.path("id").asInt()).isEqualTo(1);
            assertThat(initialize.at("/result/protocolVersion").asText()).isEqualTo("2025-06-18");
            assertThat(initialize.at("/result/capabilities/tools").isObject()).isTrue();

            writer.write("""
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                    """);
            writer.flush();
            JsonNode tools = readResponse(reader, readerExecutor);
            assertThat(tools.path("id").asInt()).isEqualTo(2);
            assertThat(tools.at("/result/tools")).hasSize(1);
            assertThat(tools.at("/result/tools/0/name").asText()).isEqualTo("add");
            assertThat(tools.at("/result/tools/0/inputSchema/properties").has("a"))
                    .isTrue();

            writer.write("""
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"add","arguments":{"a":2,"b":3}}}
                    """);
            writer.flush();
            JsonNode result = readResponse(reader, readerExecutor);
            assertThat(result.path("id").asInt()).isEqualTo(3);
            assertThat(result.at("/result/content/0/text").asText()).isEqualTo("5");
            assertThat(result.at("/result/isError").asBoolean()).isFalse();
            writer.close();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(reader.readLine()).isNull();
            assertThat(Files.readString(stderr)).contains("MAIN_RETURNED").doesNotContain("APPLICATION FAILED");
        } finally {
            terminate(process);
            reader.close();
            writer.close();
            readerExecutor.shutdownNow();
            assertThat(readerExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void langChain4jClientRoundtripAndGracefulEof() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var transport = StdioMcpTransport.builder()
                .command(command())
                .logEvents(false)
                .executorService(executor)
                .build();
        Process process = null;
        try (var client = DefaultMcpClient.builder()
                .transport(transport)
                .autoHealthCheck(false)
                .initializationTimeout(Duration.ofSeconds(20))
                .toolExecutionTimeout(Duration.ofSeconds(10))
                .build()) {
            process = transport.getProcess();
            assertThat(client.listTools()).extracting(ToolSpecification::name).containsExactly("add");
            var result = client.executeTool(ToolExecutionRequest.builder()
                    .name("add")
                    .arguments("{\"a\":4,\"b\":5}")
                    .build());
            assertThat(result.isError()).isFalse();
            assertThat(result.resultText()).isEqualTo("9");
            process.getOutputStream().close();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process == null) {
                process = transport.getProcess();
            }
            if (process != null) {
                terminate(process);
            }
            transport.close();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void applicationCloseExitsEvenWithStdinOpen() throws Exception {
        Path stderr = temp.resolve("context-close.log");
        Process process = launch(stderr, "--test.close-context=true");
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(process.getInputStream().readAllBytes()).isEmpty();
            assertThat(Files.readString(stderr)).contains("MAIN_RETURNED");
        } finally {
            terminate(process);
        }
    }

    @Test
    void immediateEofDuringStartupExitsCleanly() throws Exception {
        Process process = launch(temp.resolve("immediate-eof.log"));
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(process.getInputStream().readAllBytes()).isEmpty();
        } finally {
            terminate(process);
        }
    }

    private Process launch(Path stderr, String... extra) throws Exception {
        return new ProcessBuilder(command(extra)).redirectError(stderr.toFile()).start();
    }

    private List<String> command(String... extra) {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                StdioTestApplication.class.getName(),
                "--spring.main.web-application-type=none",
                "--spring.main.banner-mode=off",
                "--logging.config=classpath:logback-mcp.xml",
                "--langchain4j.community.mcp.server.enabled=true",
                "--langchain4j.community.mcp.server.tool-bean-names=calculator"));
        command.addAll(List.of(extra));
        return command;
    }

    private void awaitMain(Process process, Path stderr) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(process.isAlive()).isTrue();
            assertThat(Files.readString(stderr)).contains("MAIN_RETURNED");
        });
    }

    private JsonNode readResponse(BufferedReader reader, ExecutorService executor) throws Exception {
        String line = executor.submit(reader::readLine).get(10, TimeUnit.SECONDS);
        assertThat(line).isNotNull();
        JsonNode response = new ObjectMapper().readTree(line);
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("error")).isFalse();
        return response;
    }

    private void terminate(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
    }
}
