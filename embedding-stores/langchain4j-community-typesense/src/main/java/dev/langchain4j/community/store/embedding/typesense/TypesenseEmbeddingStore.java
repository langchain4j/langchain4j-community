package dev.langchain4j.community.store.embedding.typesense;

import static dev.langchain4j.community.store.embedding.typesense.TypesenseMetadataFilterMapper.fromMetadataFieldName;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.CosineSimilarity.fromRelevanceScore;
import static dev.langchain4j.store.embedding.RelevanceScore.fromCosineSimilarity;
import static java.util.Collections.singletonList;
import static org.typesense.model.IndexAction.CREATE;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.typesense.api.Client;
import org.typesense.api.FieldTypes;
import org.typesense.model.CollectionSchema;
import org.typesense.model.DeleteDocumentsParameters;
import org.typesense.model.Field;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.model.MultiSearchCollectionParameters;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchSearchesParameter;

/**
 * <p>
 * An <code>EmbeddingStore</code> which uses AI Vector Search capabilities of Typesense. This embedding store
 * supports metadata filtering and removal.
 * </p><p>
 * Instances of this store are created by configuring a builder:
 * </p><pre>{@code
 * EmbeddingStore<TextSegment> example(Client client) {
 *   return TypesenseEmbeddingStore.builder()
 *     .client(client)
 *     .settings(TypesenseSettings.builder()
 *             .url("http://localhost:8123")
 *             .dimension(embeddingModel.dimension())
 *             .build())
 *     .build();
 * }
 * }</pre><p>
 * It is required to configure a {@link Client} in order to connect and authorize to Typesense.
 * </p><p>
 * It is recommended to configure a {@link TypesenseSettings} in order to customize field name to match your application semantics.
 * </p>
 */
