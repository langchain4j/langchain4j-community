package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;


public class Neo4jEmbeddingRetriever implements ContentRetriever {
    public static final String DEFAULT_PROMPT_ANSWER = """
            You are an assistant that helps to form nice and human
            understandable answers based on the provided information from tools.
            Do not add any other information that wasn't present in the tools, and use
            very concise style in interpreting results.
            
            If you don't know the answer, just say that you don't know, don't try to make up an answer.
            """;
    public static final String DEFAULT_PARENT_ID_KEY = "parentId";
    protected final EmbeddingModel embeddingModel;
    protected final ChatLanguageModel questionModel;
    protected final ChatLanguageModel answerModel;
    protected final String promptSystem;
    protected final String promptUser;
    protected final String promptAnswer;
    protected final Driver driver;
    protected final Integer maxResults;
    protected final Double minScore;
    protected final Neo4jEmbeddingStore embeddingStore;
    protected final String query;
    protected final String parentIdKey;
    protected final Map<String, Object> params;

    /**
     * Creates an instance of Neo4jEmbeddingRetriever
     * 
     * TODO
     * 
     */
    public Neo4jEmbeddingRetriever(EmbeddingModel embeddingModel,
                                   Driver driver,
                                   int maxResults,
                                   double minScore,
                                   String query,
                                   Map<String, Object> params,
                                   Neo4jEmbeddingStore embeddingStore,
                                   ChatLanguageModel questionModel,
                                   String promptSystem,
                                   String promptUser,
                                   ChatLanguageModel answerModel,
                                   String promptAnswer,
                                   String parentIdKey) {
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        final Neo4jEmbeddingStore store = getOrDefault(embeddingStore, getDefaultEmbeddingStore(driver));
        this.embeddingStore = ensureNotNull(store, "embeddingStore");
        
        this.driver = driver;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.query = query;
        this.params = params;
        this.questionModel = questionModel;
        this.answerModel = answerModel;
        this.promptSystem = promptSystem;
        this.promptAnswer = getOrDefault(promptAnswer, DEFAULT_PROMPT_ANSWER);
        this.parentIdKey = getOrDefault(parentIdKey, DEFAULT_PARENT_ID_KEY);
        this.promptUser = promptUser;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return null;
    }

    public void index(Document document,
                      DocumentSplitter parentSplitter) {
        index(document, parentSplitter, null);
    }

    /**
     * 
     * @param document the document to be split
     * @param parentSplitter main splitter
     * @param childSplitter sub-splitter to be used with ParentChildRetriever or with a custom retriever
     */
    public void index(Document document,
                      DocumentSplitter parentSplitter,
                      DocumentSplitter childSplitter) {

        List<TextSegment> parentSegments = parentSplitter.split(document);

            for (int i = 0; i < parentSegments.size(); i++) {

                TextSegment parentSegment = parentSegments.get(i);
                String parentId = "parent_" + i;
                parentSegment = getTextSegmentWithUniqueId(parentSegment, embeddingStore.getIdProperty(), null);

                // Store parent node
                final Metadata metadata = document.metadata();
                
                if (this.query != null) {
                    final Map<String, Object> metadataMap = metadata.toMap();
                    metadataMap.put(parentIdKey, parentId);
                    
                    String textInput =  parentSegment.text();
                    String text;

                    if (this.questionModel != null) {
                        if (promptSystem == null || promptUser == null) {
                            throw new RuntimeException("");
                        }
                        final SystemMessage systemMessage = Prompt.from(promptSystem).toSystemMessage();

                        final PromptTemplate userTemplate = PromptTemplate.from(promptUser);

                        final UserMessage userMessage = userTemplate.apply(Map.of("input", textInput)).toUserMessage();

                        final List<ChatMessage> chatMessages = List.of(systemMessage, userMessage);

                        text = this.questionModel.chat(chatMessages).aiMessage().text();
                    } else {
                        text = textInput;
                    }
                    metadataMap.putIfAbsent("text", text);
                    metadataMap.putIfAbsent("title", "Untitled");
                    final Map<String, Object> params = Map.of("metadata", metadataMap);
                    metadataMap.putAll(this.params);
                    try (Session session = driver.session()) {
                        session.run(this.query, params);
                    }
                }

                // Convert back to Document to apply DocumentSplitter
                Document parentDoc = Document.from(parentSegment.text(), metadata);

                if (childSplitter == null) {
                    final Embedding content = embeddingModel.embed(parentSegment).content();
                    getAdditionalParams(parentId);
                    embeddingStore.add(content, parentSegment);
                    continue;
                }

                final String idProperty = embeddingStore.getIdProperty();
                List<TextSegment> childSegments = childSplitter.split(parentDoc)
                        .stream()
                        .map(segment -> getTextSegmentWithUniqueId(segment, idProperty, parentId))
                        .toList();

                final List<Embedding> embeddings = embeddingModel.embedAll(childSegments).content();
                getAdditionalParams(parentId);
                embeddingStore.addAll(embeddings, childSegments);
        }
    }

    private void getAdditionalParams(String parentId) {
        final HashMap<String, Object> params = new HashMap<>(this.params);
        params.put(parentIdKey, parentId);
        embeddingStore.setAdditionalParams(params);
    }
    
