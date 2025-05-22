package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.store.embedding.ParentChildEmbeddingStoreIngestor;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * An ingestor class that processes documents and stores their vector embeddings in a Neo4j database.
 * <p>
 * This class performs a multi-stage transformation pipeline: it transforms documents, splits them into
 * segments, optionally applies additional transformations to child segments, generates embeddings, and
 * stores both the parent-child relationships and embeddings in Neo4j.
 * <p>
 * It also supports using a {@link ChatModel} to manipulate the content of parent segments using a system
 * and user prompt for advanced semantic transformations.
 * </p>
 *
 * <p><b>Constructor Parameters:</b></p>
 * <ul>
 *     <li><b>documentTransformer</b> – the {@link DocumentTransformer} used to initially transform the input documents.</li>
 *     <li><b>documentSplitter</b> – the {@link DocumentSplitter} used to split documents into parent segments.</li>
 *     <li><b>textSegmentTransformer</b> – the {@link TextSegmentTransformer} applied to the parent text segments.</li>
 *     <li><b>childTextSegmentTransformer</b> – the {@link TextSegmentTransformer} applied to child text segments.</li>
 *     <li><b>embeddingModel</b> – the {@link EmbeddingModel} used to generate vector embeddings for segments.</li>
 *     <li><b>embeddingStore</b> – the {@link Neo4jEmbeddingStore} used to store the embeddings in the Neo4j database.</li>
 *     <li><b>documentChildSplitter</b> – the {@link DocumentSplitter} used to generate child segments from parent segments.</li>
 *     <li><b>driver</b> – the {@link Driver} used to execute Cypher queries against the Neo4j database.</li>
 *     <li><b>query</b> – a Cypher query template used for storing the processed segment data in Neo4j.</li>
 *     <li><b>parentIdKey</b> – the metadata key representing the parent segment's ID; if not present, a UUID is used.</li>
 *     <li><b>params</b> – additional query parameters to be included when executing the Cypher query.</li>
 *     <li><b>systemPrompt</b> – a system prompt for the {@link ChatModel} to guide transformation of parent text segments; ignored if {@code questionModel} is {@code null}.</li>
 *     <li><b>userPrompt</b> – a user prompt for the {@link ChatModel} to guide transformation of parent text segments; ignored if {@code questionModel} is {@code null}.</li>
 *     <li><b>questionModel</b> – a {@link ChatModel} used to manipulate the text of parent segments using the given prompts.</li>
 * </ul>
 */
public class Neo4jEmbeddingStoreIngestor extends ParentChildEmbeddingStoreIngestor {
    public static final String DEFAULT_PARENT_ID_KEY = "parentId";

    protected final Driver driver;
    protected final String query;
    protected final String parentIdKey;
    protected final Map<String, Object> params;
    protected Neo4jEmbeddingStore neo4jEmbeddingStore;
    protected final String userPrompt;
    protected final String systemPrompt;
    protected final ChatModel questionModel;

    /**
     * Constructs a new {@code Neo4jEmbeddingStoreIngestor}, which processes documents through a transformation
     * and embedding pipeline and stores the results in a Neo4j database. This includes:
     * <ul>
     *   <li>Document transformation</li>
     *   <li>Parent and child document splitting</li>
     *   <li>Optional manipulation of parent text segments using a {@link ChatModel}</li>
     *   <li>Embedding generation and storage of segments into Neo4j</li>
     * </ul>
     *
     * @param documentTransformer The {@link DocumentTransformer} applied to the original documents.
     * @param documentSplitter The {@link DocumentSplitter} used to split documents into parent segments.
     * @param textSegmentTransformer The {@link TextSegmentTransformer} applied to parent segments.
     * @param childTextSegmentTransformer The {@link TextSegmentTransformer} applied to child segments.
     * @param embeddingModel The {@link EmbeddingModel} used to generate embeddings from text segments.
     * @param embeddingStore The {@link EmbeddingStore} (specifically {@link Neo4jEmbeddingStore}) used to persist embeddings.
     * @param documentChildSplitter The {@link DocumentSplitter} used to generate child segments from parent segments.
     * @param driver The {@link Driver} used to execute Cypher queries against the Neo4j database.
     * @param query The Cypher query used to insert processed segments and metadata into Neo4j.
     * @param parentIdKey The metadata key used to extract the parent segment ID; if absent, a UUID will be generated.
     * @param params Additional query parameters to include in the Cypher execution, beyond segment metadata and text.
     * @param systemPrompt A system prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
     * @param userPrompt A user prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
     * @param questionModel A {@link ChatModel} used to further transform parent segment text based on provided prompts. If {@code null}, no chat-based manipulation occurs.
     */
    public Neo4jEmbeddingStoreIngestor(
            final DocumentTransformer documentTransformer,
            final DocumentSplitter documentSplitter,
            TextSegmentTransformer textSegmentTransformer,
            TextSegmentTransformer childTextSegmentTransformer,
            final EmbeddingModel embeddingModel,
            final Neo4jEmbeddingStore embeddingStore,
            final DocumentSplitter documentChildSplitter,
            Driver driver,
            String query,
            String parentIdKey,
            Map<String, Object> params,
            String systemPrompt,
            String userPrompt,
            ChatModel questionModel) {
        super(
                documentTransformer,
                documentSplitter,
                textSegmentTransformer,
                childTextSegmentTransformer,
                embeddingModel,
                embeddingStore,
                documentChildSplitter);
        this.neo4jEmbeddingStore = embeddingStore;
        this.driver = ensureNotNull(driver, "driver");
        this.query = ensureNotNull(query, "query");
        this.params = copy(params);
        this.parentIdKey = getOrDefault(parentIdKey, DEFAULT_PARENT_ID_KEY);

        super.textSegmentTransformer = getOrDefault(textSegmentTransformer, getTextSegmentTransformer());
        super.childTextSegmentTransformer =
                getOrDefault(childTextSegmentTransformer, getDefaultChildTextSegmentTransformer());
        this.userPrompt = userPrompt;
        this.systemPrompt = systemPrompt;
        this.questionModel = questionModel;
    }

