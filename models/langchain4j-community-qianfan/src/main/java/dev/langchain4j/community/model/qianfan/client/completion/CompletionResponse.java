package dev.langchain4j.community.model.qianfan.client.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.qianfan.client.Usage;
import dev.langchain4j.community.model.qianfan.client.chat.FunctionCall;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public final class CompletionResponse {

    private String id;
    private Integer errorCode;
    private String errorMsg;
    private String object;
    private Integer created;
    private Integer sentenceId;
    private Boolean isEnd;
    private Boolean isTruncated;
    private String result;
    private String finishReason;
    private Boolean needClearHistory;
    private Integer banRound;
    private Usage usage;
    private FunctionCall functionCall;

    public CompletionResponse() {
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

    public Boolean getIsEnd() {
        return isEnd;
    }

    public void setIsEnd(final Boolean isEnd) {
        this.isEnd = isEnd;
    }

    public Boolean getIsTruncated() {
        return isTruncated;
    }

    public void setIsTruncated(final Boolean isTruncated) {
        this.isTruncated = isTruncated;
    }

    public String getResult() {
        return result;
    }

    public void setResult(final String result) {
        this.result = result;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(final String finishReason) {
        this.finishReason = finishReason;
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

    @Override
    public String toString() {
        return "CompletionResponse{" +
                "id='" + id + '\'' +
                ", errorCode=" + errorCode +
                ", errorMsg='" + errorMsg + '\'' +
                ", object='" + object + '\'' +
                ", created=" + created +
                ", sentenceId=" + sentenceId +
                ", isEnd=" + isEnd +
                ", isTruncated=" + isTruncated +
                ", result='" + result + '\'' +
                ", finishReason='" + finishReason + '\'' +
                ", needClearHistory=" + needClearHistory +
                ", banRound=" + banRound +
                ", usage=" + usage +
                ", functionCall=" + functionCall +
                '}';
    }
}
