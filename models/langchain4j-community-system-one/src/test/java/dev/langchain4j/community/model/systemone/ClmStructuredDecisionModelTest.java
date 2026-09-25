package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Json.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class ClmStructuredDecisionModelTest {

    @Test
    void sends_temperature_and_preserves_latency_header() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("X-CLM-Latency-Ms", "58.1")
                    .setBody("{\"answers\":{\"q\":{\"noul\":0.6}}}"));
            server.start();
            ClmStructuredDecisionModel model = ClmStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("A state")
                    .question("q", NoulQuestion.builder().instructions("True?").build())
                    .parameters(new ClmRequestParameters(null, 0.5))
                    .build();

            var response = model.decide(request);

            assertThat(response.answers().get("q").value()).isEqualTo(0.6);
            assertThat(response.metadata()).containsEntry("clm_latency_ms", "58.1");
            Map<String, Object> json = fromJson(server.takeRequest().getBody().readUtf8(), Map.class);
            assertThat(json).containsEntry("temperature", 0.5);
        }
    }

    @Test
    void ranks_candidates_without_turning_rank_into_a_question_type() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"model":"clm-latest","ranked":[
                      {"rank":1,"candidate":"Moon","prob":0.9},
                      {"rank":2,"candidate":"Sun","prob":0.1}]}
                    """));
            server.start();
            ClmStructuredDecisionModel model = ClmStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString())
                    .build();

            ClmRankResult result = model.rank("Tides", "Which cause?", List.of("Moon", "Sun"));

            assertThat(result.model()).isEqualTo("clm-latest");
            assertThat(result.ranked()).extracting(ClmRankResult.RankedCandidate::candidate)
                    .containsExactly("Moon", "Sun");
            assertThat(result.ranked().get(0).probability()).isEqualTo(0.9);
            assertThat(server.takeRequest().getPath()).isEqualTo("/v1/rank");
        }
    }

    @Test
    void validates_temperature_and_rank_candidates_locally() {
        assertThatThrownBy(() -> new ClmRequestParameters(null, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClmRequestParameters(null, 101.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
