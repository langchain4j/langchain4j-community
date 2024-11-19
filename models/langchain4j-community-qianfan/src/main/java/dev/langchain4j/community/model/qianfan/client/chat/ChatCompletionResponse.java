package dev.langchain4j.community.model.qianfan.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.qianfan.client.Usage;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public final class ChatCompletionResponse {

    private String id;
    private Integer errorCode;
    private String errorMsg;
    private String object;
    private Integer created;
    private Integer sentenceId;
    private Boolean isEnd;
    private Boolean isTruncated;
    private String result;
    private Boolean needClearHistory;
    private Integer banRound;
    private Usage usage;
    private FunctionCall functionCall;
    private String finishReason;

    public ChatCompletionResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(final String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getObject() {
        return object;
    }

    public void setObject(final String object) {
        this.object = object;
    }

    public Integer getCreated() {
        return created;
    }

    public void setCreated(final Integer created) {
        this.created = created;
    }

    public Integer getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(final Integer sentenceId) {
        this.sentenceId = sentenceId;
    }

    public Boolean getEnd() {
        return isEnd;
    }

    public void setEnd(final Boolean end) {
        isEnd = end;
    }

    public Boolean getTruncated() {
        return isTruncated;
    }

    public void setTruncated(final Boolean truncated) {
        isTruncated = truncated;
    }

    public String getResult() {
        return result;
    }

    public void setResult(final String result) {
        this.result = result;
    }

    public Boolean getNeedClearHistory() {
        return needClearHistory;
    }

    public void setNeedClearHistory(final Boolean needClearHistory) {
        this.needClearHistory = needClearHistory;
    }

    public Integer getBanRound() {
        return banRound;
    }

    public void setBanRound(final Integer banRound) {
        this.banRound = banRound;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(final Usage usage) {
        this.usage = usage;
    }

    public FunctionCall getFunctionCall() {
        return functionCall;
    }

    public void setFunctionCall(final FunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(final String finishReason) {
        this.finishReason = finishReason;
    }

    @Override
    public String toString() {
        return "ChatCompletionResponse{" +
                "id='" + id + '\'' +
                ", errorCode=" + errorCode +
                ", errorMsg='" + errorMsg + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", sentenceId=" + sentenceId +
                ", isEnd=" + isEnd +
                ", isTruncated=" + isTruncated +
                ", result='" + result + '\'' +
                ", needClearHistory=" + needClearHistory +
                ", banRound=" + banRound +
                ", usage=" + usage +
                ", functionCall=" + functionCall +
                ", finishReason=" + finishReason +
                '}';
    }
}

