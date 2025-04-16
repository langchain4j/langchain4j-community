package dev.langchain4j.community.rag.transformer;

import static dev.langchain4j.community.rag.transformer.GraphDocument.Edge;
import static dev.langchain4j.community.rag.transformer.GraphDocument.Node;
import static dev.langchain4j.community.rag.transformer.LLMGraphTransformerUtils.EXAMPLES_PROMPT;
import static dev.langchain4j.community.rag.transformer.LLMGraphTransformerUtils.getStringFromListOfMaps;
import static dev.langchain4j.community.rag.transformer.LLMGraphTransformerUtils.parseJson;
import static dev.langchain4j.community.rag.transformer.Neo4jUtils.getBacktickText;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LLMGraphTransformer {

    public static final String DEFAULT_NODE_TYPE = "Node";
    private final List<String> allowedNodes;
    private final List<String> allowedRelationships;
    private final List<ChatMessage> prompt;
    private final String additionalInstructions;
    private final ChatLanguageModel model;
    private final List<Map<String, String>> examples;
    private final Integer maxAttempts;

    /**
     * It allows specifying constraints on the types of nodes and relationships to include in the output graph.
     * The class supports extracting properties for both nodes and relationships.
     *
     * @param model the {@link ChatLanguageModel} (required)
     * @param allowedNodes Specifies which node types are allowed in the graph. If null or empty allows all node types (default: [])
     * @param allowedRelationships Specifies which relationship types are allowed in the graph. If null or empty allows all relationship types (default: [])
     * @param prompt The chat messages to pass to the LLM with additional instructions. (optional)
     * @param additionalInstructions Allows you to add additional instructions to the prompt without having to change the whole prompt (default: '')
     * @param examples Allows you to add additional instructions to the prompt without having to change the whole prompt (default: {@link LLMGraphTransformerUtils#EXAMPLES_PROMPT})
     * @param maxAttempts Retry N times the transformation if it fails (default: 1)
     */
    public LLMGraphTransformer(
            ChatLanguageModel model,
            List<String> allowedNodes,
            List<String> allowedRelationships,
            List<ChatMessage> prompt,
            String additionalInstructions,
            List<Map<String, String>> examples,
            Integer maxAttempts) {

        this.model = ensureNotNull(model, "model");

        this.allowedNodes = getOrDefault(allowedNodes, List.of());
        this.allowedRelationships = getOrDefault(allowedRelationships, List.of());
        this.prompt = prompt;

        this.maxAttempts = getOrDefault(maxAttempts, 1);
        this.additionalInstructions = getOrDefault(additionalInstructions, "");

        this.examples = getOrDefault(examples, EXAMPLES_PROMPT);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<GraphDocument> convertToGraphDocuments(List<Document> documents) {
        return documents.stream()
                .map(this::processResponse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ChatMessage> createUnstructuredPrompt(String text) {
        if (prompt != null && !prompt.isEmpty()) {
            return prompt;
        }

        final boolean withAllowedNodes = allowedNodes != null && !allowedNodes.isEmpty();
        final boolean withAllowedRels = allowedRelationships != null && !allowedRelationships.isEmpty();

        final PromptTemplate systemTemplate = PromptTemplate.from(
                """
                        You are a top-tier algorithm designed for extracting information in structured formats to build a knowledge graph.
                        Your task is to identify entities and relations from a given text and generate output in JSON format.
                        Each object should have keys: 'head', 'head_type', 'relation', 'tail', and 'tail_type'.
                        {{nodes}}
                        {{rels}}
                        IMPORTANT NOTES:\n- Don't add any explanation or extra text.
                        {{additional}}
                        """);

        final SystemMessage systemMessage = systemTemplate
                .apply(Map.of(
                        "nodes",
                        withAllowedNodes
                                ? "The 'head_type' and 'tail_type' must be one of: " + allowedNodes.toString()
                                : "",
                        "rels",
                        withAllowedRels ? "The 'relation' must be one of: " + allowedRelationships.toString() : "",
                        "additional",
                        additionalInstructions))
                .toSystemMessage();

        final String examplesString = getStringFromListOfMaps(examples);

        final PromptTemplate humanTemplate = PromptTemplate.from(
                """
                Based on the following example, extract entities and relations from the provided text.
                {{nodes}}
                {{rels}}
                Below are a number of examples of text and their extracted entities and relationships.
                {{examples}}
                {{additional}}
                For the following text, extract entities and relations as in the provided example.
                Text: {{input}}
                """);

        final UserMessage userMessage = humanTemplate
                .apply(Map.of(
                        "nodes", withAllowedNodes ? "# ENTITY TYPES:\n" + allowedNodes.toString() : "",
                        "rels", withAllowedRels ? "# RELATION TYPES:\n" + allowedRelationships.toString() : "",
                        "examples", examplesString,
                        "additional", additionalInstructions,
                        "input", text))
                .toUserMessage();

        return List.of(systemMessage, userMessage);
    }

    private GraphDocument processResponse(Document document) {

        final String text = document.text();

        final List<ChatMessage> messages = createUnstructuredPrompt(text);

        Set<GraphDocument.Node> nodesSet = new HashSet<>();
        Set<GraphDocument.Edge> relationships = new HashSet<>();

        List<Map<String, String>> parsedJson = getJsonResult(messages);
        if (parsedJson == null || parsedJson.isEmpty()) {
            return null;
        }

        for (Map<String, String> rel : parsedJson) {
            if (!rel.containsKey("head") || !rel.containsKey("tail") || !rel.containsKey("relation")) {
                continue;
            }

            Node sourceNode = new Node(rel.get("head"), rel.getOrDefault("head_type", DEFAULT_NODE_TYPE));
            Node targetNode = new Node(rel.get("tail"), rel.getOrDefault("tail_type", DEFAULT_NODE_TYPE));

            nodesSet.add(sourceNode);
            nodesSet.add(targetNode);

            final String relation = rel.get("relation");
            final Edge edge = new Edge(sourceNode, targetNode, relation);
            relationships.add(edge);
        }

        if (nodesSet.isEmpty()) {
            return null;
        }

        return new GraphDocument(nodesSet, relationships, document);
    }

    private List<Map<String, String>> getJsonResult(List<ChatMessage> messages) {

        return RetryUtils.withRetry(
                () -> {
                    final ChatResponse chat = model.chat(messages);
                    String rawSchema = chat.aiMessage().text();

                    rawSchema = getBacktickText(rawSchema);

                    return parseJson(rawSchema);
                },
                maxAttempts);
    }

    /**
     * Builder class for LLMGraphTransformer.
     */
    public static class Builder {
        private ChatLanguageModel model;
        private List<String> allowedNodes;
        private List<String> allowedRelationships;
        private List<ChatMessage> prompt;
        private String additionalInstructions = ""; // Default: empty string
        private List<Map<String, String>> examples = EXAMPLES_PROMPT; // Default examples
        private Integer maxAttempts = 1; // Default: 1 attempt

        /**
         * Sets the required ChatLanguageModel.
         *
         * @param model the {@link ChatLanguageModel} (required)
         * @return the Builder instance
         */
        public Builder model(ChatLanguageModel model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the allowed node types in the graph.
         *
         * @param allowedNodes a list of allowed node types, null or empty allows all types (default: [])
         * @return the Builder instance
         */
        public Builder allowedNodes(List<String> allowedNodes) {
            this.allowedNodes = allowedNodes;
            return this;
        }

        /**
         * Sets the allowed relationship types in the graph.
         *
         * @param allowedRelationships a list of allowed relationship types, null or empty allows all (default: [])
         * @return the Builder instance
         */
        public Builder allowedRelationships(List<String> allowedRelationships) {
            this.allowedRelationships = allowedRelationships;
            return this;
        }

        /**
         * Sets the chat messages to pass to the LLM.
         *
         * @param prompt list of chat messages (optional)
         * @return the Builder instance
         */
        public Builder prompt(List<ChatMessage> prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets additional instructions for the prompt.
         *
         * @param additionalInstructions additional text for the prompt (default: '')
         * @return the Builder instance
         */
        public Builder additionalInstructions(String additionalInstructions) {
            this.additionalInstructions = additionalInstructions;
            return this;
        }

        /**
         * Sets example instructions for the LLM.
         *
         * @param examples additional example instructions (default: {@link LLMGraphTransformerUtils#EXAMPLES_PROMPT})
         * @return the Builder instance
         */
        public Builder examples(List<Map<String, String>> examples) {
            this.examples = examples;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxAttempts number of attempts before failing (default: 1)
         * @return the Builder instance
         */
        public Builder maxAttempts(Integer maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Builds and returns an instance of LLMGraphTransformer.
         *
         * @return a new instance of {@link LLMGraphTransformer}
         * @throws IllegalArgumentException if required parameters are missing
         */
        public LLMGraphTransformer build() {
            if (model == null) {
                throw new IllegalArgumentException("ChatLanguageModel is required");
            }
            return new LLMGraphTransformer(
                    model, allowedNodes, allowedRelationships, prompt, additionalInstructions, examples, maxAttempts);
        }
    }
}