    private TextSegmentTransformer getTextSegmentTransformer() {
        return segment -> {
            TextSegment parentSegment = getTextSegmentWithUniqueId(segment, neo4jEmbeddingStore.getIdProperty(), null);
            String textInput = parentSegment.text();
            final Map<String, Object> metadataMap = segment.metadata().toMap();
            String parentId = "parent_" + UUID.randomUUID();
            metadataMap.put(parentIdKey, parentId);
            String text;
            if (this.questionModel != null) {
                if (systemPrompt == null || userPrompt == null) {
                    throw new RuntimeException(
                            "Prompts cannot be null: systemPrompt=" + systemPrompt + ", userPrompt=" + userPrompt);
                }
                final SystemMessage systemMessage = Prompt.from(systemPrompt).toSystemMessage();

                final PromptTemplate userTemplate = PromptTemplate.from(userPrompt);

                final UserMessage userMessage =
                        userTemplate.apply(Map.of("input", textInput)).toUserMessage();

                final List<ChatMessage> chatMessages = List.of(systemMessage, userMessage);

                text = this.questionModel.chat(chatMessages).aiMessage().text();
            } else {
                text = textInput;
            }
            metadataMap.putIfAbsent("text", text);

            final Map<String, Object> params = new HashMap<>(Map.of("metadata", metadataMap));
            params.put(parentIdKey, parentId);
            getAdditionalParams(parentId);
            metadataMap.putAll(this.params);
            try (Session session = driver.session()) {
                session.run(this.query, params);
            }

            return segment;
        };
    }

    private TextSegmentTransformer getDefaultChildTextSegmentTransformer() {
        return segment -> getTextSegmentWithUniqueId(segment, neo4jEmbeddingStore.getIdProperty(), parentIdKey);
    }

    private void getAdditionalParams(String parentId) {
        final Map<String, Object> params = new HashMap<>(this.params);
        params.put(parentIdKey, parentId);
        neo4jEmbeddingStore.setAdditionalParams(params);
    }

    public static TextSegment getTextSegmentWithUniqueId(TextSegment segment, String idProperty, String parentId) {
        final Metadata metadata1 = segment.metadata();
        final Object idMeta = metadata1.toMap().get(idProperty);
        String value = parentId == null ? randomUUID() : parentId + "_" + randomUUID();
        if (idMeta != null) {
            value = idMeta + "_" + value;
        }
        metadata1.put(idProperty, value);

        return segment;
    }

    public static Neo4jEmbeddingStoreIngestor.Builder builder() {
        return new Neo4jEmbeddingStoreIngestor.Builder();
    }

    public static class Builder extends ParentChildEmbeddingStoreIngestor.Builder<Builder> {
        protected Driver driver;
        protected String query;
        protected String parentIdKey;
        protected Map<String, Object> params;
        protected String systemPrompt;
        protected String userPrompt;
        protected ChatModel questionModel;

        /**
         * Creates a new EmbeddingStoreIngestor builder.
         */
        public Builder() {}

        /**
         * @param driver The {@link Driver} used to execute Cypher queries against the Neo4j database.
         */
        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        /**
         * @param query The Cypher query used to insert processed segments and metadata into Neo4j.
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * @param parentIdKey The metadata key used to extract the parent segment ID; if absent, a UUID will be generated.
         */
        public Builder parentIdKey(String parentIdKey) {
            this.parentIdKey = parentIdKey;
            return this;
        }

        /**
         * @param params Additional query parameters to include in the Cypher execution, beyond segment metadata and text.
         */
        public Builder params(Map<String, Object> params) {
            this.params = params;
            return self();
        }

        /**
         * @param systemPrompt A system prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return self();
        }

        /**
         * @param userPrompt A user prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
         */
        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return self();
        }

        /**
         * @param questionModel A {@link ChatModel} used to further transform parent segment text based on provided prompts. If {@code null}, no chat-based manipulation occurs.
         */
        public Builder questionModel(ChatModel questionModel) {
            this.questionModel = questionModel;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Neo4jEmbeddingStoreIngestor build() {
            return new Neo4jEmbeddingStoreIngestor(
                    documentTransformer,
                    documentSplitter,
                    textSegmentTransformer,
                    childTextSegmentTransformer,
                    embeddingModel,
                    (Neo4jEmbeddingStore) embeddingStore,
                    documentChildSplitter,
                    driver,
                    query,
                    parentIdKey,
                    params,
                    systemPrompt,
                    userPrompt,
                    questionModel);
        }
    }
}
