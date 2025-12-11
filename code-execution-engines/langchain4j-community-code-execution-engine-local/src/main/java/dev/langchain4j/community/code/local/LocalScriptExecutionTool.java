package dev.langchain4j.community.code.local;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool that executes provided script code using the local computer env.
 * Attention! It might be dangerous to execute the code, see {@link CommandLineExecutionEngine} for more details.
 */
public class LocalScriptExecutionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalScriptExecutionTool.class);
    private CommandLineExecutionEngine engine = new CommandLineExecutionEngine();

    @Tool("Execute local scripts, supported script type: BASH,ZSH,PYTHON3,OSASCRIPT.")
    public String execute(@P("Script type") ScriptType scriptType, @P("Script code to execute") String scriptCode) {
        switch (scriptType) {
            case BASH:
            case ZSH:
            case PYTHON3:
            case OSASCRIPT:
                if (!isEnvReady(scriptType)) {
                    throw new RuntimeException(scriptType + " env is not ready in the current computer.");
                }

                final String ret = _execute(scriptType, scriptCode);
                // e.g. for `open page www.google.com` engine will return nothing
                return ret != null && !ret.isEmpty() ? ret : "success";
            default:
                throw new IllegalArgumentException("Unsupported script type: " + scriptType);
        }
    }

    public String _execute(ScriptType scriptType, String scriptCode) {
        String codeFileName = "code_" + randomUUID() + ".tmp";
        File file = new File(codeFileName);
        try {
            Files.writeString(file.toPath(), scriptCode, StandardOpenOption.CREATE);
            return engine.execute(scriptType + " " + codeFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    public enum ScriptType {
        BASH,
        ZSH,
        PYTHON3,
        OSASCRIPT
    }

    boolean isEnvReady(ScriptType scriptType) {
        try {
            if (scriptType.equals(ScriptType.OSASCRIPT)) {
                engine.execute("osascript -e 'system version of (system info)'");
            } else {
                engine.execute(scriptType + " --version");
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn(scriptType + " env is not ready, due to " + e.getMessage());
            return false;
        }
    }
}
