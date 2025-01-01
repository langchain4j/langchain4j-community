package dev.langchain4j.community.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.code.CodeExecutionEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class V8JavaScriptExecutionEngineTest {

    CodeExecutionEngine engine = new V8JavaScriptExecutionEngine();

    @Test
    void should_execute_code() {

        String code =
                """
                function fibonacci(n) {
                    if (n <= 1) return n;
                    return fibonacci(n - 1) + fibonacci(n - 2);
                }

                fibonacci(10)
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("55");
    }

    @Test
    void testV8RuntimeThreadSafety() throws InterruptedException, ExecutionException {

        V8JavaScriptExecutionEngine engine = new V8JavaScriptExecutionEngine();

        String code = "'Hello from thread ' + this.threadId";

        int threadCount = 10;
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            tasks.add(() -> {
                try {
                    // Each thread executes the JavaScript code
                    return engine.execute(code.replace("this.threadId", Integer.toString(threadId)));
                } catch (RuntimeException e) {
                    return "Execution failed for thread " + threadId + ": " + e.getMessage();
                }
            });
        }

        List<Future<String>> results = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        for (int i = 0; i < threadCount; i++) {
            String result = results.get(i).get();
            assertEquals("Hello from thread " + i, result, "Unexpected result in thread " + i);
        }
    }
}
