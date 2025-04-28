package dev.langchain4j.community.data.document.transformer.graph;

import static dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformerUtils.getBacktickText;
import static dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformerUtils.parseJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.data.document.graph.GraphEdge;
import dev.langchain4j.community.data.document.graph.GraphNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A llm-based graph transformer which transforms documents into graph-based documents using a LLM.
 *
 * @since 1.0.0-beta4
 */
@Experimental
public class LLMGraphTransformer implements GraphTransformer {

    private static final String DEFAULT_NODE_TYPE = "Node";
    private static final PromptTemplate SYSTEM_TEMPLATE = PromptTemplate.from(
            """
                    You are a top-tier algorithm designed for extracting information in structured formats to build a knowledge graph.
                    Your task is to identify entities and relations from a given text and generate output in JSON format.
                    Each object should have keys: 'head', 'head_type', 'relation', 'tail', and 'tail_type'.
                    {{nodes}}
                    {{rels}}
                    IMPORTANT NOTES:\n- Don't add any explanation or extra text.
                    {{additional}}
                    """);
    private static final PromptTemplate USER_TEMPLATE = PromptTemplate.from(
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

    private final List<String> allowedNodes;
    private final List<String> allowedRelationships;
    private final List<ChatMessage> prompt;
    private final String examples;
    private final String additionalInstructions;
    private final ChatModel chatModel;
    private final Integer maxAttempts;

    /**
     * It allows specifying constraints on the types of nodes and relationships to include in the output graph.
     * The class supports extracting properties for both nodes and relationships.
     *
     * @param chatModel              the {@link ChatModel} (required)
     * @param allowedNodes           Specifies which node types are allowed in the graph. If null or empty allows all node types (default: [])
     * @param allowedRelationships   Specifies which relationship types are allowed in the graph. If null or empty allows all relationship types (default: [])
     * @param prompt                 The chat messages to pass to the LLM with additional instructions. (optional)
     * @param additionalInstructions Allows you to add additional instructions to the prompt without having to change the whole prompt (default: '')
     * @param maxAttempts            Retry N times the transformation if it fails (default: 1)
     */
    public LLMGraphTransformer(
            ChatModel chatModel,
            List<String> allowedNodes,
            List<String> allowedRelationships,
            List<ChatMessage> prompt,
            String additionalInstructions,
            String examples,
            Integer maxAttempts) {

        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.examples = ensureNotNull(examples, "examples");

        this.allowedNodes = getOrDefault(allowedNodes, List.of());
        this.allowedRelationships = getOrDefault(allowedRelationships, List.of());
        this.prompt = prompt;

        this.maxAttempts = getOrDefault(maxAttempts, 1);
        this.additionalInstructions = getOrDefault(additionalInstructions, "");
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ChatMessage> createUnstructuredPrompt(String text) {
        if (prompt != null && !prompt.isEmpty()) {
            return prompt;
        }

        boolean withAllowedNodes = allowedNodes != null && !allowedNodes.isEmpty();
        boolean withAllowedRels = allowedRelationships != null && !allowedRelationships.isEmpty();

        SystemMessage systemMessage = SYSTEM_TEMPLATE
                .apply(Map.of(
                        "nodes",
                        withAllowedNodes ? "The 'head_type' and 'tail_type' must be one of: " + allowedNodes : "",
                        "rels",
                        withAllowedRels ? "The 'relation' must be one of: " + allowedRelationships : "",
                        "additional",
                        additionalInstructions))
                .toSystemMessage();
        UserMessage userMessage = USER_TEMPLATE
                .apply(Map.of(
                        "nodes", withAllowedNodes ? "# ENTITY TYPES:\n" + allowedNodes : "",
                        "rels", withAllowedRels ? "# RELATION TYPES:\n" + allowedRelationships : "",
                        "examples", examples,
                        "additional", additionalInstructions,
                        "input", text))
                .toUserMessage();

        return List.of(systemMessage, userMessage);
    }

    @Override
    public GraphDocument transform(Document document) {

        String text = document.text();
        List<ChatMessage> messages = createUnstructuredPrompt(text);

        Set<GraphNode> nodesSet = new HashSet<>();
        Set<GraphEdge> relationships = new HashSet<>();

        List<Map<String, String>> parsedJson = getJsonResult(messages);
        if (parsedJson == null || parsedJson.isEmpty()) {
            return null;
        }

        for (Map<String, String> rel : parsedJson) {
            if (!rel.containsKey("head") || !rel.containsKey("tail") || !rel.containsKey("relation")) {
                continue;
            }

            GraphNode sourceNode = GraphNode.from(rel.get("head"), rel.getOrDefault("head_type", DEFAULT_NODE_TYPE));
            GraphNode targetNode = GraphNode.from(rel.get("tail"), rel.getOrDefault("tail_type", DEFAULT_NODE_TYPE));

            nodesSet.add(sourceNode);
            nodesSet.add(targetNode);

            String relation = rel.get("relation");
            GraphEdge edge = GraphEdge.from(sourceNode, targetNode, relation);
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
                    ChatResponse chat = chatModel.chat(messages);
                    return parseJson(getBacktickText(chat.aiMessage().text()));
                },
                maxAttempts);
    }

    /**
     * Builder class for LLMGraphTransformer.
     */
    public static class Builder {

        private ChatModel model;
        private List<String> allowedNodes;
        private List<String> allowedRelationships;
        private List<ChatMessage> prompt;
        private String additionalInstructions = ""; // Default: empty string
        private String examples; // Default examples
        private Integer maxAttempts = 1; // Default: 1 attempt

        /**
         * Sets the required ChatModel.
         *
         * @param model the {@link ChatModel} (required)
         * @return the Builder instance
         */
        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }

        /**
         * Sets example instructions for the LLM.
         *
         * @param examples additional example instructions (required)
         * @return the Builder instance
         */
        public Builder examples(String examples) {
            this.examples = examples;
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
            return new LLMGraphTransformer(
                    model, allowedNodes, allowedRelationships, prompt, additionalInstructions, examples, maxAttempts);
        }
    }
}
