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

    private String requestId;
    private String nodeId;
    private String pushType;
    private AssistantNodeData nodeData;
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
