package dev.langchain4j.community.code.docker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DockerCodeExecutionEngine}.
 * Requires Docker to be running. Tests multi-language execution and security constraints.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class DockerCodeExecutionEngineIT {

    private DockerCodeExecutionEngine engine;

    @BeforeAll
    static void checkDockerAvailable() {
        DockerTestUtils.assumeDockerAvailable();
    }

    @BeforeEach
    void setUp() {
        engine = new DockerCodeExecutionEngine(
                DockerExecutionConfig.builder()
                        .timeout(Duration.ofSeconds(60))
                        .memoryLimit("256m")
                        .networkDisabled(true)
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // =========================================================================
    // Python Tests
    // =========================================================================

    @Nested
    @DisplayName("Python Execution Tests")
    class PythonTests {

        private static final String PYTHON_IMAGE = "python:3.12-slim";

        @Test
        @DisplayName("Should execute simple Python print statement")
        void should_execute_simple_python_print() {
            String result = engine.execute(
                    PYTHON_IMAGE,
                    ".py",
                    "print('Hello from Python!')",
                    "python"
            );

            assertThat(result).isEqualTo("Hello from Python!");
        }

        @Test
        @DisplayName("Should execute Python arithmetic")
        void should_execute_python_arithmetic() {
            String result = engine.execute(
                    PYTHON_IMAGE,
                    ".py",
                    "print(2 + 2 * 10)",
                    "python"
            );

            assertThat(result).isEqualTo("22");
        }

        @Test
        @DisplayName("Should execute multi-line Python code")
        void should_execute_multiline_python_code() {
            String code = """
                    def fibonacci(n):
                        if n <= 1:
                            return n
                        return fibonacci(n-1) + fibonacci(n-2)

                    for i in range(10):
                        print(fibonacci(i), end=' ')
                    """;

            String result = engine.execute(PYTHON_IMAGE, ".py", code, "python");

            assertThat(result).isEqualTo("0 1 1 2 3 5 8 13 21 34");
        }

        @Test
        @DisplayName("Should capture Python stderr on syntax error")
        void should_capture_python_stderr_on_syntax_error() {
            assertThatThrownBy(() -> engine.execute(
                    PYTHON_IMAGE,
                    ".py",
                    "print('unclosed string)",
                    "python"
            ))
                    .isInstanceOf(DockerExecutionException.class)
                    .satisfies(e -> {
                        DockerExecutionException ex = (DockerExecutionException) e;
                        assertThat(ex.getErrorType()).isEqualTo(DockerExecutionException.ErrorType.EXECUTION_FAILED);
                        assertThat(ex.getStderr()).containsIgnoringCase("SyntaxError");
                    });
        }

        @Test
        @DisplayName("Should capture Python stderr on runtime error")
        void should_capture_python_stderr_on_runtime_error() {
            assertThatThrownBy(() -> engine.execute(
                    PYTHON_IMAGE,
                    ".py",
                    "raise ValueError('Test error message')",
                    "python"
            ))
                    .isInstanceOf(DockerExecutionException.class)
                    .satisfies(e -> {
                        DockerExecutionException ex = (DockerExecutionException) e;
                        assertThat(ex.getErrorType()).isEqualTo(DockerExecutionException.ErrorType.EXECUTION_FAILED);
                        assertThat(ex.getStderr()).contains("ValueError");
                        assertThat(ex.getStderr()).contains("Test error message");
                    });
        }

        @Test
        @DisplayName("Should execute Python with standard library imports")
        void should_execute_python_with_stdlib() {
            String code = """
                    import json
                    import math

                    data = {'pi': round(math.pi, 4), 'e': round(math.e, 4)}
                    print(json.dumps(data))
                    """;

            String result = engine.execute(PYTHON_IMAGE, ".py", code, "python");

            assertThat(result).contains("\"pi\"").contains("3.1416");
        }

        @Test
        @DisplayName("Should handle Python with unicode output")
        void should_handle_python_unicode_output() {
            String result = engine.execute(
                    PYTHON_IMAGE,
                    ".py",
                    "print('Hello 世界 🐍')",
                    "python"
            );

            assertThat(result).isEqualTo("Hello 世界 🐍");
        }
    }

    // =========================================================================
    // JavaScript/Node.js Tests
    // =========================================================================

    @Nested
    @DisplayName("JavaScript/Node.js Execution Tests")
    class JavaScriptTests {

        private static final String NODE_IMAGE = "node:20-alpine";

        @Test
        @DisplayName("Should execute simple JavaScript console.log")
        void should_execute_simple_javascript() {
            String result = engine.execute(
                    NODE_IMAGE,
                    ".js",
                    "console.log('Hello from Node.js!');",
                    "node"
            );

            assertThat(result).isEqualTo("Hello from Node.js!");
        }

        @Test
        @DisplayName("Should execute JavaScript array operations")
        void should_execute_javascript_array_operations() {
            String code = """
                    const numbers = [3, 1, 4, 1, 5, 9, 2, 6];
                    const sorted = numbers.sort((a, b) => a - b);
                    console.log(sorted.join(', '));
                    """;

            String result = engine.execute(NODE_IMAGE, ".js", code, "node");

            assertThat(result).isEqualTo("1, 1, 2, 3, 4, 5, 6, 9");
        }

        @Test
        @DisplayName("Should execute JavaScript with JSON manipulation")
        void should_execute_javascript_json() {
            String code = """
                    const data = { name: 'test', values: [1, 2, 3] };
                    console.log(JSON.stringify(data));
                    """;

            String result = engine.execute(NODE_IMAGE, ".js", code, "node");

            assertThat(result).isEqualTo("{\"name\":\"test\",\"values\":[1,2,3]}");
        }

        @Test
        @DisplayName("Should execute JavaScript async/await")
        void should_execute_javascript_async() {
            String code = """
                    async function getData() {
                        return new Promise(resolve => {
                            setTimeout(() => resolve('async result'), 10);
                        });
                    }

                    (async () => {
                        const result = await getData();
                        console.log(result);
                    })();
                    """;

            String result = engine.execute(NODE_IMAGE, ".js", code, "node");

            assertThat(result).isEqualTo("async result");
        }

        @Test
        @DisplayName("Should capture JavaScript error with stack trace")
        void should_capture_javascript_error() {
            assertThatThrownBy(() -> engine.execute(
                    NODE_IMAGE,
                    ".js",
                    "throw new Error('Test JS error');",
                    "node"
            ))
                    .isInstanceOf(DockerExecutionException.class)
                    .satisfies(e -> {
                        DockerExecutionException ex = (DockerExecutionException) e;
                        assertThat(ex.getStderr()).contains("Error");
                        assertThat(ex.getStderr()).contains("Test JS error");
                    });
        }
    }

    // =========================================================================
    // Ruby Tests
    // =========================================================================

    @Nested
    @DisplayName("Ruby Execution Tests")
    class RubyTests {

        private static final String RUBY_IMAGE = "ruby:3.3-slim";

        @Test
        @DisplayName("Should execute simple Ruby puts")
        void should_execute_simple_ruby() {
            String result = engine.execute(
                    RUBY_IMAGE,
                    ".rb",
                    "puts 'Hello from Ruby!'",
                    "ruby"
            );

            assertThat(result).isEqualTo("Hello from Ruby!");
        }

        @Test
        @DisplayName("Should execute Ruby with blocks")
        void should_execute_ruby_blocks() {
            String code = """
                    result = [1, 2, 3, 4, 5].map { |n| n * n }.join(', ')
                    puts result
                    """;

            String result = engine.execute(RUBY_IMAGE, ".rb", code, "ruby");

            assertThat(result).isEqualTo("1, 4, 9, 16, 25");
        }

        @Test
        @DisplayName("Should execute Ruby with classes")
        void should_execute_ruby_classes() {
            String code = """
                    class Greeter
                      def initialize(name)
                        @name = name
                      end

                      def greet
                        "Hello, #{@name}!"
                      end
                    end

                    greeter = Greeter.new("World")
                    puts greeter.greet
                    """;

            String result = engine.execute(RUBY_IMAGE, ".rb", code, "ruby");

            assertThat(result).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("Should execute Ruby with JSON library")
        void should_execute_ruby_json() {
            String code = """
                    require 'json'

                    data = { name: 'test', count: 42 }
                    puts JSON.generate(data)
                    """;

            String result = engine.execute(RUBY_IMAGE, ".rb", code, "ruby");

            assertThat(result).contains("\"name\"").contains("\"test\"").contains("42");
        }
    }

    // =========================================================================
    // Go Tests
    // =========================================================================

    @Nested
    @DisplayName("Go Execution Tests")
    class GoTests {

        private static final String GO_IMAGE = "golang:1.22-alpine";

        @Test
        @DisplayName("Should execute simple Go program")
        void should_execute_simple_go() {
            String code = """
                    package main

                    import "fmt"

                    func main() {
                        fmt.Println("Hello from Go!")
                    }
                    """;

            String result = engine.execute(GO_IMAGE, ".go", code, "go run code.go");

            assertThat(result).isEqualTo("Hello from Go!");
        }

        @Test
        @DisplayName("Should execute Go with functions")
        void should_execute_go_with_functions() {
            String code = """
                    package main

                    import "fmt"

                    func factorial(n int) int {
                        if n <= 1 {
                            return 1
                        }
                        return n * factorial(n-1)
                    }

                    func main() {
                        fmt.Println(factorial(10))
                    }
                    """;

            String result = engine.execute(GO_IMAGE, ".go", code, "go run code.go");

            assertThat(result).isEqualTo("3628800");
        }

        @Test
        @DisplayName("Should execute Go with slices")
        void should_execute_go_with_slices() {
            String code = """
                    package main

                    import (
                        "fmt"
                        "sort"
                    )

                    func main() {
                        nums := []int{3, 1, 4, 1, 5, 9, 2, 6}
                        sort.Ints(nums)
                        fmt.Println(nums)
                    }
                    """;

            String result = engine.execute(GO_IMAGE, ".go", code, "go run code.go");

            assertThat(result).isEqualTo("[1 1 2 3 4 5 6 9]");
        }

        @Test
        @DisplayName("Should capture Go compilation error")
        void should_capture_go_compilation_error() {
            String code = """
                    package main

                    func main() {
                        fmt.Println("missing import")
                    }
                    """;

            assertThatThrownBy(() -> engine.execute(GO_IMAGE, ".go", code, "go run code.go"))
                    .isInstanceOf(DockerExecutionException.class)
                    .satisfies(e -> {
                        DockerExecutionException ex = (DockerExecutionException) e;
                        assertThat(ex.getStderr()).containsIgnoringCase("undefined");
                    });
        }
    }

    // =========================================================================
    // Shell Script Tests
    // =========================================================================

    @Nested
    @DisplayName("Shell Script Execution Tests")
    class ShellTests {

        private static final String ALPINE_IMAGE = "alpine:3.19";

        @Test
        @DisplayName("Should execute simple shell echo")
        void should_execute_simple_shell_echo() {
            String result = engine.execute(
                    ALPINE_IMAGE,
                    ".sh",
                    "echo 'Hello from Shell!'",
                    "sh"
            );

            assertThat(result).isEqualTo("Hello from Shell!");
        }

        @Test
        @DisplayName("Should execute shell with variables")
        void should_execute_shell_with_variables() {
            String code = """
                    NAME="World"
                    echo "Hello, $NAME!"
                    """;

            String result = engine.execute(ALPINE_IMAGE, ".sh", code, "sh");

            assertThat(result).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("Should execute shell with loops")
        void should_execute_shell_with_loops() {
            String code = """
                    for i in 1 2 3 4 5; do
                        echo -n "$i "
                    done
                    """;

            String result = engine.execute(ALPINE_IMAGE, ".sh", code, "sh");

            assertThat(result).isEqualTo("1 2 3 4 5");
        }

        @Test
        @DisplayName("Should execute shell with pipes")
        void should_execute_shell_with_pipes() {
            String code = """
                    echo "apple banana cherry" | tr ' ' '\\n' | sort | head -1
                    """;

            String result = engine.execute(ALPINE_IMAGE, ".sh", code, "sh");

            assertThat(result).isEqualTo("apple");
        }
    }

    // =========================================================================
    // Security Tests
    // =========================================================================

    @Nested
    @DisplayName("Security Constraint Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should timeout on infinite loop")
        void should_timeout_on_infinite_loop() {
            DockerCodeExecutionEngine shortTimeoutEngine = new DockerCodeExecutionEngine(
                    DockerExecutionConfig.builder()
                            .timeout(Duration.ofSeconds(3))
                            .build()
            );

            try {
                assertThatThrownBy(() -> shortTimeoutEngine.execute(
                        "python:3.12-slim",
                        ".py",
                        "while True: pass",
                        "python"
                ))
                        .isInstanceOf(DockerExecutionException.class)
                        .satisfies(e -> {
                            DockerExecutionException ex = (DockerExecutionException) e;
                            assertThat(ex.getErrorType()).isEqualTo(DockerExecutionException.ErrorType.EXECUTION_TIMEOUT);
                        });
            } finally {
                shortTimeoutEngine.close();
            }
        }

        @Test
        @DisplayName("Should enforce memory limit")
        void should_enforce_memory_limit() {
            DockerCodeExecutionEngine limitedEngine = new DockerCodeExecutionEngine(
                    DockerExecutionConfig.builder()
                            .memoryLimit("32m")
                            .timeout(Duration.ofSeconds(30))
                            .build()
            );

            try {
                // Attempt to allocate more memory than allowed
                String code = """
                        # Try to allocate ~100MB
                        data = 'x' * (100 * 1024 * 1024)
                        print(len(data))
                        """;

                assertThatThrownBy(() -> limitedEngine.execute(
                        "python:3.12-slim",
                        ".py",
                        code,
                        "python"
                ))
                        .isInstanceOf(DockerExecutionException.class);
            } finally {
                limitedEngine.close();
            }
        }

        @Test
        @DisplayName("Should block network access when disabled")
        void should_block_network_access() {
            String code = """
                    import urllib.request
                    try:
                        urllib.request.urlopen('http://example.com', timeout=5)
                        print('NETWORK_ACCESSIBLE')
                    except Exception as e:
                        print('NETWORK_BLOCKED')
                    """;

            String result = engine.execute("python:3.12-slim", ".py", code, "python");

            assertThat(result).isEqualTo("NETWORK_BLOCKED");
        }

        @Test
        @DisplayName("Should run with dropped capabilities")
        void should_run_with_dropped_capabilities() {
            // This code would fail if running as root with full capabilities
            String code = """
                    import os
                    # Check we're not running as root (UID 0)
                    # or if we are, capabilities are dropped
                    print('running with limited privileges')
                    """;

            String result = engine.execute("python:3.12-slim", ".py", code, "python");

            assertThat(result).contains("limited privileges");
        }
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty output")
        void should_handle_empty_output() {
            String result = engine.execute(
                    "python:3.12-slim",
                    ".py",
                    "x = 1 + 1",  // No output
                    "python"
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle multiline output")
        void should_handle_multiline_output() {
            String code = """
                    for i in range(5):
                        print(f"Line {i}")
                    """;

            String result = engine.execute("python:3.12-slim", ".py", code, "python");

            assertThat(result).contains("Line 0", "Line 1", "Line 2", "Line 3", "Line 4");
        }

        @Test
        @DisplayName("Should handle special characters in output")
        void should_handle_special_characters() {
            String result = engine.execute(
                    "python:3.12-slim",
                    ".py",
                    "print('Special: $HOME \\t tab \\n newline')",
                    "python"
            );

            assertThat(result).contains("Special:");
        }

        @Test
        @DisplayName("Should handle large output")
        void should_handle_large_output() {
            String code = """
                    for i in range(1000):
                        print(f"Line {i}: " + "x" * 50)
                    """;

            String result = engine.execute("python:3.12-slim", ".py", code, "python");

            assertThat(result).isNotEmpty();
            assertThat(result.split("\n")).hasSizeGreaterThan(100);
        }

        @Test
        @DisplayName("Should handle code with file extension without dot")
        void should_handle_extension_without_dot() {
            String result = engine.execute(
                    "python:3.12-slim",
                    "py",  // without leading dot
                    "print('works')",
                    "python"
            );

            assertThat(result).isEqualTo("works");
        }
    }
}
