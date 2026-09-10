package dev.langchain4j.community.web.search.serply;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.web.search.WebSearchInformationResult;
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

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(parse(json), 1);

        List<WebSearchOrganicResult> organicResults = results.results();
        assertThat(organicResults).hasSize(1);
        assertThat(organicResults.get(0).title()).isEqualTo("LangChain4j");
        assertThat(organicResults.get(0).snippet()).isEqualTo("Idiomatic Java library for LLMs.");
        assertThat(organicResults.get(0).url().toString()).isEqualTo("https://docs.langchain4j.dev/");
        assertThat(organicResults.get(0).metadata()).containsEntry("position", "1");

        WebSearchInformationResult information = results.searchInformation();
        assertThat(information.totalResults()).isEqualTo(42L);
        assertThat(information.pageNumber()).isEqualTo(1);
        assertThat(information.metadata()).doesNotContainKey(SerplyWebSearchEngine.TOTAL_RESULTS_ESTIMATED_KEY);
        assertThat(results.searchMetadata()).containsKey("relatedQuestions");
    }

    @Test
    void should_flag_total_results_as_estimated_when_serply_omits_total() throws Exception {
        String json = """
                {
                  "results": [
                    {"title": "One", "description": "A snippet", "position": 1, "link": "https://example.com/1"},
                    {"title": "Two", "description": "A snippet", "position": 2, "link": "https://example.com/2"}
                  ],
                  "total": null,
                  "related_questions": []
                }
                """;

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(parse(json), 3);

        WebSearchInformationResult information = results.searchInformation();
        assertThat(information.totalResults()).isEqualTo(2L);
        assertThat(information.pageNumber()).isEqualTo(3);
        assertThat(information.metadata()).containsEntry(SerplyWebSearchEngine.TOTAL_RESULTS_ESTIMATED_KEY, true);
        assertThat(results.searchMetadata()).doesNotContainKey("relatedQuestions");
    }

    @Test
    void should_fall_back_to_real_position_when_position_is_missing() throws Exception {
        String json = """
                {
                  "results": [
                    {"title": "Ranked", "description": "kept", "realPosition": 4, "link": "https://example.com/ranked"},
                    {"title": "Unranked", "description": "kept", "link": "https://example.com/unranked"}
                  ]
                }
                """;

        List<WebSearchOrganicResult> results =
                SerplyWebSearchEngine.toWebSearchResults(parse(json), 1).results();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).metadata()).containsEntry("position", "4");
        assertThat(results.get(1).metadata()).doesNotContainKey("position");
    }

    @Test
    void should_pass_missing_description_through_as_null_snippet() throws Exception {
        String json = """
                {
                  "results": [
                    {"title": "No description", "position": 1, "link": "https://example.com"}
                  ]
                }
                """;

        List<WebSearchOrganicResult> results =
                SerplyWebSearchEngine.toWebSearchResults(parse(json), 1).results();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).snippet()).isNull();
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

        WebSearchResults results = SerplyWebSearchEngine.toWebSearchResults(parse(json), 1);

        assertThat(results.results()).hasSize(1);
        assertThat(results.results().get(0).title()).isEqualTo("Valid");
    }

    private static SerplyWebSearchResponse parse(String json) throws Exception {
        return OBJECT_MAPPER.readValue(json, SerplyWebSearchResponse.class);
    }
}
