package dev.langchain4j.community.dashscope.spring;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenLanguageModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingLanguageModel;
import dev.langchain4j.community.model.dashscope.QwenTokenCountEstimator;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class AutoConfig {

    private static List<QwenChatRequestParameters.TranslationOptionTerm> getTmList(
            ChatModelProperties.TranslationOptions translationOptions) {
        List<ChatModelProperties.TranslationOptionTerm> tmList = translationOptions.getTmList();
        if (isNullOrEmpty(tmList)) {
            return Collections.emptyList();
        }

        return tmList.stream()
                .map(term -> QwenChatRequestParameters.TranslationOptionTerm.builder()
                        .source(term.getSource())
                        .target(term.getTarget())
                        .build())
                .collect(toList());
    }

    private static List<QwenChatRequestParameters.TranslationOptionTerm> getTerms(
            ChatModelProperties.TranslationOptions translationOptions) {
        List<ChatModelProperties.TranslationOptionTerm> terms = translationOptions.getTerms();
        if (isNullOrEmpty(terms)) {
            return Collections.emptyList();
        }

        return terms.stream()
                .map(term -> QwenChatRequestParameters.TranslationOptionTerm.builder()
                        .source(term.getSource())
                        .target(term.getTarget())
                        .build())
                .collect(toList());
    }

    private static QwenChatRequestParameters.TranslationOptions getTranslationOptions(
            ChatModelProperties.Parameters parameters) {
        ChatModelProperties.TranslationOptions translationOptions = parameters.getTranslationOptions();
        if (translationOptions == null) {
            return null;
        }

        return QwenChatRequestParameters.TranslationOptions.builder()
                .sourceLang(translationOptions.getSourceLang())
                .targetLang(translationOptions.getTargetLang())
                .terms(getTerms(translationOptions))
                .tmLists(getTmList(translationOptions))
                .domains(translationOptions.getDomains())
                .build();
    }

    private static QwenChatRequestParameters.SearchOptions getSearchOption(ChatModelProperties.Parameters parameters) {
        ChatModelProperties.SearchOptions searchOptions = parameters.getSearchOptions();
        if (searchOptions == null) {
            return null;
        }

        return QwenChatRequestParameters.SearchOptions.builder()
                .enableSource(searchOptions.getEnableSource())
                .enableCitation(searchOptions.getEnableCitation())
                .citationFormat(searchOptions.getCitationFormat())
                .forcedSearch(searchOptions.getForcedSearch())
                .searchStrategy(searchOptions.getSearchStrategy())
                .build();
    }

    private static ResponseFormat getResponseFormat(ChatModelProperties.Parameters parameters) {
        ResponseFormatType responseFormatType = parameters.getResponseFormat();
        if (responseFormatType == null) {
            return null;
        }

        return ResponseFormat.builder().type(responseFormatType).build();
    }

    private QwenChatRequestParameters getParameters(ChatModelProperties properties) {
        ChatModelProperties.Parameters parameters = properties.getParameters();
        if (parameters == null) {
            return null;
        }

        return QwenChatRequestParameters.builder()
                .modelName(parameters.getModelName())
                .temperature(parameters.getTemperature())
                .topP(parameters.getTopP())
                .topK(parameters.getTopK())
                .frequencyPenalty(parameters.getFrequencyPenalty())
                .presencePenalty(parameters.getPresencePenalty())
                .maxOutputTokens(parameters.getMaxOutputTokens())
                .stopSequences(parameters.getStopSequences())
                .toolChoice(parameters.getToolChoice())
                .responseFormat(getResponseFormat(parameters))
                .seed(parameters.getSeed())
                .enableSearch(parameters.getEnableSearch())
                .searchOptions(getSearchOption(parameters))
                .translationOptions(getTranslationOptions(parameters))
                .vlHighResolutionImages(parameters.getVlHighResolutionImages())
                .isMultimodalModel(parameters.getIsMultimodalModel())
                .supportIncrementalOutput(parameters.getSupportIncrementalOutput())
                .enableThinking(parameters.getEnableThinking())
                .thinkingBudget(parameters.getThinkingBudget())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".chat-model.api-key")
    QwenChatModel qwenChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getChatModel();
        return QwenChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .topK(chatModelProperties.getTopK())
                .enableSearch(chatModelProperties.getEnableSearch())
                .seed(chatModelProperties.getSeed())
                .repetitionPenalty(chatModelProperties.getRepetitionPenalty())
                .temperature(chatModelProperties.getTemperature())
                .stops(chatModelProperties.getStops())
                .maxTokens(chatModelProperties.getMaxTokens())
                .defaultRequestParameters(getParameters(chatModelProperties))
                .isMultimodalModel(chatModelProperties.getIsMultimodalModel())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".streaming-chat-model.api-key")
    QwenStreamingChatModel qwenStreamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getStreamingChatModel();
        return QwenStreamingChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .topK(chatModelProperties.getTopK())
                .enableSearch(chatModelProperties.getEnableSearch())
                .seed(chatModelProperties.getSeed())
                .repetitionPenalty(chatModelProperties.getRepetitionPenalty())
                .temperature(chatModelProperties.getTemperature())
                .stops(chatModelProperties.getStops())
                .maxTokens(chatModelProperties.getMaxTokens())
                .defaultRequestParameters(getParameters(chatModelProperties))
                .isMultimodalModel(chatModelProperties.getIsMultimodalModel())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".language-model.api-key")
    QwenLanguageModel qwenLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getLanguageModel();
        return QwenLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .topP(languageModelProperties.getTopP())
                .topK(languageModelProperties.getTopK())
                .enableSearch(languageModelProperties.getEnableSearch())
                .seed(languageModelProperties.getSeed())
                .repetitionPenalty(languageModelProperties.getRepetitionPenalty())
                .temperature(languageModelProperties.getTemperature())
                .stops(languageModelProperties.getStops())
                .maxTokens(languageModelProperties.getMaxTokens())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".streaming-language-model.api-key")
    QwenStreamingLanguageModel qwenStreamingLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getStreamingLanguageModel();
        return QwenStreamingLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .topP(languageModelProperties.getTopP())
                .topK(languageModelProperties.getTopK())
                .enableSearch(languageModelProperties.getEnableSearch())
                .seed(languageModelProperties.getSeed())
                .repetitionPenalty(languageModelProperties.getRepetitionPenalty())
                .temperature(languageModelProperties.getTemperature())
                .stops(languageModelProperties.getStops())
                .maxTokens(languageModelProperties.getMaxTokens())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".embedding-model.api-key")
    QwenEmbeddingModel qwenEmbeddingModel(Properties properties) {
        EmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();
        return QwenEmbeddingModel.builder()
                .baseUrl(embeddingModelProperties.getBaseUrl())
                .apiKey(embeddingModelProperties.getApiKey())
                .modelName(embeddingModelProperties.getModelName())
                .dimension(embeddingModelProperties.getDimension())
                .build();
    }

    @Bean
    @ConditionalOnProperty(Properties.PREFIX + ".tokenizer.api-key")
    QwenTokenCountEstimator qwenTokenizer(Properties properties) {
        TokenizerProperties tokenizerProperties = properties.getTokenizer();
        return QwenTokenCountEstimator.builder()
                .apiKey(tokenizerProperties.getApiKey())
                .modelName(tokenizerProperties.getModelName())
                .build();
    }
}
