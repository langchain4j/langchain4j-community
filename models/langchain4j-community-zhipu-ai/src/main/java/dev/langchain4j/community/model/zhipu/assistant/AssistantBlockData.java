package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantBlockData {
    /**
     * 当前节点输入数据
     */
    private String input;
    /**
     * 块日志类型:
     * input:节点输入日志
     * action:调用的插件
     * output: 节点输出日志
     */
    private String blockType;
    /**
     * 节点名称
     */
    private String blockName;
    /**
     * 异常信息: 当块日志类型为error的时候 有值
     */
    private String errorMsg;
    /**
     * 块的总耗时,单位秒 一位小数
     */
    private String blockDur;
    /**
     * 节点输出
     */
    private AssistantOutPut outPut;

    public String getInput() {
        return input;
    }

    public void setInput(final String input) {
        this.input = input;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(final String blockType) {
        this.blockType = blockType;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(final String blockName) {
        this.blockName = blockName;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(final String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getBlockDur() {
        return blockDur;
    }

    public void setBlockDur(final String blockDur) {
        this.blockDur = blockDur;
    }

    public AssistantOutPut getOutPut() {
        return outPut;
    }

    public void setOutPut(final AssistantOutPut outPut) {
        this.outPut = outPut;
    }
}
