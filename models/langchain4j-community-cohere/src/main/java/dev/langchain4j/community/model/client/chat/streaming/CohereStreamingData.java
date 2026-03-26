package dev.langchain4j.community.model.client.chat.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereStreamingData.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereStreamingData {

    private final String type;
    private final String id;
    private final CohereDelta delta;
    private final Integer index;

    private CohereStreamingData(Builder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.delta = builder.delta;
        this.index = builder.index;
    }

    public String getType() { return type; }

    public String getId() { return id; }

    public CohereDelta getDelta() { return delta; }

    public Integer getIndex() { return index; }

    public String toString() {
        return "CohereStreamingResponse{"
                    + "type=" + type
                    + ", id=" + id
                    + ", delta=" + delta
                    + ", index=" + index
                + "}";
    }

    public int hashCode() { return Objects.hash(type, id, delta, index); }

    public boolean equals(Object o) {
        return o instanceof CohereStreamingData response && equalsTo(response);
    }

    private boolean equalsTo(CohereStreamingData that) {
        return Objects.equals(type, that.type) && Objects.equals(id, that.id)
                && Objects.equals(delta, that.delta) && Objects.equals(index, that.index);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String type;
        private String id;
        private CohereDelta delta;
        private Integer index;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder delta(CohereDelta delta) {
            this.delta = delta;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public CohereStreamingData build() {
            return new CohereStreamingData(this);
        }
    }
}
