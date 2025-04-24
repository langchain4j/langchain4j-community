package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Type NODE = TypeSystem.getDefault().NODE();
    private static final Type RELATIONSHIP = TypeSystem.getDefault().RELATIONSHIP();
    private static final Type PATH = TypeSystem.getDefault().PATH();

    private final Neo4jGraph graph;

    private final ChatLanguageModel chatLanguageModel;

    private final PromptTemplate promptTemplate;
    private final List<String> examples;

    public Neo4jText2CypherRetriever(
            Neo4jGraph graph,
            ChatLanguageModel chatLanguageModel,
            PromptTemplate promptTemplate,
            List<String> examples) {

        this.graph = ensureNotNull(graph, "graph");
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.examples = getOrDefault(examples, List.of());
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
        String cypherQuery = generateCypherQuery(schema, question);
        List<String> response = executeQuery(cypherQuery);
        return response.stream().map(Content::from).toList();
    }

    private String generateCypherQuery(String schema, String question) {

        String examplesString = "";
        if (!this.examples.isEmpty()) {
            final String exampleJoin = String.join("\n", this.examples);
            examplesString = String.format("Cypher examples: \n%s\n", exampleJoin);
        }
        final Map<String, Object> templateVariables =
                Map.of("schema", schema, "question", question, "examples", examplesString);
        Prompt cypherPrompt = promptTemplate.apply(templateVariables);
        String cypherQuery = chatLanguageModel.chat(cypherPrompt.text());
        Matcher matcher = BACKTICKS_PATTERN.matcher(cypherQuery);
        if (matcher.find()) {
            return matcher.group(1);
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
        protected List<String> examples;

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
         * @param examples the few-shot examples to improve retrieving (optional, default is "")
         */
        public T examples(List<String> examples) {
            this.examples = examples;
            return self();
        }

        protected T self() {
            return (T) this;
        }

        public Neo4jText2CypherRetriever build() {
            return new Neo4jText2CypherRetriever(graph, chatLanguageModel, promptTemplate, examples);
        }
    }
}
