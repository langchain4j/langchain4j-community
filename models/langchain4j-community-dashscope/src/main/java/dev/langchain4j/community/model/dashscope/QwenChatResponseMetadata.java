package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.aigc.generation.SearchInfo;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@Experimental
public class QwenChatResponseMetadata extends ChatResponseMetadata {
    private final SearchInfo searchInfo;

    protected QwenChatResponseMetadata(Builder builder) {
        super(builder);
        this.searchInfo = builder.searchInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QwenChatResponseMetadata that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(searchInfo, that.searchInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), searchInfo);
    }

    @Override
    public String toString() {
        return "QwenChatResponseMetadata{" +
                "id=" + quoted(id()) +
                ", modelName=" + quoted(modelName()) +
                ", tokenUsage=" + tokenUsage() +
                ", finishReason=" + finishReason() +
                ", searchInfo=" + searchInfo +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private SearchInfo searchInfo;

        public  Builder searchInfo(SearchInfo searchInfo) {
            this.searchInfo = searchInfo;
            return this;
        }

        @Override
        public QwenChatResponseMetadata build() {
            return new QwenChatResponseMetadata(this);
        }
    }
}
