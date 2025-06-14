package dev.langchain4j.community.store.embedding.typesense;

import dev.langchain4j.community.store.embedding.typesense.exception.TypesenseException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.typesense.api.Client;
import org.typesense.model.CollectionResponse;
import org.typesense.model.DeleteDocumentsParameters;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.model.IndexAction;
import org.typesense.model.MultiSearchCollectionParameters;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchSearchesParameter;
import org.typesense.model.SearchParameters;
import org.typesense.model.SearchResult;
import org.typesense.model.SearchResultHit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a <a href="https://typesense.org/">Typesense</a> embedding store
 *
 * TODO: javadoc
 */
public class TypesenseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(TypesenseEmbeddingStore.class);

    private final Client client;
    private final TypesenseSchema schema;
    private final TypesenseMetadataFilterMapper metadataFilterMapper;

    public TypesenseEmbeddingStore(Client client, TypesenseSchema schema) {
        this.client = ensureNotNull(client, "client");
        this.schema = getOrDefault(schema, TypesenseSchema.builder().build());
        this.metadataFilterMapper = new TypesenseMetadataFilterMapper(schema);

        String collectionName = this.schema.getCollectionName();
        try {
            if (!collectionExist(collectionName)) {
                CollectionResponse response = client.collections().create(this.schema.getCollectionSchema());
                log.debug("Created collection {}, response {}", collectionName, response);
            }
        } catch (Exception e) {
            log.error("Error creating collection {}: {}", collectionName, e.getMessage());
            throw new TypesenseException(String.format("Error creating collection %s", collectionName), e);
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureNotEmpty(ids, "ids");
        ensureNotEmpty(embeddings, "embeddings");
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        List<Map<String, Object>> documents = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> document = new HashMap<>();
            document.put(schema.getIdFieldName(), ids.get(i));
            if (embedded != null) {
                TextSegment embeddedText = embedded.get(i);
                document.put(schema.getTextFieldName(), embeddedText.text());
                document.put(
                        schema.getMetadataFieldName(), embeddedText.metadata().toMap());
            }
            document.put(schema.getEmbeddingFieldName(), embeddings.get(i).vector());
            documents.add(document);
        }

        ImportDocumentsParameters importDocumentsParameters = new ImportDocumentsParameters();
        importDocumentsParameters.action(IndexAction.UPSERT);

        try {
            client.collections(schema.getCollectionName()).documents().import_(documents, importDocumentsParameters);
            log.info("Added {} documents", documents.size());
        } catch (Exception e) {
            log.error("Failed to add documents", e);
            throw new TypesenseException("Failed to add documents", e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters()
                .filterBy(schema.getIdFieldName() + ":=[" + String.join(",", ids) + "]");

        doRemove(deleteDocumentsParameters);
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        String expression = metadataFilterMapper.mapToFilter(filter);
        DeleteDocumentsParameters deleteDocumentsParameters = new DeleteDocumentsParameters()
                .filterBy(expression);

        doRemove(deleteDocumentsParameters);
    }

    @Override
    public void removeAll() {
        // Typesense need filter_by to remove
        SearchParameters searchParameters = new SearchParameters()
                .q("*")
                .queryBy(schema.getIdFieldName());

        List<String> ids = new ArrayList<>();
        try {
            // FIXME: paginate
            SearchResult searchResult = client.collections(schema.getCollectionName()).documents().search(searchParameters);
            Optional.ofNullable(searchResult.getHits())
                    .ifPresent(hit -> hit.stream()
                            .map(SearchResultHit::getDocument)
                            .forEach(document -> ids.add((String) document.get(schema.getIdFieldName())))
                    );
        } catch (Exception e) {
            log.error("Failed to retrieve ids during removeAll", e);
            throw new TypesenseException("Failed to retrieve ids during removeAll", e);
        }

        String filterBy = schema.getIdFieldName() + ":=[" + String.join(",", ids) + "]";
        doRemove(new DeleteDocumentsParameters().filterBy(filterBy));
    }

    @SuppressWarnings("unchecked")
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        String filterExpression = metadataFilterMapper.mapToFilter(request.filter());

        MultiSearchCollectionParameters multiSearchCollectionParameters = new MultiSearchCollectionParameters();
        multiSearchCollectionParameters.collection(schema.getCollectionName());
        multiSearchCollectionParameters.q("*");

        String vectorQuery = schema.getEmbeddingFieldName() + ":("
                + "["
                + String.join(
                ",",
                request.queryEmbedding().vectorAsList().stream()
                        .map(String::valueOf)
                        .toList()) + "], "
                + "k: " + request.maxResults() + ", "
                + "distance_threshold: " + 2 * (1 - request.minScore()) + ")";

        multiSearchCollectionParameters.vectorQuery(vectorQuery);
        if (filterExpression != null) {
            multiSearchCollectionParameters.filterBy(filterExpression);
        }
        MultiSearchSearchesParameter multiSearchesParameter =
                new MultiSearchSearchesParameter().addSearchesItem(multiSearchCollectionParameters);

        try {
            MultiSearchResult result = client.multiSearch.perform(
                    multiSearchesParameter, Map.of("query_by", schema.getEmbeddingFieldName()));

            List<EmbeddingMatch<TextSegment>> results = result.getResults().stream()
                    .map(item -> {
                        if (item.getCode() != null) {
                            log.error("Failed to search documents: {}", item.getError());
                            throw new TypesenseException("Failed to search documents: " + item.getError());
                        }

                        return item.getHits();
                    })
                    .filter(hits -> !isNullOrEmpty(hits))
                    .flatMap(hits -> hits.stream().map(hit -> {
                        Map<String, Object> rawDocument = hit.getDocument();
                        String id = rawDocument.get(schema.getIdFieldName()).toString();
                        List<Float> embedding = ((List<Double>) rawDocument.get(schema.getEmbeddingFieldName())).stream()
                                .map(Double::floatValue)
                                .toList();

                        TextSegment textSegment = null;
                        if (rawDocument.containsKey(schema.getTextFieldName())) {
                            Map<String, Object> metadata = (Map<String, Object>) rawDocument.get(schema.getMetadataFieldName());
                            textSegment = TextSegment.from((String) rawDocument.get(schema.getTextFieldName()), Metadata.from(metadata));
                        }

                        // Typesense vector_distance is a value between [0, 2] where 0 represents perfect match and 2
                        // represents extreme different
                        return new EmbeddingMatch<>(
                                1 - hit.getVectorDistance().doubleValue() / 2, id, Embedding.from(embedding), textSegment);
                    }))
                    .toList();

            log.info("Found {} documents", results.size());

            return new EmbeddingSearchResult<>(results);
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            throw new TypesenseException("Failed to search documents", e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private boolean collectionExist(String collectionName) throws Exception {
        CollectionResponse[] responses = client.collections().retrieve();

        return Arrays.stream(responses)
                .anyMatch(collection -> collection.getName().equals(collectionName));
    }

    private void doRemove(DeleteDocumentsParameters deleteDocumentsParameters) {
        try {
            Map<String, Object> res =
                    client.collections(schema.getCollectionName()).documents().delete(deleteDocumentsParameters);
            log.info("Remove {} documents", res.get("num_deleted"));
        } catch (Exception e) {
            log.error("Failed to remove documents", e);
            throw new TypesenseException("Failed to remove documents", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Client client;
        private TypesenseSchema schema;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder schema(TypesenseSchema schema) {
            this.schema = schema;
            return this;
        }

        public TypesenseEmbeddingStore build() {
            return new TypesenseEmbeddingStore(client, schema);
        }
    }
}
