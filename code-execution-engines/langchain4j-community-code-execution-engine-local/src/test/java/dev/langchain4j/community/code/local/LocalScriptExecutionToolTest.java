package dev.langchain4j.community.code.local;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.code.local.LocalScriptExecutionTool.ScriptType;
import org.junit.jupiter.api.Test;

class LocalScriptExecutionToolTest {

    private final LocalScriptExecutionTool tool = new LocalScriptExecutionTool();

    @Test
    public void bash_tests() {
        if (!tool.isEnvReady(ScriptType.BASH)) {
            return;
        }
        assertThat(tool.execute(ScriptType.BASH, "echo 'hi shell'")).isEqualTo("hi shell");
    }

    @Test
    public void zsh_tests() {
        if (!tool.isEnvReady(ScriptType.ZSH)) {
            return;
        }
        assertThat(tool.execute(ScriptType.ZSH, "echo 'hi zshell'")).isEqualTo("hi zshell");
    }

    @Test
    public void python3_tests() {
        if (!tool.isEnvReady(ScriptType.PYTHON3)) {
            return;
        }
        assertThat(tool.execute(ScriptType.PYTHON3, "print('hi py')")).isEqualTo("hi py");
    }

    @Test
    public void osascript_tests() {
        if (!tool.isEnvReady(ScriptType.OSASCRIPT)) {
            return;
        }
        assertThat(tool.execute(ScriptType.OSASCRIPT, "computer name of (system info)"))
                .isNotEmpty();
    }
}
