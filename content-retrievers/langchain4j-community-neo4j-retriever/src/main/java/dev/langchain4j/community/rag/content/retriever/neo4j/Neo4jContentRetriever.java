package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;

/**
 * @deprecated use {@link Neo4jText2CypherRetriever} instead
 */
@Deprecated(forRemoval = true)
public class Neo4jContentRetriever extends Neo4jText2CypherRetriever {

    public Neo4jContentRetriever(Neo4jGraph graph, ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {
        super(graph, chatLanguageModel, promptTemplate, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jText2CypherRetriever.Builder<Builder> {

        @Override
        public Neo4jContentRetriever build() {
            return new Neo4jContentRetriever(graph, chatLanguageModel, promptTemplate);
        }
    }
}
