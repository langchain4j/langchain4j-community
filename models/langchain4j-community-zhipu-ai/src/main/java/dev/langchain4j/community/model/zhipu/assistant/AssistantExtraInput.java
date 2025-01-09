package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantExtraInput {
    /**
     * 请求id
     */
    private String requestId;
    /**
     * 节点id
     */
    private String nodeId;
    /**
     * 推送日志类型 node:节点级日志, block:节点内块日志
     */
    private String pushType;
    /**
     * 节点级日志数据: 节点开始、完成日志
     */
    private AssistantNodeData nodeData;
    /**
     * 节点内块日志数据: 节点异常、执行动作、知识库查询等事件日志
     */
    private AssistantBlockData blockData;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(final String requestId) {
        this.requestId = requestId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPushType() {
        return pushType;
    }

    public void setPushType(final String pushType) {
        this.pushType = pushType;
    }

    public AssistantNodeData getNodeData() {
        return nodeData;
    }

    public void setNodeData(final AssistantNodeData nodeData) {
        this.nodeData = nodeData;
    }

    public AssistantBlockData getBlockData() {
        return blockData;
    }

    public void setBlockData(final AssistantBlockData blockData) {
        this.blockData = blockData;
    }
}
