package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jUtils.getBacktickText;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.cypherdsl.parser.CypherParser;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;

public class Neo4jText2CypherRetriever implements ContentRetriever {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
                    Task:Generate Cypher statement to query a graph database.
                    Instructions
                    Use only the provided relationship types and properties in the schema.
                    Do not use any other relationship types or properties that are not provided.

                    Schema:
                    {{schema}}

                    {{examples}}
                    Note: Do not include any explanations or apologies in your responses.
                    Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
                    Do not include any text except the generated Cypher statement.
                    The question is: {{question}}
                    """);

    private static final Type NODE = TypeSystem.getDefault().NODE();
    private static final Type RELATIONSHIP = TypeSystem.getDefault().RELATIONSHIP();
    private static final Type PATH = TypeSystem.getDefault().PATH();

    private final Neo4jGraph graph;

    private final ChatModel chatModel;

    private final PromptTemplate promptTemplate;
    private final int maxRetries;
    private final List<String> examples;
    private final List<String> relationships;
    private final String dialect;

    public Neo4jText2CypherRetriever(
            Neo4jGraph graph,
            ChatModel chatModel,
            PromptTemplate promptTemplate,
            List<String> examples,
            int maxRetries,
            List<String> relationships,
            String dialect) {

        this.graph = ensureNotNull(graph, "graph");
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.examples = getOrDefault(examples, List.of());
        this.maxRetries = maxRetries;
        this.relationships = getOrDefault(relationships, List.of());
        this.dialect = getOrDefault(dialect, Dialect.NEO4J_5_26.name());
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

    public ChatModel getChatModel() {
        return chatModel;
    }

    public PromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    @Override
    public List<Content> retrieve(Query query) {

        String question = query.text();
        String schema = graph.getSchema();

        String examplesString = "";
        if (!this.examples.isEmpty()) {
            String exampleJoin = String.join("\n", this.examples);
            examplesString = String.format("Cypher examples: \n%s\n", exampleJoin);
        }
        Map<String, Object> templateVariables =
                Map.of("schema", schema, "question", question, "examples", examplesString);
        String cypherPrompt = promptTemplate.apply(templateVariables).text();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(cypherPrompt));

        String emptyResultMsg =
                "The query result is empty. If `maxRetries` number is not reached, the query will be re-generated";
        try {
            return withRetry(
                    () -> {
                        String cypherQuery = generateCypherQuery(messages);

                        List<String> response;
                        try {
                            response = executeQuery(cypherQuery);
                        } catch (Exception e) {
                            String errorUserMsg = String.format(
                                    """
                                            The previous Cypher Statement throws the following error, consider it to return the correct statement: `%s`.
                                            Please, try to return a valid query.

                                            Cypher query:
                                            """,
                                    e.getMessage());
                            messages.add(UserMessage.from(errorUserMsg));
                            throw e;
                        }

                        List<Content> list =
                                response.stream().map(Content::from).toList();
                        if (list.isEmpty()) {
                            String errorUserMsg =
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

    /**
     * To fixed e.g. wrong relationship directions
     *
     * @param cypher the starting Cypher statement
     * @return the fixed Cypher
     */
    private String getFixedCypherWithDSL(String cypher) {
        if (relationships.isEmpty()) {
            return cypher;
        }
        var statement = CypherParser.parse(cypher);

        var configuration = Configuration.newConfig()
                .withPrettyPrint(false)
                .alwaysEscapeNames(false)
                .withEnforceSchema(true)
                .withDialect(Dialect.valueOf(dialect));

        relationships.stream()
                .map(Configuration::relationshipDefinition)
                .forEach(configuration::withRelationshipDefinition);

        return Renderer.getRenderer(configuration.build()).render(statement);
    }

    private String generateCypherQuery(List<ChatMessage> messages) {

        String cypherQuery = chatModel.chat(messages).aiMessage().text();
        cypherQuery = getFixedCypherWithDSL(cypherQuery);
        return getBacktickText(cypherQuery);
    }

    private List<String> executeQuery(String cypherQuery) {

        List<Record> records = graph.executeRead(cypherQuery);
        return records.stream()
                .flatMap(r -> r.values().stream())
                .map(value -> {
                    boolean isEntity = NODE.isTypeOf(value) || RELATIONSHIP.isTypeOf(value) || PATH.isTypeOf(value);
                    if (isEntity) {
                        return value.asMap().toString();
                    }
                    return value.toString();
                })
                .toList();
    }

    public static class Builder {

        protected Neo4jGraph graph;
        protected ChatModel chatModel;
        protected PromptTemplate promptTemplate;
        protected List<String> relationships;
        protected String dialect;
        protected int maxRetries = 3;
        protected List<String> examples;

        /**
         * @param graph the {@link Neo4jGraph} (required)
         */
        public Builder graph(Neo4jGraph graph) {
            this.graph = graph;
            return this;
        }

        /**
         * @param chatModel the {@link ChatModel} (required)
         */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * @param promptTemplate the {@link PromptTemplate} (optional, default is {@link Neo4jText2CypherRetriever#DEFAULT_PROMPT_TEMPLATE})
         */
        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        /**
         * @param relationships the list of relationships, if not null fix (if needed) the generated query via {@link org.neo4j.cypherdsl.core.renderer.Configuration.Builder#withRelationshipDefinition(Configuration.RelationshipDefinition)}
         *                      for example, a List.of( "(Foo, WROTE, Baz)", "(Jack, KNOWS, John)" ),
         *                      will create configuration.withRelationshipDefinition(new RelationshipDefinition("Foo", "WROTE", "Baz"))
         *                      .withRelationshipDefinition("Jack", "KNOWS", "John")
         *                      (default is: empty list)
         */
        public Builder relationships(List<String> relationships) {
            this.relationships = relationships;
            return this;
        }

        /**
         * @param dialect the string value of the {@link org.neo4j.cypherdsl.core.renderer},
         *                to be used via {@link org.neo4j.cypherdsl.core.renderer.Configuration.Builder#withDialect(Dialect)} , if {@param relationships} is not empty
         *                (default is: "NEO4J_5_23")
         */
        public Builder dialect(String dialect) {
            this.dialect = dialect;
            return this;
        }

        /**
         * @param maxRetries The maximum number of attempts to re-run the generated failed queries (default: 3)
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * @param examples the few-shot examples to improve retrieving (optional, default is "")
         */
        public Builder examples(List<String> examples) {
            this.examples = examples;
            return this;
        }

        public Neo4jText2CypherRetriever build() {
            return new Neo4jText2CypherRetriever(
                    graph, chatModel, promptTemplate, examples, maxRetries, relationships, dialect);
        }
    }
}
