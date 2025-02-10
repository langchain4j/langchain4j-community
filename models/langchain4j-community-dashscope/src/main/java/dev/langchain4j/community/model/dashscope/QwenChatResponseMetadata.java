package dev.langchain4j.community.model.dashscope;

import com.alibaba.dashscope.aigc.generation.SearchInfo;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@Experimental
public class QwenChatResponseMetadata extends ChatResponseMetadata {
    private final SearchInfo searchInfo;

    protected QwenChatResponseMetadata(Builder builder) {
        super(builder);
        this.searchInfo = builder.searchInfo;
    }

    public SearchInfo searchInfo() {
        return searchInfo;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>(5);
        if (id() != null) {
            map.put("id", id());
        }
        if (modelName() != null) {
            map.put("modelName", modelName());
        }
        if (tokenUsage() != null) {
            map.put("tokenUsage", tokenUsage());
        }
        if (finishReason() != null) {
            map.put("finishReason", finishReason());
        }
        if (searchInfo != null) {
            map.put("searchInfo", searchInfo);
        }
        return map;
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
