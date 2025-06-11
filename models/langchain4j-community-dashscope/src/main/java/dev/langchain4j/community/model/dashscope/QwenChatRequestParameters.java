package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parameter details are available <a href="https://www.alibabacloud.com/help/en/model-studio/use-qwen-by-calling-api#2ed5ee7377fum">here</a>.
 */
@Experimental
public class QwenChatRequestParameters extends DefaultChatRequestParameters {
    /**
     * If specified, it will make a best effort to sample deterministically, such that
     * repeated requests with the same seed and parameters should return the same
     * result.
     */
    private final Integer seed;
    /**
     * Whether the model should use internet search results for reference when generating
     * text.
     */
    private final Boolean enableSearch;
    /**
     * The strategy for network search. Only takes effect when enableSearch is true.
     */
    private final SearchOptions searchOptions;
    /**
     * The translation parameters you need to configure when you use the translation
     * models.
     */
    private final TranslationOptions translationOptions;
    /**
     * Whether to increase the default token limit for input images. The default token
     * limit for input images is 1280. When configured to true, the token limit for input
     * images is 16384. Default value is false.
     */
    private final Boolean vlHighResolutionImages;
    /**
     * Whether the model is a multimodal model (whether it supports multimodal input). If
     * not specified, it will be judged based on the model name when called, but these
     * judgments may not keep up with the latest situation.
     */
    private final Boolean isMultimodalModel;
    /**
     * Whether the model supports incremental output in the streaming output mode. This
     * parameter is used to assist QwenStreamingChatModel in providing incremental output
     * in stream mode. If not specified, it will be judged based on the model name when
     * called, but these judgments may not keep up with the latest situation.
     */
    private final Boolean supportIncrementalOutput;
    /**
     * Specifies whether to use the reasoning mode. Applicable for Qwen3 models.
     * Default value is false.
     */
    private final Boolean enableThinking;
    /**
     * The maximum reasoning length, effective when enable_thinking is set to true.
     * Applicable for qwen-plus-latest, qwen-turbo-latest and all other Qwen3 models.
     */
    private final Integer thinkingBudget;
    /**
     * User-defined parameters. They may have special effects on some special models.
     */
    private final Map<String, Object> custom;

    protected QwenChatRequestParameters(Builder builder) {
        super(builder);
        this.seed = builder.seed;
        this.enableSearch = builder.enableSearch;
        this.searchOptions = builder.searchOptions;
        this.translationOptions = builder.translationOptions;
        this.vlHighResolutionImages = builder.vlHighResolutionImages;
        this.isMultimodalModel = builder.isMultimodalModel;
        this.supportIncrementalOutput = builder.supportIncrementalOutput;
        this.enableThinking = getOrDefault(builder.enableThinking, Boolean.FALSE);
        this.thinkingBudget = builder.thinkingBudget;
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

    public Boolean isMultimodalModel() {
        return isMultimodalModel;
    }

    public Boolean supportIncrementalOutput() {
        return supportIncrementalOutput;
    }

    public Boolean enableThinking() {
        return enableThinking;
    }

    public Integer thinkingBudget() {
        return thinkingBudget;
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
        private Boolean isMultimodalModel;
        private Boolean supportIncrementalOutput;
        private Boolean enableThinking;
        private Integer thinkingBudget;
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
                enableThinking(getOrDefault(qwenParameters.enableThinking(), enableThinking));
                thinkingBudget(getOrDefault(qwenParameters.thinkingBudget(), thinkingBudget));
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

        public Builder isMultimodalModel(Boolean isMultimodalModel) {
            this.isMultimodalModel = isMultimodalModel;
            return this;
        }

        public Builder supportIncrementalOutput(Boolean supportIncrementalOutput) {
            this.supportIncrementalOutput = supportIncrementalOutput;
            return this;
        }

        public Builder enableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
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

    /**
     * The strategy for network search.
     *
     * @param enableSource Whether to display the searched information in the returned
     * results. Default value is false.
     * @param enableCitation Whether to enable the [1] or [ref_1] style superscript
     * annotation function. This function takes effect only when enable_source is true.
     * Default value is false.
     * @param citationFormat Subscript style. Only available when enable_citation is true.
     * Supported styles: “[1]” and “[ref_1]”. Default value is “[1]”.
     * @param forcedSearch Whether to force search to start.
     * @param searchStrategy The amount of Internet information searched. Supported
     * values: “standard” and “pro”. Default value is “standard”.
     */
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

    /**
     * The translation parameters you need to configure when you use the translation
     * models.
     *
     * @param sourceLang The full English name of the source language.For more
     * information, see <a href=
     * "https://www.alibabacloud.com/help/en/model-studio/machine-translation">Supported
     * Languages</a>. You can set source_lang to "auto" and the model will automatically
     * determine the language of the input text.
     * @param targetLang The full English name of the target language.For more
     * information, see <a href=
     * "https://www.alibabacloud.com/help/en/model-studio/machine-translation">Supported
     * Languages</a>.
     * @param terms An array of terms that needs to be set when using the
     * term-intervention-translation feature.
     * @param tmList The translation memory array that needs to be set when using the
     * translation-memory feature.
     * @param domains The domain prompt statement needs to be set when using the
     * domain-prompt feature.
     */
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

    /**
     * The term.
     *
     * @param source The term in the source language.
     * @param target The term in the target language.
     */
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
