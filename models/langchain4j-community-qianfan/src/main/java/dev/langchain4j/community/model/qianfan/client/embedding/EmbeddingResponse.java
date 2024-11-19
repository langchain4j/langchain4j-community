package dev.langchain4j.community.model.qianfan.client.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.qianfan.client.Usage;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public final class EmbeddingResponse {

    private String object;
    private String id;
    private Integer created;
    private List<EmbeddingData> data;
    private Usage usage;
    private String errorCode;
    private String errorMsg;

    public EmbeddingResponse() {
    }

    public String getObject() {
        return object;
    }

    public void setObject(final String object) {
        this.object = object;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getCreated() {
        return created;
    }

    public void setCreated(final Integer created) {
        this.created = created;
    }

    public List<EmbeddingData> getData() {
        return data;
    }

    public void setData(final List<EmbeddingData> data) {
        this.data = data;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(final Usage usage) {
        this.usage = usage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(final String errorMsg) {
        this.errorMsg = errorMsg;
    }

    @Override
    public String toString() {
        return "EmbeddingResponse{" +
                "object='" + object + '\'' +
                ", id='" + id + '\'' +
                ", created=" + created +
                ", data=" + data +
                ", usage=" + usage +
                '}';
    }
}

