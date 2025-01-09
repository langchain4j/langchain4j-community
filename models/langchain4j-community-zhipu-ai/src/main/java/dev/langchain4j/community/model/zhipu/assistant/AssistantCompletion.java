package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.zhipu.shared.Usage;

/**
 * This class represents the completion data returned by an assistant.
 */
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantCompletion {

    /**
     * 模型响应消息
     */
    private String msg;

    /**
     * 节点执行日志: 如 触发的agent工具动作, 节点跳转等
     */
    private AssistantExtraInput extraInput;

    /**
     * token消耗, event为finish、errorhandler会有此值
     */
    private Usage usage;

    public String getMsg() {
        return msg;
    }

    public void setMsg(final String msg) {
        this.msg = msg;
    }

    public AssistantExtraInput getExtraInput() {
        return extraInput;
    }

    public void setExtraInput(final AssistantExtraInput extraInput) {
        this.extraInput = extraInput;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(final Usage usage) {
        this.usage = usage;
    }
}
