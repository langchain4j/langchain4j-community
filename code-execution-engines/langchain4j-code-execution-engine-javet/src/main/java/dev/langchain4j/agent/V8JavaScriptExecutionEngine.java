package dev.langchain4j.agent;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.engine.IJavetEnginePool;
import com.caoccao.javet.interop.engine.JavetEngineConfig;
import com.caoccao.javet.interop.engine.JavetEnginePool;
import com.caoccao.javet.values.V8Value;
import dev.langchain4j.code.CodeExecutionEngine;

public class V8JavaScriptExecutionEngine implements CodeExecutionEngine {

    private final IJavetEnginePool<V8Runtime> javetEnginePool;

    public V8JavaScriptExecutionEngine() {
        this(createDefaultConfig());
    }

    public V8JavaScriptExecutionEngine(JavetEngineConfig config) {
        javetEnginePool = new JavetEnginePool<>(config);
    }


    @Override
    public String execute(final String code) {
        try(V8Value v8Value = javetEnginePool.getEngine().getV8Runtime().getExecutor(code).execute()){
            return v8Value.asString();
        }catch (JavetException e) {
            throw new RuntimeException("Execution failed", e);
        }
    }

    private static JavetEngineConfig createDefaultConfig() {
        JavetEngineConfig config = new JavetEngineConfig();
        config.setPoolMaxSize(20);
        config.setPoolMinSize(10);
        return config;
    }
}
