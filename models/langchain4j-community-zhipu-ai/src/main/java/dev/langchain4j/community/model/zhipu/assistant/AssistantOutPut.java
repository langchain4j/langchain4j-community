package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantOutPut {
    /**
     * function执行时间 单位秒 一位小数
     */
    private String functionExecTime;
    /**
     * 模型执行时间 单位秒 一位小数
     */
    private String modelExecTime;
    /**
     * 输出内容
     */
    private String outContent;

    public String getFunctionExecTime() {
        return functionExecTime;
    }

    public void setFunctionExecTime(final String functionExecTime) {
        this.functionExecTime = functionExecTime;
    }

    public String getModelExecTime() {
        return modelExecTime;
    }

    public void setModelExecTime(final String modelExecTime) {
        this.modelExecTime = modelExecTime;
    }

    public String getOutContent() {
        return outContent;
    }

    public void setOutContent(final String outContent) {
        this.outContent = outContent;
    }
}
