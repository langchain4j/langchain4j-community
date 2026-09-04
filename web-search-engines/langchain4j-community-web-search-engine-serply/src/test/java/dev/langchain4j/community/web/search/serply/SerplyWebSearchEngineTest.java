package dev.langchain4j.community.web.search.serply;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import java.util.List;
import org.junit.jupiter.api.Test;

class SerplyWebSearchEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void should_map_organic_results_and_expose_related_questions_as_metadata() throws Exception {
        String json = """
                {
                  "results": [
                    {
                      "title": "LangChain4j",
                      "description": "Idiomatic Java library for LLMs.",
                      "position": 1,
                      "realPosition": 1,
                      "result_type": "organic",
                      "metadata": {"display_url": "docs.langchain4j.dev"},
                      "link": "https://docs.langchain4j.dev/"
                    }
                  ],
                  "total": 42,
                  "related_questions": [{"question": "What is LangChain4j?"}]
                }
                """;
        SerplyWebSearchResponse response = OBJECT_MAPPER.readValue(json, SerplyWebSearchResponse.class);

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(response);

        List<WebSearchOrganicResult> organicResults = results.results();
        assertThat(organicResults).hasSize(1);
        assertThat(organicResults.get(0).title()).isEqualTo("LangChain4j");
        assertThat(organicResults.get(0).snippet()).isEqualTo("Idiomatic Java library for LLMs.");
        assertThat(organicResults.get(0).url().toString()).isEqualTo("https://docs.langchain4j.dev/");

        assertThat(results.searchInformation().totalResults()).isEqualTo(42L);
        assertThat(results.searchMetadata()).containsKey("relatedQuestions");
    }

    @Test
    void should_omit_related_questions_metadata_when_absent() throws Exception {
        String json = """
                {
                  "results": [
                    {"title": "Only Result", "description": "A snippet", "position": 1, "link": "https://example.com"}
                  ],
                  "related_questions": []
                }
                """;
        SerplyWebSearchResponse response = OBJECT_MAPPER.readValue(json, SerplyWebSearchResponse.class);

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(response);

        assertThat(results.searchMetadata()).doesNotContainKey("relatedQuestions");
        assertThat(results.results()).hasSize(1);
    }

    @Test
    void should_skip_results_missing_title_or_link() throws Exception {
        String json = """
                {
                  "results": [
                    {"title": "", "description": "no title", "link": "https://example.com"},
                    {"title": "No link", "description": "missing link"},
                    {"title": "Valid", "description": "kept", "link": "https://example.com/valid"}
                  ]
                }
                """;
        SerplyWebSearchResponse response = OBJECT_MAPPER.readValue(json, SerplyWebSearchResponse.class);

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(response);

        assertThat(results.results()).hasSize(1);
        assertThat(results.results().get(0).title()).isEqualTo("Valid");
    }
}