    /**
     * If `id` metadata is present, we create a new univocal one to prevent this error:
     * org.neo4j.driver.exceptions.ClientException: Node(1) already exists with label `Child` and property `id` = 'doc-ai'
     */
    public static TextSegment getTextSegmentWithUniqueId(TextSegment segment, String idProperty, String parentId) {
        final Metadata metadata1 = segment.metadata();
        final Object idMeta = metadata1.toMap().get(idProperty);
        String value = parentId == null 
                ? randomUUID() 
                : parentId + "_" + randomUUID();
        if (idMeta != null) {
            value = idMeta + "_" + value;
        }
        metadata1.put(idProperty, value);

        return segment;
    }
    
    @Override
    public List<Content> retrieve(final Query query) {

        final String question = query.text();
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        
        if (answerModel == null) {
            final Function<EmbeddingMatch<TextSegment>, Content> embeddingMapFunction = i -> {
                final TextSegment embedded = i.embedded();
                return Content.from(embedded);
            };

            return getList(request, embeddingMapFunction);
        } else {
            final Function<EmbeddingMatch<TextSegment>, String> embeddingMapFunction = i -> i.embedded().text();
            final List<String> context = getList(request, embeddingMapFunction);

            final String prompt = promptAnswer + """
                    Answer the question based only on the context provided.

                    Context: {{context}}

                    Question: {{question}}

                    Answer:
                    """;
            final String text = PromptTemplate.from(prompt).apply(Map.of("question", question, "context", context))
                    .text();
            final String chat = answerModel.chat(text);
            return List.of(Content.from(chat));
        }
    }

    private <T> List<T> getList(EmbeddingSearchRequest request, Function<EmbeddingMatch<TextSegment>, T> embeddingMapFunction) {
        return embeddingStore.search(request)
                .matches()
                .stream()
                .map(embeddingMapFunction)
                .toList();
    }

    public static class Builder<T extends Builder, V extends Neo4jEmbeddingRetriever> {
        protected EmbeddingModel embeddingModel;
        protected Driver driver;
        protected int maxResults = 10;
        protected double minScore = 0.7;
        protected String query;
        protected Map<String, Object> params = new HashMap<>();
        protected Neo4jEmbeddingStore embeddingStore;
        protected ChatLanguageModel chatModel;
        protected String promptSystem;
        protected String promptUser;
        protected ChatLanguageModel chatAnswerModel;
        protected String promptAnswer;
        protected String parentIdKey;

        /**
         * @param embeddingModel the embedding model used to embed the query and documents
         */
        public T embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return self();
        }

        /**
         * @param driver the Neo4j driver to connect to the database
         */
        public T driver(Driver driver) {
            this.driver = driver;
            return self();
        }

        /**
         * @param maxResults the maximum number of results to return
         */
        public T maxResults(int maxResults) {
            this.maxResults = maxResults;
            return self();
        }

        /**
         * @param minScore the minimum similarity score threshold for results
         */
        public T minScore(double minScore) {
            this.minScore = minScore;
            return self();
        }

        /**
         * @param query the Cypher query used to retrieve related documents
         */
        public T query(String query) {
            this.query = query;
            return self();
        }

        /**
         * @param params the parameters to be used with the Cypher query
         */
        public T params(Map<String, Object> params) {
            this.params = params;
            return self();
        }

        /**
         * @param embeddingStore a custom Neo4jEmbeddingStore (optional)
         */
        public T embeddingStore(Neo4jEmbeddingStore embeddingStore) {
            this.embeddingStore = embeddingStore;
            return self();
        }

        /**
         * @param chatLanguageModel the language model used for the question prompt
         */
        public T chatModel(ChatLanguageModel chatLanguageModel) {
            this.chatModel = chatLanguageModel;
            return self();
        }

        /**
         * @param promptSystem the system prompt text
         */
        public T promptSystem(String promptSystem) {
            this.promptSystem = promptSystem;
            return self();
        }

        /**
         * @param promptUser the user prompt template
         */
        public T promptUser(String promptUser) {
            this.promptUser = promptUser;
            return self();
        }

        /**
         * @param answerModel the language model used to generate answers
         */
        public T answerModel(ChatLanguageModel answerModel) {
            this.chatAnswerModel = answerModel;
            return self();
        }

        /**
         * @param promptAnswer the prompt template used to generate the answer (optional)
         */
        public T promptAnswer(String promptAnswer) {
            this.promptAnswer = promptAnswer;
            return self();
        }

        /**
         * @param parentIdKey the key used to identify the parent document (optional)
         */
        public T parentIdKey(String parentIdKey) {
            this.parentIdKey = parentIdKey;
            return self();
        }

        protected T self() {
            return (T) this;
        }

        public Neo4jEmbeddingRetriever build() {
            return new Neo4jEmbeddingRetriever(
                    embeddingModel,
                    driver,
                    maxResults,
                    minScore,
                    query,
                    params,
                    embeddingStore,
                    chatModel,
                    promptSystem,
                    promptUser,
                    chatAnswerModel,
                    promptAnswer,
                    parentIdKey
            );
        }
    }


}
