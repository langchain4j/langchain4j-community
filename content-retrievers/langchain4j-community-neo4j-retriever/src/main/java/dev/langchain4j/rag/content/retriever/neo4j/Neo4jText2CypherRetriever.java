package dev.langchain4j.rag.content.retriever.neo4j;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;

public class Neo4jText2CypherRetriever implements ContentRetriever {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
                    Based on the Neo4j graph schema below, write a Cypher query that would answer the user's question:
                    {{schema}}

                    Question: {{question}}
                    Cypher query:
                    """);

    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Type NODE = TypeSystem.getDefault().NODE();
    private static final Type RELATIONSHIP = TypeSystem.getDefault().RELATIONSHIP();
    private static final Type PATH = TypeSystem.getDefault().PATH();

    private final Neo4jGraph graph;

    private final ChatLanguageModel chatLanguageModel;

    private final PromptTemplate promptTemplate;
    private final int maxRetries;

    public Neo4jText2CypherRetriever(
            Neo4jGraph graph, ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate, int maxRetries) {

        this.graph = ensureNotNull(graph, "graph");
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.maxRetries = maxRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    /*
    Getter methods
    */
    public Neo4jGraph getGraph() {
        return graph;
    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public PromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    @Override
    public List<Content> retrieve(Query query) {

        String question = query.text();
        String schema = graph.getSchema();

        List<ChatMessage> messages = new ArrayList<>();
        final String cypherPrompt = promptTemplate
                .apply(Map.of("schema", schema, "question", question))
                .text();
        messages.add(UserMessage.from(cypherPrompt));

        final String emptyResultMsg =
                "The query result is empty. If `maxRetries` number is not reached, the query will be re-generated";
        try {
            return withRetry(
                    () -> {
                        String cypherQuery = generateCypherQuery(messages);

                        List<String> response;
                        try {
                            response = executeQuery(cypherQuery);
                        } catch (Exception e) {
                            final String errorUserMsg = String.format(
                                    """
                            The previous Cypher Statement throws the following error, consider it to return the correct statement: `%s`.
                            Please, try to return a valid query.

                            Cypher query:
                            """,
                                    e.getMessage());
                            messages.add(UserMessage.from(errorUserMsg));
                            throw e;
                        }

                        final List<Content> list =
                                response.stream().map(Content::from).toList();
                        if (list.isEmpty()) {
                            final String errorUserMsg =
                                    """
                            The previous Cypher Statement returns no result, consider it to return the correct statement.
                            Please, try to return a valid query.

                            Cypher query:
                            """;
                            messages.add(UserMessage.from(errorUserMsg));
                            throw new RuntimeException(emptyResultMsg);
                        }
                        return list;
                    },
                    maxRetries);
        } catch (Exception e) {
            if (e.getMessage().contains(emptyResultMsg)) {
                return List.of();
            }
            throw e;
        }
    }

    private String generateCypherQuery(List<ChatMessage> messages) {

        String cypherQuery = chatLanguageModel.chat(messages).aiMessage().text();
        Matcher matcher = BACKTICKS_PATTERN.matcher(cypherQuery);
        if (matcher.find()) {
            cypherQuery = matcher.group(1);
        }

        /*
        Sometimes, `cypher` is generated as a prefix, e.g.
        ```
        cypher
        MATCH (p:Person)-[:WROTE]->(b:Book {title: 'Dune'}) RETURN p.name AS author
        ```
         */
        if (cypherQuery.startsWith("cypher\n")) {
            cypherQuery = cypherQuery.replaceFirst("cypher\n", "");
        }

        return cypherQuery;
    }

    private List<String> executeQuery(String cypherQuery) {

        List<Record> records = graph.executeRead(cypherQuery);
        return records.stream()
                .flatMap(r -> r.values().stream())
                .map(value -> {
                    final boolean isEntity =
                            NODE.isTypeOf(value) || RELATIONSHIP.isTypeOf(value) || PATH.isTypeOf(value);
                    if (isEntity) {
                        return value.asMap().toString();
                    }
                    return value.toString();
                })
                .toList();
    }

    public static class Builder<T extends Builder<T>> {

        protected Neo4jGraph graph;
        protected ChatLanguageModel chatLanguageModel;
        protected PromptTemplate promptTemplate;
        protected int maxRetries = 3;

        /**
         * @param graph the {@link Neo4jGraph} (required)
         */
        public T graph(Neo4jGraph graph) {
            this.graph = graph;
            return self();
        }

        /**
         * @param chatLanguageModel the {@link ChatLanguageModel} (required)
         */
        public T chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return self();
        }

        /**
         * @param promptTemplate the {@link PromptTemplate} (optional, default is {@link Neo4jText2CypherRetriever#DEFAULT_PROMPT_TEMPLATE})
         */
        public T promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return self();
        }

        /**
         * @param maxRetries The maximum number of attempts to re-run the generated failed queries (default: 3)
         */
        public T maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return self();
        }

        protected T self() {
            return (T) this;
        }

        Neo4jText2CypherRetriever build() {
            return new Neo4jText2CypherRetriever(graph, chatLanguageModel, promptTemplate, maxRetries);
        }
    }
}
