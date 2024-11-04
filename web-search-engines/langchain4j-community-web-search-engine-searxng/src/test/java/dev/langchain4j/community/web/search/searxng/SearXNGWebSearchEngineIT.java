package dev.langchain4j.community.web.search.searxng;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;

@EnabledIfEnvironmentVariable(named = "SEARXNG_BASE_URL", matches = ".+")
class SearXNGWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = SearXNGWebSearchEngine.builder(System.getenv("SEARXNG_BASE_URL")).build();

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
