package dev.langchain4j.community.web.search.serpapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

class SerpApiWebSearchEngineIT extends WebSearchEngineIT {

    @BeforeAll
    static void checkApiKey() {
        String apiKey = System.getenv("SERPAPI_API_KEY");
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(), "Skipping SerpApi integration tests: SERPAPI_API_KEY is not set");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return SerpApiWebSearchEngine.withApiKey(System.getenv("SERPAPI_API_KEY"));
    }
}
