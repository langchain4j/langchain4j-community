package dev.langchain4j.community.model.xinference.client.rerank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = RerankRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class RerankRequest {
    private final String model;
    private final String query;
    private final List<String> documents;
    private final Integer topN;
    private final Boolean returnDocuments;
    private final Boolean returnLen;

    private RerankRequest(Builder builder) {
        model = builder.model;
        query = builder.query;
        documents = builder.documents;
        topN = builder.topN;
        returnDocuments = builder.returnDocuments;
        returnLen = builder.returnLen;
    }

    public String getModel() {
        return model;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public Integer getTopN() {
        return topN;
    }

    public Boolean getReturnDocuments() {
        return returnDocuments;
    }

    public Boolean getReturnLen() {
        return returnLen;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private String query;
        private List<String> documents;
        private Integer topN;
        private Boolean returnDocuments;
        private Boolean returnLen;

        private Builder() {}

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder query(String val) {
            query = val;
            return this;
        }

        public Builder documents(List<String> val) {
            documents = val;
            return this;
        }

        public Builder topN(Integer val) {
            topN = val;
            return this;
        }

        public Builder returnDocuments(Boolean val) {
            returnDocuments = val;
            return this;
        }

        public Builder returnLen(Boolean val) {
            returnLen = val;
            return this;
        }

        public RerankRequest build() {
            return new RerankRequest(this);
        }
    }
}
