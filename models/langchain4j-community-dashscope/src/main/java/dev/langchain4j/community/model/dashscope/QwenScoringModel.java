package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.GTE_RERANK_V2;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import dev.langchain4j.community.model.dashscope.spi.QwenScoringModelBuilderFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * An implementation of a {@link ScoringModel} that uses the
 * <a href="https://www.alibabacloud.com/help/en/model-studio/text-rerank-api">DashScope Text Rerank API</a>.
 */
public class QwenScoringModel implements ScoringModel {

    private final String apiKey;
    private final String modelName;
    private final Integer topN;
    private final Boolean returnDocuments;
    private final String instruct;
    private final TextReRank textReRank;
    private Consumer<TextReRankParam.TextReRankParamBuilder<?, ?>> textReRankParamCustomizer = p -> {};

    public QwenScoringModel(String baseUrl, String apiKey, String modelName, Integer topN, Boolean returnDocuments) {
        this(baseUrl, apiKey, modelName, topN, returnDocuments, null);
    }

    public QwenScoringModel(
            String baseUrl, String apiKey, String modelName, Integer topN, Boolean returnDocuments, String instruct) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. Reference: https://www.alibabacloud.com/help/en/model-studio/get-api-key");
        }
        this.apiKey = apiKey;
        this.modelName = isNullOrBlank(modelName) ? GTE_RERANK_V2 : modelName;
        this.topN = topN;
        this.returnDocuments = returnDocuments;
        this.instruct = instruct;
        this.textReRank = isNullOrBlank(baseUrl) ? new TextReRank() : new TextReRank(Protocol.HTTP.getValue(), baseUrl);
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        TextReRankParam.TextReRankParamBuilder<?, ?> builder = TextReRankParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .query(query)
                .documents(segments.stream().map(TextSegment::text).collect(toList()));

        if (topN != null) {
            builder.topN(topN);
        }
        if (returnDocuments != null) {
            builder.returnDocuments(returnDocuments);
        }
        if (instruct != null) {
            builder.instruct(instruct);
        }

        textReRankParamCustomizer.accept(builder);

        TextReRankResult result;
        try {
            result = textReRank.call(builder.build());
        } catch (NoApiKeyException e) {
            throw new IllegalArgumentException(e);
        } catch (ApiException | InputRequiredException e) {
            throw new RuntimeException(e);
        }

        List<Double> scores = result.getOutput().getResults().stream()
                .sorted(Comparator.comparing(TextReRankOutput.Result::getIndex))
                .map(TextReRankOutput.Result::getRelevanceScore)
                .collect(toList());

        TokenUsage tokenUsage = null;
        if (result.getUsage() != null) {
            tokenUsage = new TokenUsage(result.getUsage().getTotalTokens());
        }

        return Response.from(scores, tokenUsage);
    }

    public void setTextReRankParamCustomizer(
            Consumer<TextReRankParam.TextReRankParamBuilder<?, ?>> textReRankParamCustomizer) {
        this.textReRankParamCustomizer = ensureNotNull(textReRankParamCustomizer, "textReRankParamCustomizer");
    }

    public static QwenScoringModelBuilder builder() {
        for (QwenScoringModelBuilderFactory factory : loadFactories(QwenScoringModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenScoringModelBuilder();
    }

    public static class QwenScoringModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer topN;
        private Boolean returnDocuments;
        private String instruct;

        public QwenScoringModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenScoringModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenScoringModelBuilder topN(Integer topN) {
            this.topN = topN;
            return this;
        }

        public QwenScoringModelBuilder returnDocuments(Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
            return this;
        }

        public QwenScoringModelBuilder instruct(String instruct) {
            this.instruct = instruct;
            return this;
        }

        public QwenScoringModel build() {
            return new QwenScoringModel(baseUrl, apiKey, modelName, topN, returnDocuments, instruct);
        }
    }
}
