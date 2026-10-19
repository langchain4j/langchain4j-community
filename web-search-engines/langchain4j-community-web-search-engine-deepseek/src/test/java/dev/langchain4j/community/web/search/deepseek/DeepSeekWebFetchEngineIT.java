package dev.langchain4j.community.web.search.deepseek;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeepSeekWebFetchEngineIT {

    private static final String DEEPSEEK_API_KEY = System.getenv("DEEPSEEK_API_KEY");

    @BeforeAll
    static void checkApiKey() {
        String apiKey = DEEPSEEK_API_KEY;
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(),
                "Skipping DeepSeek integration tests: DEEPSEEK_API_KEY is not set");
    }

    private final DeepSeekWebFetchEngine fetchEngine = DeepSeekWebFetchEngine.builder()
            .apiKey(DEEPSEEK_API_KEY)
            .timeout(Duration.ofSeconds(60))
            .build();

    @Test
    void should_fetch_page() {
        DeepSeekFetchResult result = fetchEngine.fetch("https://docs.langchain4j.dev/");

        assertThat(result.title()).isNotBlank();
        assertThat(result.content()).isNotBlank();
    }
}
