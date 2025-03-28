package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        return "QwenChatResponseMetadata{" + "id="
                + quoted(id()) + ", modelName="
                + quoted(modelName()) + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", searchInfo="
                + searchInfo + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private SearchInfo searchInfo;

        public Builder searchInfo(SearchInfo searchInfo) {
            this.searchInfo = searchInfo;
            return this;
        }

        @Override
        public QwenChatResponseMetadata build() {
            return new QwenChatResponseMetadata(this);
        }
    }

    public record SearchInfo(List<SearchResult> searchResults) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<SearchResult> searchResults;

            public Builder searchResults(List<SearchResult> searchResults) {
                this.searchResults = searchResults;
                return this;
            }

            public SearchInfo build() {
                return new SearchInfo(searchResults);
            }
        }
    }

    public record SearchResult(String siteName, String icon, Integer index, String title, String url) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String siteName;
            private String icon;
            private Integer index;
            private String title;
            private String url;

            public Builder siteName(String siteName) {
                this.siteName = siteName;
                return this;
            }

            public Builder icon(String icon) {
                this.icon = icon;
                return this;
            }

            public Builder index(Integer index) {
                this.index = index;
                return this;
            }

            public Builder title(String title) {
                this.title = title;
                return this;
            }

            public Builder url(String url) {
                this.url = url;
                return this;
            }

            public SearchResult build() {
                return new SearchResult(siteName, icon, index, title, url);
            }
        }
    }
}
