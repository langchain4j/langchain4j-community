package dev.langchain4j.community.model.typesafe;

import static dev.langchain4j.internal.Json.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.structureddecision.ChoiceQuestion;
import dev.langchain4j.model.structureddecision.NoulCriteria;
import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.OptionCriteria;
import dev.langchain4j.model.structureddecision.ScoreQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionAnswer;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class TypeSafeStructuredDecisionModelTest {

    @Test
    @SuppressWarnings("unchecked")
    void should_send_system_one_request_and_map_all_answer_types() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "model": "jev-latest",
                              "answers": {
                                "refund": {"type": "noul", "noul": 0.91},
                                "team": {"type": "choice", "choice": "billing", "confidence": 0.87,
                                         "probabilities": {"billing": 0.87, "support": 0.13}},
                                "urgency": {"type": "score", "score": 1.7, "confidence": 0.78,
                                            "legend": {"0": "low", "1": "medium", "2": "high"},
                                            "probabilities": {"0": 0.1, "1": 0.1, "2": 0.8}}
                              },
                              "usage": {"input_tokens": 42, "output_tokens": 3}
                            }
                            """));
            server.start();

            TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                    .apiKey("secret-key")
                    .baseUrl(server.url("/").toString())
                    .build();

            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state(Map.of("message", "I was charged twice; fix this today"))
                    .question(
                            "refund",
                            NoulQuestion.builder()
                                    .instructions("Is a refund requested?")
                                    .criteria(NoulCriteria.builder()
                                            .what("The customer explicitly asks for money back")
                                            .notFor("A question about a charge")
                                            .examples(List.of("Refund me"))
                                            .build())
                                    .build())
                    .question(
                            "team",
                            ChoiceQuestion.builder()
                                    .instructions("Which team should handle this?")
                                    .option("billing", criteria("Payment or charge problem"))
                                    .option("support", criteria("Product usage problem"))
                                    .build())
                    .question(
                            "urgency",
                            ScoreQuestion.builder()
                                    .instructions("How urgent is this?")
                                    .level("low", criteria("Can wait"))
                                    .level("medium", criteria("Needs attention this week"))
                                    .level("high", criteria("Needs attention today"))
                                    .build())
                    .build();

            StructuredDecisionResponse response = model.decide(request);

            assertThat(response.answers())
                    .containsExactly(
                            Map.entry("refund", answerWithNoul(0.91)),
                            Map.entry("team", answerWithChoice("billing", 0.87)),
                            Map.entry("urgency", answerWithScore(1.7, 0.78)));

            RecordedRequest recorded = server.takeRequest();
            assertThat(recorded.getMethod()).isEqualTo("POST");
            assertThat(recorded.getPath()).isEqualTo("/v1/systemone");
            assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer secret-key");
            assertThat(recorded.getHeader("Accept")).isEqualTo("application/json");
            assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");

            Map<String, Object> json = fromJson(recorded.getBody().readUtf8(), Map.class);
            assertThat(json.get("model")).isEqualTo("jev-latest");
            assertThat(json.get("state")).isEqualTo(request.state());

            Map<String, Object> questions = (Map<String, Object>) json.get("questions");
            assertThat(questions).containsOnlyKeys("refund", "team", "urgency");

            Map<String, Object> refund = (Map<String, Object>) questions.get("refund");
            assertThat(refund.get("type")).isEqualTo("noul");
            assertThat(refund.get("instructions")).isEqualTo("Is a refund requested?");
            assertThat(refund.get("criteria"))
                    .isEqualTo(Map.of(
                            "true",
                            Map.of(
                                    "what", "The customer explicitly asks for money back",
                                    "not_for", "A question about a charge",
                                    "examples", List.of("Refund me"))));

            Map<String, Object> team = (Map<String, Object>) questions.get("team");
            assertThat(team.get("type")).isEqualTo("choice");
            assertThat(team.get("criteria"))
                    .isEqualTo(Map.of(
                            "billing", Map.of("what", "Payment or charge problem"),
                            "support", Map.of("what", "Product usage problem")));

            Map<String, Object> urgency = (Map<String, Object>) questions.get("urgency");
            assertThat(urgency.get("type")).isEqualTo("score");
            assertThat(urgency.get("criteria"))
                    .isEqualTo(List.of(
                            Map.of("level", "low", "what", "Can wait"),
                            Map.of("level", "medium", "what", "Needs attention this week"),
                            Map.of("level", "high", "what", "Needs attention today")));
        }
    }

    @Test
    void should_preserve_unknown_response_fields_as_metadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "model": "jev-latest",
                              "answers": {"answer": {"type": "noul", "noul": 0.5}},
                              "usage": {"input_tokens": 1, "output_tokens": 1},
                              "latency_ms": 17,
                              "diagnostics": {"region": "local"}
                            }
                            """));
            server.start();

            TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                    .apiKey("key")
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state(Map.of("message", "hello"))
                    .question(
                            "answer",
                            NoulQuestion.builder().instructions("Is it true?").build())
                    .build();

            StructuredDecisionResponse response = model.decide(request);
            assertThat(response.answers()).containsExactly(Map.entry("answer", answerWithNoul(0.5)));
            assertThat(response.metadata())
                    .containsExactly(Map.entry("latency_ms", 17), Map.entry("diagnostics", Map.of("region", "local")));
        }
    }

    @Test
    void should_send_text_state_without_rewriting_it() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(
                    new MockResponse().setBody("{" + "\"answers\":{\"answer\":{\"type\":\"noul\",\"noul\":0.5}}}"));
            server.start();
            TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                    .apiKey("key")
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state("hello")
                    .question(
                            "answer",
                            NoulQuestion.builder().instructions("Is it true?").build())
                    .build();

            model.decide(request);
            Map<String, Object> json = fromJson(server.takeRequest().getBody().readUtf8(), Map.class);
            assertThat(json.get("state")).isEqualTo("hello");
        }
    }

    @Test
    void should_reject_unsupported_content_and_request_properties_before_http() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                    .apiKey("key")
                    .baseUrl(server.url("/").toString())
                    .build();
            StructuredDecisionRequest.Builder request = StructuredDecisionRequest.builder()
                    .state("hello")
                    .question(
                            "answer",
                            NoulQuestion.builder().instructions("Is it true?").build());

            assertThatThrownBy(() -> model.decide(request.content(ImageContent.from("aGVsbG8=", "image/png"))
                            .build()))
                    .isInstanceOf(UnsupportedFeatureException.class);
            assertThatThrownBy(() -> model.decide(request.contents(List.of())
                            .parameters(StructuredDecisionRequestParameters.builder()
                                    .additionalProperty("temperature", 0.5)
                                    .build())
                            .build()))
                    .isInstanceOf(UnsupportedFeatureException.class);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_use_per_request_model_override() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"model":"jev-special","answers":{"answer":{"type":"noul","noul":0.5}},
                             "usage":{"input_tokens":1,"output_tokens":1}}
                            """));
            server.start();

            TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                    .apiKey("key")
                    .baseUrl(server.url("/").toString())
                    .modelName("configured-model")
                    .build();
            StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                    .state(Map.of("message", "hello"))
                    .question(
                            "answer",
                            NoulQuestion.builder().instructions("Is it true?").build())
                    .parameters(StructuredDecisionRequestParameters.builder()
                            .modelName("jev-special")
                            .build())
                    .build();

            assertThat(model.decide(request).answers().get("answer").noul()).isEqualTo(0.5);
            Map<String, Object> json = fromJson(server.takeRequest().getBody().readUtf8(), Map.class);
            assertThat(json.get("model")).isEqualTo("jev-special");
            assertThat(model.defaultRequestParameters().modelName()).isEqualTo("configured-model");
        }
    }

    @Test
    void should_validate_required_builder_values() {
        assertThatThrownBy(() -> TypeSafeStructuredDecisionModel.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() ->
                        TypeSafeStructuredDecisionModel.builder().apiKey(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    private static OptionCriteria criteria(String what) {
        return OptionCriteria.builder().what(what).build();
    }

    private static StructuredDecisionAnswer answerWithNoul(double noul) {
        return StructuredDecisionAnswer.builder().noul(noul).build();
    }

    private static StructuredDecisionAnswer answerWithChoice(String choice, double confidence) {
        return StructuredDecisionAnswer.builder()
                .choice(choice)
                .confidence(confidence)
                .build();
    }

    private static StructuredDecisionAnswer answerWithScore(double score, double confidence) {
        return StructuredDecisionAnswer.builder()
                .score(score)
                .confidence(confidence)
                .build();
    }
}
