package dev.langchain4j.community.dashscope.spring;

import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.List;

public class ChatModelProperties {

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
    private Boolean isMultimodalModel;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getEnableSearch() {
        return enableSearch;
    }

    public void setEnableSearch(Boolean enableSearch) {
        this.enableSearch = enableSearch;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public Float getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public void setRepetitionPenalty(Float repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public List<String> getStops() {
        return stops;
    }

    public void setStops(List<String> stops) {
        this.stops = stops;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public Boolean getIsMultimodalModel() {
        return isMultimodalModel;
    }

    public void setIsMultimodalModel(Boolean isMultimodalModel) {
        this.isMultimodalModel = isMultimodalModel;
    }

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
        private Boolean isMultimodalModel;
        private Boolean supportIncrementalOutput;
        private Boolean enableThinking;
        private Integer thinkingBudget;

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getTopK() {
            return topK;
        }

        public void setTopK(Integer topK) {
            this.topK = topK;
        }

        public Double getFrequencyPenalty() {
            return frequencyPenalty;
        }

        public void setFrequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
        }

        public Double getPresencePenalty() {
            return presencePenalty;
        }

        public void setPresencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public List<String> getStopSequences() {
            return stopSequences;
        }

        public void setStopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
        }

        public ToolChoice getToolChoice() {
            return toolChoice;
        }

        public void setToolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
        }

        public ResponseFormatType getResponseFormat() {
            return responseFormat;
        }

        public void setResponseFormat(ResponseFormatType responseFormat) {
            this.responseFormat = responseFormat;
        }

        public Integer getSeed() {
            return seed;
        }

        public void setSeed(Integer seed) {
            this.seed = seed;
        }

        public Boolean getEnableSearch() {
            return enableSearch;
        }

        public void setEnableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
        }

        public SearchOptions getSearchOptions() {
            return searchOptions;
        }

        public void setSearchOptions(SearchOptions searchOptions) {
            this.searchOptions = searchOptions;
        }

        public TranslationOptions getTranslationOptions() {
            return translationOptions;
        }

        public void setTranslationOptions(TranslationOptions translationOptions) {
            this.translationOptions = translationOptions;
        }

        public Boolean getVlHighResolutionImages() {
            return vlHighResolutionImages;
        }

        public void setVlHighResolutionImages(Boolean vlHighResolutionImages) {
            this.vlHighResolutionImages = vlHighResolutionImages;
        }

        public Boolean getIsMultimodalModel() {
            return isMultimodalModel;
        }

        public void setIsMultimodalModel(Boolean isMultimodalModel) {
            this.isMultimodalModel = isMultimodalModel;
        }

        public Boolean getSupportIncrementalOutput() {
            return supportIncrementalOutput;
        }

        public void setSupportIncrementalOutput(Boolean supportIncrementalOutput) {
            this.supportIncrementalOutput = supportIncrementalOutput;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        public Integer getThinkingBudget() {
            return thinkingBudget;
        }

        public void setThinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
        }
    }

    public static class SearchOptions {
        private Boolean enableSource;
        private Boolean enableCitation;
        private String citationFormat;
        private Boolean forcedSearch;
        private String searchStrategy;

        public Boolean getEnableSource() {
            return enableSource;
        }

        public void setEnableSource(Boolean enableSource) {
            this.enableSource = enableSource;
        }

        public Boolean getEnableCitation() {
            return enableCitation;
        }

        public void setEnableCitation(Boolean enableCitation) {
            this.enableCitation = enableCitation;
        }

        public String getCitationFormat() {
            return citationFormat;
        }

        public void setCitationFormat(String citationFormat) {
            this.citationFormat = citationFormat;
        }

        public Boolean getForcedSearch() {
            return forcedSearch;
        }

        public void setForcedSearch(Boolean forcedSearch) {
            this.forcedSearch = forcedSearch;
        }

        public String getSearchStrategy() {
            return searchStrategy;
        }

        public void setSearchStrategy(String searchStrategy) {
            this.searchStrategy = searchStrategy;
        }
    }

    public static class TranslationOptions {
        private String sourceLang;
        private String targetLang;
        private List<TranslationOptionTerm> terms;
        private List<TranslationOptionTerm> tmList;
        private String domains;

        public String getSourceLang() {
            return sourceLang;
        }

        public void setSourceLang(String sourceLang) {
            this.sourceLang = sourceLang;
        }

        public String getTargetLang() {
            return targetLang;
        }

        public void setTargetLang(String targetLang) {
            this.targetLang = targetLang;
        }

        public List<TranslationOptionTerm> getTerms() {
            return terms;
        }

        public void setTerms(List<TranslationOptionTerm> terms) {
            this.terms = terms;
        }

        public List<TranslationOptionTerm> getTmList() {
            return tmList;
        }

        public void setTmList(List<TranslationOptionTerm> tmList) {
            this.tmList = tmList;
        }

        public String getDomains() {
            return domains;
        }

        public void setDomains(String domains) {
            this.domains = domains;
        }
    }

    public static class TranslationOptionTerm {
        private String source;
        private String target;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}
