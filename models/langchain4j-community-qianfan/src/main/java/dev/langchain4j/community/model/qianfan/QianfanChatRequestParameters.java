package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

public class QianfanChatRequestParameters extends DefaultChatRequestParameters {

    private final String userId;
    private final String endpoint;
    private final String system;

    protected QianfanChatRequestParameters(Builder builder) {
        super(builder);
        this.userId = builder.userId;
        this.endpoint = builder.endpoint;
        this.system = builder.system;
    }

    public String userId() {
        return userId;
    }

    public String endpoint() {
        return endpoint;
    }

    public String system() {
        return system;
    }

    @Override
    public QianfanChatRequestParameters overrideWith(ChatRequestParameters that) {
        return QianfanChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public QianfanChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return QianfanChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QianfanChatRequestParameters that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(endpoint, that.endpoint)
                && Objects.equals(system, that.system);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, endpoint, system);
    }

    @Override
    public String toString() {
        return "QianfanChatRequestParameters{"
                + "modelName=" + quoted(modelName())
                + ", temperature=" + temperature()
                + ", topP=" + topP()
                + ", topK=" + topK()
                + ", frequencyPenalty=" + frequencyPenalty()
                + ", presencePenalty=" + presencePenalty()
                + ", maxOutputTokens=" + maxOutputTokens()
                + ", stopSequences=" + stopSequences()
                + ", toolSpecifications=" + toolSpecifications()
                + ", toolChoice=" + toolChoice()
                + ", responseFormat=" + responseFormat()
                + ", userId=" + quoted(userId)
                + ", endpoint=" + quoted(endpoint)
                + ", system=" + quoted(system)
                + '}';
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String userId;
        private String endpoint;
        private String system;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof QianfanChatRequestParameters qianfanParams) {
                userId(getOrDefault(qianfanParams.userId(), userId));
                endpoint(getOrDefault(qianfanParams.endpoint(), endpoint));
                system(getOrDefault(qianfanParams.system(), system));
            }
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        @Override
        public QianfanChatRequestParameters build() {
            return new QianfanChatRequestParameters(this);
        }
    }
}
