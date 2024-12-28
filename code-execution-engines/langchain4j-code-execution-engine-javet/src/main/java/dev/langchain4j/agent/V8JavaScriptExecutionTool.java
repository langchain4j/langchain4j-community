package dev.langchain4j.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;

public class V8JavaScriptExecutionTool {


    private final CodeExecutionEngine engine;

    public V8JavaScriptExecutionTool() {
        this(new V8JavaScriptExecutionEngine());
    }

    public V8JavaScriptExecutionTool(CodeExecutionEngine engine) {
        this.engine = engine;
    }

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executeJavaScriptCode(@P("JavaScript code to execute, result MUST be returned by the code") String code) {
        return engine.execute(code);
    }
}
