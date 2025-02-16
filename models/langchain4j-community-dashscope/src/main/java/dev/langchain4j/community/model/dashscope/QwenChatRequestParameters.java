package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

/**
 * Parameter details are available <a href="https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api#2ed5ee7377fum">here</a>.
 */
@Experimental
public class QwenChatRequestParameters extends DefaultChatRequestParameters {
    private final Integer seed;
    private final Boolean enableSearch;
    private final SearchOptions searchOptions;
    private final TranslationOptions translationOptions;
    private final Boolean vlHighResolutionImages;
    private final Map<String, Object> custom;

    protected QwenChatRequestParameters(Builder builder) {
        super(builder);
        this.seed = builder.seed;
        this.enableSearch = builder.enableSearch;
        this.searchOptions = builder.searchOptions;
        this.translationOptions = builder.translationOptions;
        this.vlHighResolutionImages = builder.vlHighResolutionImages;
        this.custom = builder.custom;
    }

    public Integer seed() {
        return seed;
    }

    public Boolean enableSearch() {
        return enableSearch;
    }

    public SearchOptions searchOptions() {
        return searchOptions;
    }

    public TranslationOptions translationOptions() {
        return translationOptions;
    }

    public Boolean vlHighResolutionImages() {
        return vlHighResolutionImages;
    }

    public Map<String, Object> custom() {
        return custom;
    }

    @Override
    public QwenChatRequestParameters overrideWith(ChatRequestParameters that) {
        return QwenChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QwenChatRequestParameters that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(seed, that.seed)
                && Objects.equals(enableSearch, that.enableSearch)
                && Objects.equals(searchOptions, that.searchOptions)
                && Objects.equals(translationOptions, that.translationOptions)
                && Objects.equals(vlHighResolutionImages, that.vlHighResolutionImages)
                && Objects.equals(custom, that.custom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                seed,
                enableSearch,
                searchOptions,
                translationOptions,
                vlHighResolutionImages,
                custom);
    }

    @Override
    public String toString() {
        return "QwenChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", seed="
                + seed + ", enableSearch="
                + enableSearch + ", searchOptions="
                + searchOptions + ", translationOptions="
                + translationOptions + ", vlHighResolutionImages="
                + vlHighResolutionImages + ", custom="
                + custom + '}';
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {
        private Integer seed;
        private Boolean enableSearch;
        private SearchOptions searchOptions;
        private TranslationOptions translationOptions;
        private Boolean vlHighResolutionImages;
        private Map<String, Object> custom;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof QwenChatRequestParameters qwenParameters) {
                seed(getOrDefault(qwenParameters.seed(), seed));
                enableSearch(getOrDefault(qwenParameters.enableSearch(), enableSearch));
                searchOptions(getOrDefault(qwenParameters.searchOptions(), searchOptions));
                translationOptions(getOrDefault(qwenParameters.translationOptions(), translationOptions));
                vlHighResolutionImages(getOrDefault(qwenParameters.vlHighResolutionImages(), vlHighResolutionImages));
                custom(getOrDefault(qwenParameters.custom(), custom));
            }
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public Builder searchOptions(SearchOptions searchOptions) {
            this.searchOptions = searchOptions;
            return this;
        }

        public Builder translationOptions(TranslationOptions translationOptions) {
            this.translationOptions = translationOptions;
            return this;
        }

        public Builder vlHighResolutionImages(Boolean vlHighResolutionImages) {
            this.vlHighResolutionImages = vlHighResolutionImages;
            return this;
        }

        public Builder custom(Map<String, Object> custom) {
            this.custom = custom;
            return this;
        }

        @Override
        public QwenChatRequestParameters build() {
            return new QwenChatRequestParameters(this);
        }
    }

    public record SearchOptions(
            Boolean enableSource,
            Boolean enableCitation,
            String citationFormat,
            Boolean forcedSearch,
            String searchStrategy) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Boolean enableSource;
            private Boolean enableCitation;
            private String citationFormat;
            private Boolean forcedSearch;
            private String searchStrategy;

            public Builder enableSource(Boolean enableSource) {
                this.enableSource = enableSource;
                return this;
            }

            public Builder enableCitation(Boolean enableCitation) {
                this.enableCitation = enableCitation;
                return this;
            }

            public Builder citationFormat(String citationFormat) {
                this.citationFormat = citationFormat;
                return this;
            }

            public Builder forcedSearch(Boolean forcedSearch) {
                this.forcedSearch = forcedSearch;
                return this;
            }

            public Builder searchStrategy(String searchStrategy) {
                this.searchStrategy = searchStrategy;
                return this;
            }

            public SearchOptions build() {
                return new SearchOptions(enableSource, enableCitation, citationFormat, forcedSearch, searchStrategy);
            }
        }
    }

    public record TranslationOptions(
            String sourceLang,
            String targetLang,
            List<TranslationOptionTerm> terms,
            List<TranslationOptionTerm> tmList,
            String domains) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String sourceLang;
            private String targetLang;
            private List<TranslationOptionTerm> terms;
            private List<TranslationOptionTerm> tmLists;
            private String domains;

            public Builder sourceLang(String sourceLang) {
                this.sourceLang = sourceLang;
                return this;
            }

            public Builder targetLang(String targetLang) {
                this.targetLang = targetLang;
                return this;
            }

            public Builder terms(List<TranslationOptionTerm> terms) {
                this.terms = terms;
                return this;
            }

            public Builder tmLists(List<TranslationOptionTerm> tmLists) {
                this.tmLists = tmLists;
                return this;
            }

            public Builder domains(String domains) {
                this.domains = domains;
                return this;
            }

            public TranslationOptions build() {
                return new TranslationOptions(sourceLang, targetLang, terms, tmLists, domains);
            }
        }
    }

    public record TranslationOptionTerm(String source, String target) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String source;
            private String target;

            public Builder source(String source) {
                this.source = source;
                return this;
            }

            public Builder target(String target) {
                this.target = target;
                return this;
            }

            public TranslationOptionTerm build() {
                return new TranslationOptionTerm(source, target);
            }
        }
    }
}
