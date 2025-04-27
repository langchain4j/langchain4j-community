package dev.langchain4j.community.model.dashscope;

import static com.alibaba.dashscope.embeddings.TextEmbeddingParam.TextType.DOCUMENT;
import static com.alibaba.dashscope.embeddings.TextEmbeddingParam.TextType.QUERY;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V1;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V2;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingOutput;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.community.model.dashscope.spi.QwenEmbeddingModelBuilderFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An implementation of an {@link dev.langchain4j.model.embedding.EmbeddingModel} that uses
 * <a href="https://www.alibabacloud.com/help/en/model-studio/text-embedding-synchronous-api">DashScope Embeddings API</a>.
 */
public class QwenEmbeddingModel extends DimensionAwareEmbeddingModel {

    public static final String TYPE_KEY = "type";
    public static final String TYPE_QUERY = "query";
    public static final String TYPE_DOCUMENT = "document";
    private static final int BATCH_SIZE = 10;
    // https://www.alibabacloud.com/help/en/model-studio/text-embedding-synchronous-api#853dfeccb97cd
    private static final List<Integer> SUPPORTED_DIMENSIONS = List.of(1024, 768, 512);

    private final String apiKey;
    private final String modelName;
    private final TextEmbedding embedding;
    private Consumer<TextEmbeddingParam.TextEmbeddingParamBuilder<?, ?>> textEmbeddingParamCustomizer = p -> {};

    public QwenEmbeddingModel(String baseUrl, String apiKey, String modelName, Integer dimension) {
        if (Utils.isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException(
                    "DashScope api key must be defined. Reference: https://www.alibabacloud.com/help/en/model-studio/get-api-key");
        }
        this.modelName = Utils.isNullOrBlank(modelName) ? QwenModelName.TEXT_EMBEDDING_V3 : modelName;
        this.apiKey = apiKey;
        this.dimension = ensureDimension(this.modelName, dimension);
        this.embedding = Utils.isNullOrBlank(baseUrl) ? new TextEmbedding() : new TextEmbedding(baseUrl);
    }

    private boolean containsDocuments(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.getString(TYPE_KEY))
                .anyMatch(TYPE_DOCUMENT::equalsIgnoreCase);
    }

    private boolean containsQueries(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::metadata)
                .map(metadata -> metadata.getString(TYPE_KEY))
                .anyMatch(TYPE_QUERY::equalsIgnoreCase);
    }

    private Response<List<Embedding>> embedTexts(List<TextSegment> textSegments, TextEmbeddingParam.TextType textType) {
        int size = textSegments.size();
        if (size < BATCH_SIZE) {
            return batchEmbedTexts(textSegments, textType);
        }

        List<Embedding> allEmbeddings = new ArrayList<>(size);
        TokenUsage allUsage = null;
        for (int i = 0; i < size; i += BATCH_SIZE) {
            List<TextSegment> batchTextSegments = textSegments.subList(i, Math.min(size, i + BATCH_SIZE));
            Response<List<Embedding>> batchResponse = batchEmbedTexts(batchTextSegments, textType);
            allEmbeddings.addAll(batchResponse.content());
            allUsage = TokenUsage.sum(allUsage, batchResponse.tokenUsage());
        }

        return Response.from(allEmbeddings, allUsage);
    }

    private Response<List<Embedding>> batchEmbedTexts(
            List<TextSegment> textSegments, TextEmbeddingParam.TextType textType) {
        TextEmbeddingParam.TextEmbeddingParamBuilder<?, ?> builder = TextEmbeddingParam.builder()
                .apiKey(apiKey)
                .model(modelName)
                .textType(textType)
                .texts(textSegments.stream().map(TextSegment::text).collect(toList()));

        if (dimension != null) {
            builder.parameter("dimension", dimension);
        }

        try {
            textEmbeddingParamCustomizer.accept(builder);
            TextEmbeddingResult generationResult = embedding.call(builder.build());
            // total_tokens are the same as input_tokens in the embedding model
            TokenUsage usage = new TokenUsage(generationResult.getUsage().getTotalTokens());
            List<Embedding> embeddings = Optional.of(generationResult)
                    .map(TextEmbeddingResult::getOutput)
                    .map(TextEmbeddingOutput::getEmbeddings)
                    .orElse(Collections.emptyList())
                    .stream()
                    .sorted(Comparator.comparing(TextEmbeddingResultItem::getTextIndex))
                    .map(TextEmbeddingResultItem::getEmbedding)
                    .map(doubleList ->
                            doubleList.stream().map(Double::floatValue).collect(toList()))
                    .map(Embedding::from)
                    .collect(toList());
            return Response.from(embeddings, usage);
        } catch (NoApiKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        boolean queries = containsQueries(textSegments);

        if (!queries) {
            // default all documents
            return embedTexts(textSegments, DOCUMENT);
        } else {
            boolean documents = containsDocuments(textSegments);
            if (!documents) {
                return embedTexts(textSegments, QUERY);
            } else {
                // This is a mixed collection of queries and documents. Embed one by one.
                List<Embedding> embeddings = new ArrayList<>(textSegments.size());
                Integer tokens = null;
                for (TextSegment textSegment : textSegments) {
                    Response<List<Embedding>> result;
                    if (TYPE_QUERY.equalsIgnoreCase(textSegment.metadata().getString(TYPE_KEY))) {
                        result = embedTexts(singletonList(textSegment), QUERY);
                    } else {
                        result = embedTexts(singletonList(textSegment), DOCUMENT);
                    }
                    embeddings.addAll(result.content());
                    if (result.tokenUsage() == null) {
                        continue;
                    }
                    if (tokens == null) {
                        tokens = result.tokenUsage().inputTokenCount();
                    } else {
                        tokens += result.tokenUsage().inputTokenCount();
                    }
                }
                return Response.from(embeddings, new TokenUsage(tokens));
            }
        }
    }

    public void setTextEmbeddingParamCustomizer(
            Consumer<TextEmbeddingParam.TextEmbeddingParamBuilder<?, ?>> textEmbeddingParamCustomizer) {
        this.textEmbeddingParamCustomizer = ensureNotNull(textEmbeddingParamCustomizer, "textEmbeddingParamCustomizer");
    }

    private static Integer ensureDimension(String modelName, Integer dimension) {
        if (dimension == null) {
            return null;
        }
        if (TEXT_EMBEDDING_V1.equals(modelName)
                || TEXT_EMBEDDING_V2.equals(modelName)
                || !SUPPORTED_DIMENSIONS.contains(dimension)) {
            throw new IllegalArgumentException("dimension '" + dimension + "' is not supported by " + modelName);
        }
        return dimension;
    }

    public static QwenEmbeddingModelBuilder builder() {
        for (QwenEmbeddingModelBuilderFactory factory : loadFactories(QwenEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QwenEmbeddingModelBuilder();
    }

    public static class QwenEmbeddingModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer dimension;

        public QwenEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QwenEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QwenEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QwenEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QwenEmbeddingModelBuilder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public QwenEmbeddingModel build() {
            return new QwenEmbeddingModel(baseUrl, apiKey, modelName, dimension);
        }
    }
}
