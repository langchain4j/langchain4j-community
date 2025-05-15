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
    private final String reasoningContent;

    protected QwenChatResponseMetadata(Builder builder) {
        super(builder);
        this.searchInfo = builder.searchInfo;
        this.reasoningContent = builder.reasoningContent;
    }

    public SearchInfo searchInfo() {
        return searchInfo;
    }

    public String reasoningContent() {
        return reasoningContent;
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
        if (reasoningContent != null) {
            map.put("reasoningContent", reasoningContent);
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QwenChatResponseMetadata that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(searchInfo, that.searchInfo) && Objects.equals(reasoningContent, that.reasoningContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), searchInfo, reasoningContent);
    }

    @Override
    public String toString() {
        return "QwenChatResponseMetadata{" + "id="
                + quoted(id()) + ", modelName="
                + quoted(modelName()) + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", searchInfo="
                + searchInfo + ", reasoningContent="
                + reasoningContent + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private SearchInfo searchInfo;
        private String reasoningContent;

        public Builder searchInfo(SearchInfo searchInfo) {
            this.searchInfo = searchInfo;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        @Override
        public QwenChatResponseMetadata build() {
            return new QwenChatResponseMetadata(this);
        }
    }

    /**
     * The information searched on the Internet will be returned after the search_options
     * parameter is set.
     *
     * @param searchResults a list of results from online searches
     */
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

    /**
     * Results from online searches.
     *
     * @param siteName the name of the website from which the search results came
     * @param icon the URL of the icon from the source website, or an empty string if there is
     * no icon
     * @param index the sequence number of the search result, indicating the index of the
     * search result in search_results
     * @param title the title of the search result
     * @param url the URL of the search result
     */
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
