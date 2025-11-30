package dev.langchain4j.community.code.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandLineExecutionEngineTest {

    private final CommandLineExecutionEngine engine = new CommandLineExecutionEngine();

    @Test
    void cmd_line_tests() {
        assertThat(engine.execute("ls -a")).contains(".");
        assertThat(engine.execute("bash --version")).contains("bash");
    }
}
