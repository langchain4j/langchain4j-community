package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;

public class Neo4jContentRetrieverBuilder {
    private Neo4jGraph graph;
    private ChatLanguageModel chatLanguageModel;
    private PromptTemplate promptTemplate;

    /**
     * @param graph the {@link Neo4jGraph} (required)
     */
    public Neo4jContentRetrieverBuilder graph(Neo4jGraph graph) {
        this.graph = graph;
        return this;
    }

    /**
     * @param chatLanguageModel the {@link ChatLanguageModel} (required)
     */
    public Neo4jContentRetrieverBuilder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * @param promptTemplate the {@link PromptTemplate} (optional, default is {@link Neo4jContentRetriever#DEFAULT_PROMPT_TEMPLATE})
     */
    public Neo4jContentRetrieverBuilder promptTemplate(PromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
        return this;
    }

    Neo4jContentRetriever build() {
        return new Neo4jContentRetriever(graph, chatLanguageModel, promptTemplate);
    }
}
