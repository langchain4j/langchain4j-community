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

    private String input;
    private String blockType;
    private String blockName;
    private String errorMsg;
    private String blockDur;
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

    public void setBlockName(String blockName) {
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
