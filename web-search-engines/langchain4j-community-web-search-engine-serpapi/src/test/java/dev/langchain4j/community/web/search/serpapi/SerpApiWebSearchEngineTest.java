package dev.langchain4j.community.web.search.serpapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.util.List;
import org.junit.jupiter.api.Test;

class SerpApiWebSearchEngineTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String GOOGLE = """
            {
              "search_metadata": {"id": "abc", "status": "Success", "total_time_taken": 1.2,
                                  "google_url": "https://www.google.com/search?q=245*17"},
              "search_parameters": {"engine": "google", "q": "245*17"},
              "search_information": {"total_results": 12300000000, "organic_results_state": "Results for exact spelling"},
              "serpapi_pagination": {"current": 1},
              "answer_box": {"type": "calculator_result", "problem": "245 × 17 =", "result": "4165"},
              "knowledge_graph": {"title": "ISRO", "type": "Space agency",
                                  "description": "The Indian Space Research Organisation is India's space agency.",
                                  "source": {"name": "Wikipedia", "link": "https://en.wikipedia.org/wiki/ISRO"}},
              "organic_results": [
                {"position": 1, "title": "LangChain4j", "link": "https://docs.langchain4j.dev/",
                 "snippet": "Supercharge your Java application.", "date": "Oct 1, 2026"},
                {"position": 2, "title": "Highlighted only", "link": "https://example.com/2",
                 "snippet_highlighted_words": ["Java", "LLM"]},
                {"position": 3, "title": "No link", "snippet": "Skipped"},
                {"position": 4, "title": "", "link": "https://example.com/4", "snippet": "Skipped"},
                {"position": 5, "title": "Displayed link only", "link": "https://example.com/5",
                 "displayed_link": "example.com › 5"}
              ]
            }
            """;

    @Test
    void should_map_google_organic_results() {
        WebSearchResults results = map(GOOGLE, "google", WebSearchRequest.from("q"), false, false);

        List<WebSearchOrganicResult> organic = results.results();
        assertThat(organic).hasSize(3);
        assertThat(organic.get(0).title()).isEqualTo("LangChain4j");
        assertThat(organic.get(0).url()).hasToString("https://docs.langchain4j.dev/");
        assertThat(organic.get(0).snippet()).isEqualTo("Oct 1, 2026 — Supercharge your Java application.");
        assertThat(organic.get(0).metadata()).containsEntry("position", "1").containsEntry("result_type", "organic");
        assertThat(organic.get(1).snippet()).isEqualTo("Java … LLM");
        assertThat(organic.get(2).snippet()).isEqualTo("example.com › 5");
        assertThat(organic).allSatisfy(result -> assertThat(result.content()).isNull());

        assertThat(results.searchInformation().totalResults()).isEqualTo(12_300_000_000L);
        assertThat(results.searchInformation().pageNumber()).isEqualTo(1);
        assertThat(results.searchInformation().metadata())
                .doesNotContainKey(SerpApiWebSearchEngine.TOTAL_RESULTS_ESTIMATED_KEY);
        assertThat(results.searchMetadata()).containsEntry("search_id", "abc").containsKey("search_parameters");
    }

    @Test
    void should_prepend_answer_box_and_knowledge_graph_when_enabled() {
        List<WebSearchOrganicResult> results =
                map(GOOGLE, "google", WebSearchRequest.from("q"), true, true).results();

        assertThat(results).hasSize(5);
        assertThat(results.get(0).title()).isEqualTo("Google answer: Calculator");
        assertThat(results.get(0).snippet()).isEqualTo("245 × 17 = 4165");
        assertThat(results.get(0).url()).hasToString("https://www.google.com/search?q=245*17");
        assertThat(results.get(0).metadata())
                .containsEntry("result_type", "answer_box")
                .containsEntry("answer_box_type", "calculator_result");
        assertThat(results.get(1).title()).isEqualTo("Knowledge graph: ISRO (Space agency)");
        assertThat(results.get(1).snippet())
                .isEqualTo("The Indian Space Research Organisation is India's space agency. Source: Wikipedia");
        assertThat(results.get(1).url()).hasToString("https://en.wikipedia.org/wiki/ISRO");
    }

    @Test
    void should_not_include_answer_box_for_non_google_engines() {
        List<WebSearchOrganicResult> results =
                map(GOOGLE, "bing", WebSearchRequest.from("q"), true, true).results();

        assertThat(results).extracting(r -> r.metadata().get("result_type")).containsOnly("organic");
    }

    @Test
    void should_format_organic_answer_box() throws Exception {
        String json = """
                {"answer_box": {"type": "organic_result", "title": "Capital of India",
                                "answer": "New Delhi", "link": "https://en.wikipedia.org/wiki/New_Delhi"}}
                """;

        List<WebSearchOrganicResult> results =
                map(json, "google", WebSearchRequest.from("q"), true, false).results();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Google answer: Capital of India");
        assertThat(results.get(0).snippet()).isEqualTo("New Delhi");
        assertThat(SerpApiWebSearchEngine.formatAnswerBox(OBJECT_MAPPER.readTree("""
                        {"type": "weather_result", "temperature": "31", "unit": "Celsius",
                         "weather": "Haze", "location": "New Delhi"}
                        """)))
                .isEqualTo("31°C, Haze, New Delhi");
    }

    @Test
    void should_skip_knowledge_graph_without_description() {
        String json = """
                {"knowledge_graph": {"title": "ISRO", "website": "https://www.isro.gov.in"}}
                """;

        assertThat(map(json, "google", WebSearchRequest.from("q"), false, true).results())
                .isEmpty();
    }

    @Test
    void should_return_empty_results_when_google_found_nothing() {
        String json = """
                {"search_metadata": {"status": "Success"},
                 "search_information": {"organic_results_state": "Fully empty"},
                 "error": "Google hasn't returned any results for this query."}
                """;

        WebSearchResults results = map(SerpApiClient.parse(json), "google", WebSearchRequest.from("q"), true, true);

        assertThat(results.results()).isEmpty();
        assertThat(results.searchInformation().totalResults()).isZero();
        assertThat(results.searchInformation().metadata())
                .containsEntry(SerpApiWebSearchEngine.TOTAL_RESULTS_ESTIMATED_KEY, true);
    }

    @Test
    void should_trim_to_max_results_keeping_answer_box_first() {
        List<WebSearchOrganicResult> results =
                map(GOOGLE, "google", WebSearchRequest.from("q", 2), true, true).results();

        assertThat(results)
                .extracting(r -> r.metadata().get("result_type"))
                .containsExactly("answer_box", "knowledge_graph");
    }

    @Test
    void should_map_news_and_flatten_stories() {
        String json = """
                {"news_results": [
                  {"position": 1, "title": "ISRO launches satellite", "link": "https://news.example/1",
                   "source": {"name": "The Hindu"}, "date": "10/09/2026, 07:00 AM, +0000 UTC",
                   "iso_date": "2026-10-09T07:00:00Z"},
                  {"position": 2, "title": "Top story", "stories": [
                    {"title": "Story A", "link": "https://news.example/a", "source": {"name": "NDTV"},
                     "date": "1 day ago"},
                    {"title": "Story without link", "source": {"name": "NDTV"}}
                  ]}
                ]}
                """;

        List<WebSearchOrganicResult> results = map(json, "google_news", WebSearchRequest.from("q"), false, false)
                .results();

        assertThat(results)
                .extracting(WebSearchOrganicResult::title)
                .containsExactly("ISRO launches satellite", "Story A");
        assertThat(results.get(0).snippet()).isEqualTo("The Hindu · 10/09/2026, 07:00 AM, +0000 UTC");
        assertThat(results.get(0).metadata())
                .containsEntry("result_type", "news")
                .containsEntry("iso_date", "2026-10-09T07:00:00Z");
        assertThat(results.get(1).snippet()).isEqualTo("NDTV · 1 day ago");
    }

    @Test
    void should_map_scholar_and_skip_results_without_link() {
        String json = """
                {"organic_results": [
                  {"position": 0, "title": "Retrieval-augmented generation", "link": "https://arxiv.org/abs/2005.11401",
                   "result_id": "xyz", "snippet": "We explore RAG.",
                   "publication_info": {"summary": "P Lewis, E Perez - NeurIPS, 2020"},
                   "inline_links": {"cited_by": {"total": 9000}}},
                  {"position": 1, "title": "[CITATION] No link", "snippet": "Skipped"}
                ]}
                """;

        List<WebSearchOrganicResult> results = map(json, "google_scholar", WebSearchRequest.from("q"), false, false)
                .results();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).snippet())
                .isEqualTo("We explore RAG. — P Lewis, E Perez - NeurIPS, 2020 (cited by 9000)");
        assertThat(results.get(0).metadata())
                .containsEntry("result_type", "scholar")
                .containsEntry("cited_by", "9000")
                .containsEntry("result_id", "xyz");
    }

    @Test
    void should_map_youtube_videos() {
        String json = """
                {"video_results": [
                  {"position_on_page": 1, "title": "LangChain4j tutorial", "link": "https://www.youtube.com/watch?v=1",
                   "channel": {"name": "Java Channel"}, "length": "12:34", "views": 1500,
                   "published_date": "2 months ago", "description": "Build an agent in Java."},
                  {"position_on_page": 2, "title": "No description", "link": "https://www.youtube.com/watch?v=2",
                   "channel": {"name": "Other"}}
                ]}
                """;

        List<WebSearchOrganicResult> results =
                map(json, "youtube", WebSearchRequest.from("q"), false, false).results();

        assertThat(results.get(0).snippet())
                .isEqualTo("Build an agent in Java. — Java Channel · 12:34 · 1500 views · 2 months ago");
        assertThat(results.get(0).metadata()).containsEntry("channel", "Java Channel");
        assertThat(results.get(1).snippet()).isEqualTo("Other");
    }

    @Test
    void should_mask_api_key_in_builder_to_string() {
        SerpApiWebSearchEngine.Builder builder =
                SerpApiWebSearchEngine.builder().apiKey("secret-key-123");

        assertThat(builder.toString()).doesNotContain("secret-key-123").contains("****");
        assertThat(builder.build().toString()).doesNotContain("secret-key-123");
    }

    private static WebSearchResults map(
            String json, String engine, WebSearchRequest request, boolean answerBox, boolean knowledgeGraph) {
        try {
            return map(OBJECT_MAPPER.readTree(json), engine, request, answerBox, knowledgeGraph);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static WebSearchResults map(
            JsonNode root, String engine, WebSearchRequest request, boolean answerBox, boolean knowledgeGraph) {
        return SerpApiWebSearchEngine.toWebSearchResults(root, engine, request, answerBox, knowledgeGraph);
    }
}