public class TypesenseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(TypesenseEmbeddingStore.class);

    private final Client client;
    private final TypesenseSettings settings;
    private final TypesenseMetadataFilterMapper metadataFilterMapper;

    public TypesenseEmbeddingStore(Client client, TypesenseSettings settings) {
        this.client = client;
        this.settings = getOrDefault(settings, new TypesenseSettings());
        this.metadataFilterMapper = fromMetadataFieldName(settings.metadataFieldName());

        if (!isCollectionExist()) {
            createCollection();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private TypesenseSettings settings;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder settings(TypesenseSettings settings) {
            this.settings = settings;
            return this;
        }

        public TypesenseEmbeddingStore build() {
            return new TypesenseEmbeddingStore(client, settings);
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.warn("TypesenseEmbeddingStore do not add empty embeddings to Typesense");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");
        int length = ids.size();

        List<Map<String, Object>> documents = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            documents.add(toDocument(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i)));
        }

        ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
        importDocumentsParameters.action(CREATE);

        try {
            String result = client.collections(settings.collectionName())
                    .documents()
                    .import_(documents, importDocumentsParameters);

            log.info(result);
        } catch (Exception e) {
            log.error("Failed to add documents", e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        // https://typesense.org/docs/27.1/api/vector-search.html#nearest-neighbor-vector-search
        String vectorQuery = "%s:([%s], k:%d, distance_threshold:%f)"
                .formatted(
                        settings.embeddingFieldName(),
                        request.queryEmbedding().vectorAsList().stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(",")),
                        request.maxResults(),
                        1 - fromRelevanceScore(request.minScore()));
        MultiSearchCollectionParameters parameters =
                new MultiSearchCollectionParameters().collection(settings.collectionName());
        parameters.q("*");
        parameters.vectorQuery(vectorQuery);
        if (request.filter() != null) {
            // TODO: support case sensitive filter: https://github.com/typesense/typesense/issues/1546
            parameters.filterBy(metadataFilterMapper.map(request.filter()));
        }

        MultiSearchSearchesParameter multiSearchesParameter =
                new MultiSearchSearchesParameter().addSearchesItem(parameters);

        try {
            MultiSearchResult result = client.multiSearch.perform(
                    multiSearchesParameter, Map.of("query_by", settings.embeddingFieldName()));

            return new EmbeddingSearchResult<>(toMatches(result));
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
        deleteDocumentsParameters.filterBy("%s := [%s]".formatted(settings.idFieldName(), String.join(",", ids)));

        try {
            client.collections(settings.collectionName()).documents().delete(deleteDocumentsParameters);
        } catch (Exception e) {
            log.error("Failed to delete documents", e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters();
        deleteDocumentsParameters.filterBy(metadataFilterMapper.map(filter));

        try {
            client.collections(settings.collectionName()).documents().delete(deleteDocumentsParameters);
        } catch (Exception e) {
            log.error("Failed to delete documents", e);
        }
    }

    @Override
    public void removeAll() {
        // To delete all documents in a collection, you can use a filter that matches all documents in your collection.
        // For eg, if you have an int32 field called popularity in your documents, you can use filter_by=popularity:>0
        // to delete all documents.
        // Or if you have a bool field called in_stock in your documents, you can use filter_by=in_stock:[true,false] to
        // delete all documents.
        EmbeddingStore.super.removeAll();
    }

    private boolean isCollectionExist() {
        try {
            client.collections(settings.collectionName()).retrieve();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createCollection() {
        ensureNotNull(settings.dimension(), "dimension");
        CollectionSchema collectionSchema = new CollectionSchema();

        collectionSchema
                .name(settings.collectionName())
                .addFieldsItem(new Field()
                        .name(settings.idFieldName())
                        .type(FieldTypes.STRING)
                        .optional(false))
                .addFieldsItem(new Field()
                        .name(settings.textFieldName())
                        .type(FieldTypes.STRING)
                        .optional(true))
                .addFieldsItem(new Field()
                        .name(settings.metadataFieldName())
                        .type(FieldTypes.OBJECT)
                        .optional(true))
                .addFieldsItem(new Field()
                        .name(settings.embeddingFieldName())
                        .type(FieldTypes.FLOAT_ARRAY)
                        .numDim(settings.dimension())
                        .optional(false))
                .enableNestedFields(true);

        try {
            client.collections().create(collectionSchema);
        } catch (Exception e) {
            log.error("Failed to create collection {}", settings.collectionName(), e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private Map<String, Object> toDocument(String id, Embedding embedding, @Nullable TextSegment embedded) {
        Map<String, Object> doc = new HashMap<>();
        doc.put(settings.idFieldName(), id);
        doc.put(settings.embeddingFieldName(), embedding.vector());
        doc.put(
                settings.textFieldName(),
                Optional.ofNullable(embedded).map(TextSegment::text).orElse(null));
        doc.put(
                settings.metadataFieldName(),
                Optional.ofNullable(embedded)
                        .map(TextSegment::metadata)
                        .map(Metadata::toMap)
                        .orElse(null));

        return doc;
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> toMatches(MultiSearchResult result) {
        if (result == null || isNullOrEmpty(result.getResults())) {
            return List.of();
        }
        return result.getResults().stream()
                .flatMap(r -> {
                    if (r == null || isNullOrEmpty(r.getHits())) {
                        return Stream.of();
                    }
                    return r.getHits().stream().map(hit -> {
                        Map<String, Object> document = hit.getDocument();
                        // Typesense uses the cosine similarity, so this distance will be a value between 0 and 2
                        // where 0 means relevant and 2 means not relevant
                        double score = fromCosineSimilarity((1 - hit.getVectorDistance()));
                        String id = String.valueOf(document.get(settings.idFieldName()));
                        List<Double> embeddingResult = (List<Double>) document.get(settings.embeddingFieldName());
                        Embedding embedding = Embedding.from(
                                embeddingResult.stream().map(Double::floatValue).toList());
                        TextSegment textSegment = null;
                        if (document.get(settings.textFieldName()) != null) {
                            String text = String.valueOf(document.get(settings.textFieldName()));
                            Metadata metadata = document.get(settings.metadataFieldName()) == null
                                    ? null
                                    : Metadata.from((Map<String, ?>) document.get(settings.metadataFieldName()));
                            textSegment = metadata == null ? TextSegment.from(text) : TextSegment.from(text, metadata);
                        }

                        return new EmbeddingMatch<>(score, id, embedding, textSegment);
                    });
                })
                .toList();
    }
}
