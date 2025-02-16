package dev.langchain4j.community.dashscope.spring;

import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class ChatModelProperties {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double topP;
    private Integer topK;
    private Boolean enableSearch;
    private Integer seed;
    private Float repetitionPenalty;
    private Float temperature;
    private List<String> stops;
    private Integer maxTokens;
    private Parameters parameters;

    @Getter
    @Setter
    public static class Parameters {
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private ToolChoice toolChoice;
        private ResponseFormatType responseFormat;
        private Integer seed;
        private Boolean enableSearch;
        private SearchOptions searchOptions;
        private TranslationOptions translationOptions;
        private Boolean vlHighResolutionImages;
    }

    @Getter
    @Setter
    public static class SearchOptions {
        private Boolean enableSource;
        private Boolean enableCitation;
        private String citationFormat;
        private Boolean forcedSearch;
        private String searchStrategy;
    }

    @Getter
    @Setter
    public static class TranslationOptions {
        private String sourceLang;
        private String targetLang;
        private List<TranslationOptionTerm> terms;
        private List<TranslationOptionTerm> tmList;
        private String domains;
    }

    @Getter
    @Setter
    public static class TranslationOptionTerm {
        private String source;
        private String target;
    }
}
