package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Json.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.structureddecision.ChoiceAnswer;
import dev.langchain4j.model.structureddecision.ChoiceQuestion;
import dev.langchain4j.model.structureddecision.ConfidenceProvenance;
import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.OptionCriteria;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class SystemOneStructuredDecisionModelTest {

    @Test
    @SuppressWarnings("unchecked")
    void decodes_system_one_extensions_without_treating_them_as_protocol_fields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"model":"local","usage":{"input_tokens":5,"output_tokens":2},"latency_ms":17,
                     "vendor_null":null,
                     "answers":{"team":{"type":"choice","choice":"billing","confidence":0.8,
                       "probabilities":{"billing":0.8},"legend":{"billing":"Billing"},
                       "vendor_diagnostic":{"code":3}}}}
                    """));
            server.start();
            SystemOneStructuredDecisionModel model = SystemOneStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("Charge dispute")
                    .question("team", ChoiceQuestion.builder()
                            .instructions("Route request")
                            .option("billing", OptionCriteria.builder().what("Payments").build())
                            .build())
                    .build();

            StructuredDecisionResponse response = model.decide(request);

            assertThat(response.answers().get("team")).isInstanceOf(ChoiceAnswer.class);
            assertThat(response.answers().get("team").confidenceProvenance())
                    .isEqualTo(ConfidenceProvenance.PROVIDER_REPORTED);
            assertThat(response.answers().get("team").metadata())
                    .containsEntry("probabilities", Map.of("billing", 0.8))
                    .containsEntry("legend", Map.of("billing", "Billing"))
                    .containsEntry("vendor_diagnostic", Map.of("code", 3));
            assertThat(response.metadata())
                    .containsEntry("model", "local")
                    .containsEntry("latency_ms", 17)
                    .containsEntry("vendor_null", null)
                    .containsEntry("usage", Map.of("input_tokens", 5, "output_tokens", 2));

            RecordedRequest posted = server.takeRequest();
            assertThat(posted.getPath()).isEqualTo("/v1/systemone");
            assertThat(posted.getHeader("Authorization")).isNull();
            Map<String, Object> json = fromJson(posted.getBody().readUtf8(), Map.class);
            assertThat(json).doesNotContainKey("model");
            assertThat(json.get("state")).isEqualTo("Charge dispute");
        }
    }

    @Test
    void rejects_an_unknown_question_before_http() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            SystemOneStructuredDecisionModel model = SystemOneStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("x")
                    .question("new", () -> "Provider-specific question")
                    .build();

            assertThatThrownBy(() -> model.decide(request)).isInstanceOf(UnsupportedFeatureException.class);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void rejects_a_response_answer_for_a_question_not_sent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"answers":{"unexpected":{"type":"noul","noul":0.8}}}
                    """));
            server.start();
            SystemOneStructuredDecisionModel model = SystemOneStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("x")
                    .question("expected", NoulQuestion.builder().instructions("True?").build())
                    .build();

            assertThatThrownBy(() -> model.decide(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unexpected");
        }
    }

    @Test
    void rejects_a_response_answer_with_the_wrong_type() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"answers":{"expected":{"type":"choice","choice":"wrong"}}}
                    """));
            server.start();
            SystemOneStructuredDecisionModel model = SystemOneStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("x")
                    .question("expected", NoulQuestion.builder().instructions("True?").build())
                    .build();

            assertThatThrownBy(() -> model.decide(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected");
        }
    }
}
