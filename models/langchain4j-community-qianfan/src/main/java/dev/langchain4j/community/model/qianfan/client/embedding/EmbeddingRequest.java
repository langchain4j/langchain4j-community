package dev.langchain4j.community.model.qianfan.client.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public final class EmbeddingRequest {

    private final String model;
    private final List<String> input;
    private final String user;

    private EmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
        this.user = builder.user;
    }

    public String getModel() {
        return model;
    }

    public List<String> getInput() {
        return input;
    }

    public String getUser() {
        return user;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof EmbeddingRequest
                    && this.equalTo((EmbeddingRequest) another);
        }
    }

    private boolean equalTo(EmbeddingRequest another) {
        return Objects.equals(this.model, another.model) && Objects.equals(this.input, another.input) && Objects.equals(this.user, another.user);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.model);
        h += (h << 5) + Objects.hashCode(this.input);
        h += (h << 5) + Objects.hashCode(this.user);
        return h;
    }

    public String toString() {
        return "EmbeddingRequest{model=" + this.model + ", input=" + this.input + ", user=" + this.user + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String model;
        private List<String> input;
        private String user;

        private Builder() {

        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }


        public Builder input(String... input) {
            return this.input(Arrays.asList(input));
        }

        public Builder input(List<String> input) {
            if (input != null) {
                this.input = Collections.unmodifiableList(input);
            }
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}

