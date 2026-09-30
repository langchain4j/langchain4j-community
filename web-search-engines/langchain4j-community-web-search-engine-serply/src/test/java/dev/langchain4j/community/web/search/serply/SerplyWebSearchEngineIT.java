package dev.langchain4j.community.web.search.serply;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

class SerplyWebSearchEngineIT extends WebSearchEngineIT {

    @BeforeAll
    static void checkApiKey() {
        String apiKey = System.getenv("SERPLY_API_KEY");
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(), "Skipping Serply integration tests: SERPLY_API_KEY is not set");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return SerplyWebSearchEngine.builder()
                .apiKey(System.getenv("SERPLY_API_KEY"))
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
