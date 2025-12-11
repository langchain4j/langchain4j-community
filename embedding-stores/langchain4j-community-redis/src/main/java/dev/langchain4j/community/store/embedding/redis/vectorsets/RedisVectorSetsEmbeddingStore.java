package dev.langchain4j.community.store.embedding.redis.vectorsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.VAddParams;
import redis.clients.jedis.params.VSimParams;
import redis.clients.jedis.resps.VSimScoreAttribs;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RedisVectorSetsEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final String ATTRIBUTES_TEXT_KEY = "$embedding-store-redis-vector-set-text";

    private static final Logger log = LoggerFactory.getLogger(RedisVectorSetsEmbeddingStore.class);

    private final UnifiedJedis client;

    private final String key;
    private final SimilarityFilterMapper filterMapper;
    private final ObjectMapper objectMapper;
    private final Function<TextSegment, Optional<String>> metadataSerializer;
    private final Supplier<VAddParams> addParamsSupplier;

    public RedisVectorSetsEmbeddingStore(UnifiedJedis client, String key, SimilarityFilterMapper filterMapper, final Function<TextSegment, Optional<String>> metadataSerializer, final Supplier<VAddParams> addParamsSupplier) {
        this.client = client;
        this.key = key;
        this.filterMapper = filterMapper;
        this.objectMapper = new ObjectMapper();
        this.metadataSerializer = Optional.ofNullable(metadataSerializer)
                .orElse(m -> {
                if (m == null) return Optional.empty();

                try {
                    var toJson = Optional.of(m).map(TextSegment::metadata).map(Metadata::toMap).orElseGet(Map::of);
                    toJson.putIfAbsent(ATTRIBUTES_TEXT_KEY, m.text());


                    return Optional.of(objectMapper.writeValueAsString(toJson));
                } catch (JsonProcessingException e) {
                    log.warn("Unable to transform value {} into a json.", m, e);
                    return Optional.empty();
                }
            });

        this.addParamsSupplier =  Optional.ofNullable(addParamsSupplier)
                .orElse(VAddParams::new);
    }

    public RedisVectorSetsEmbeddingStore(UnifiedJedis client, String key) { this(client, key, new SimilarityFilterMapper(), null, null); }

    @Override
    public String add(Embedding embedding) {
        var id = Utils.randomUUID();
        add(id, embedding);

        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addAll(List.of(id), List.of(embedding), List.of());
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        var result = addAll(List.of(embedding), List.of(textSegment));

        return Optional.ofNullable(result)
                .filter(e -> !e.isEmpty())
                .map(e -> e.get(0))
                .orElseThrow();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, List.of());
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings == null) return;

        IntFunction<Optional<Entry>> embeddingToVectorSetEntry = i -> {
            /* Return a default id if it wasn't passed as input. */
            var id = getElementAtIndex(ids, i).orElseGet(Utils::randomUUID);

            var embedding = getElementAtIndex(embeddings, i);
            var text = getElementAtIndex(embedded, i);

            return embedding
                    .map(e -> new Entry(id, e, text))
                    .or(() -> {
                        log.warn("Skipping element index: {} since embedding is null.", i);
                        return Optional.empty();
                    });
        };

        var inputElementSize = embeddings.size();
        var pipelineToAddEmbeddingsToTheVector = IntStream.range(0, inputElementSize)
                .mapToObj(embeddingToVectorSetEntry)
                .flatMap(Optional::stream)
                .map(this::addToTheVectorSet)
                .flatMap(Optional::stream);

        var result = pipelineToAddEmbeddingsToTheVector
                .filter(EntryResult::ok)
                /* here it's actually executed the pipeline */
                .toList();

        log.debug("[key: {}] Successfully added {}/{} elements.", this.key, result.size(), inputElementSize);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        var vector = Optional.ofNullable(request)
                .map(EmbeddingSearchRequest::queryEmbedding)
                .map(Embedding::vector)
                .orElse(null);

        if (vector == null) return new EmbeddingSearchResult<>(List.of());

        var params = this.mapToVSimParams(request);
        var similarElements = client.vsimWithScoresAndAttribs(this.key, vector, params);

        List<EmbeddingMatch<TextSegment>> matches = similarElements
                .entrySet()
                .stream()
                .map(this::mapToEmbeddingMatch)
                .flatMap(Optional::stream)
                .toList();

        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll() {
        client.unlink(key);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("ids cannot be null or empty");

        Stream<Boolean> pipelineToRemoveIdsFromTheVectorSet = ids
                .stream()
                .map(id -> {
                    var result = client.vrem(key, id);

                    if (!result) {
                        log.warn("[key: {}] Id [{}] not removed from the key.", this.key, id);
                    }

                    return result;
                });

        var executeAndKeepOnlySuccessful = pipelineToRemoveIdsFromTheVectorSet
                .filter(Boolean.TRUE::equals)
                /* here it's actually executed the pipeline */
                .toList();

        log.debug("[key: {}] Successfully removed {}/{} elements.", this.key, executeAndKeepOnlySuccessful.size(), ids.size());
    }

    @Override
    public void remove(String id) {
        ValidationUtils.ensureNotBlank(id, "id");
        removeAll(List.of(id));
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        var ids = Optional.ofNullable(embeddings)
                .orElseGet(List::of)
                .stream()
                .map(ignore -> Utils.randomUUID())
                .toList();

        addAll(ids, embeddings, embedded);
        return ids;
    }

    private Optional<EmbeddingMatch<TextSegment>> mapToEmbeddingMatch(Map.Entry<String, VSimScoreAttribs> e) {
        if (e == null) return Optional.empty();
        if (e.getKey() == null) return Optional.empty();
        if (e.getValue() == null) return Optional.empty();

        Function<String, List<Float>> fetchEmbeddingsById = id -> client.vemb(key, id)
                .stream()
                .map(Double::floatValue)
                .toList();

        var id = e.getKey();
        var value = e.getValue();

        var embedding = fetchEmbeddingsById.apply(id);

        TextSegment embedded = Optional.of(value)
                .map(VSimScoreAttribs::getAttributes)
                .map(attrs -> {
                    TypeReference<Map<String, Object>> type = new TypeReference<>() {};
                    try {
                        var map = objectMapper.readValue(attrs, type);
                        String text = (String) map.get(ATTRIBUTES_TEXT_KEY);
                        map.remove(ATTRIBUTES_TEXT_KEY);

                        return TextSegment.from(text, Metadata.from(map));
                    } catch (JsonProcessingException ex) {
                        log.warn("Unable to parse value: {}", attrs, ex);
                        return null;
                    }
                })
                .orElse(null);

        return Optional.of(value)
                .map(VSimScoreAttribs::getScore)
                .map(score -> new EmbeddingMatch<>(
                        score,
                        id,
                        Embedding.from(embedding),
                        embedded
                ));
    };

    VSimParams mapToVSimParams(EmbeddingSearchRequest r) {
        var count = Optional.of(r)
                .map(EmbeddingSearchRequest::maxResults)
                .orElse(10);

        var commandParams = new VSimParams()
                .count(count);

        double minScore = Optional.of(r)
                .map(EmbeddingSearchRequest::minScore)
                .orElse(0D);

        if (minScore > 0 && minScore < 1) {
            commandParams.epsilon(1 - minScore);
        }

        /* Maps request's filters into VSIM FILTER expression and add it to params. */
        Optional.of(r)
                .map(EmbeddingSearchRequest::filter)
                .flatMap(filterMapper::from)
                .ifPresent(commandParams::filter);

        return commandParams;
    }

    private Optional<EntryResult> addToTheVectorSet(Entry record) {
        if (record == null) return Optional.empty();

        var params = this.addParamsSupplier.get();

        var attr = Optional.ofNullable(metadataSerializer)
                .flatMap(serialize -> record.embedded().flatMap(serialize))
                .or(() -> record.embedded().map(TextSegment::text));
        attr.ifPresent(params::setAttr);

        var result = client.vadd(key,
                record.embedding().vector(),
                record.id(),
                params);

        if (!result) {
            log.warn("[key: {}] Record [{}] not added to the key.", this.key, record);
        }

        return Optional.of(new EntryResult(record, result));
    }

    private <E> Optional<E> getElementAtIndex(List<E> list, int i) {
        if (list == null) return Optional.empty();

        try {
            return Optional.ofNullable(list.get(i));
        } catch (IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    private record Entry(String id, Embedding embedding, Optional<TextSegment> embedded) { }

    private record EntryResult(Entry entry, boolean ok) { }
}
