package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantNodeData {
    /**
     * 节点id
     */
    private String nodeId;
    /**
     * 节点类型
     */
    private String nodeType;
    /**
     * 节点名称
     */
    private String nodeName;
    /**
     * 节点状态
     * processing:处理中
     * finished:已完成
     * warning:发出告警并继续执行
     * conversation:对话中
     * error:异常
     */
    private String nodeStatus;
    /**
     * 节点耗时 单位秒 一位小数
     */
    private String nodeDur;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(final String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(final String nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public String getNodeDur() {
        return nodeDur;
    }

    public void setNodeDur(final String nodeDur) {
        this.nodeDur = nodeDur;
    }
}
