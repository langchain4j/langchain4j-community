package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Json.fromJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class MultimodalSystemOneModelsTest {

    @Test
    void openjev_sends_inline_image_data_urls() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.7}}}"));
            server.start();
            OpenJevStructuredDecisionModel model = OpenJevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            var response = model.decide(request().content(ImageContent.from("aGVsbG8=", "image/png")).build());

            assertThat(response.answers().get("q").value()).isEqualTo(0.7);
            Map<String, Object> json = fromJson(server.takeRequest().getBody().readUtf8(), Map.class);
            assertThat(json.get("images")).isEqualTo(java.util.List.of("data:image/png;base64,aGVsbG8="));
        }
    }

    @Test
    void djev_supports_mixed_span_questions_and_json_images() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"answers":{
                      "valid":{"type":"noul","noul":0.8},
                      "id":{"type":"span","found":true,"text":"A-1042","start":9,"end":15,
                            "confidence":0.87,"coverage":0.99},
                      "dates":{"type":"spans","found":true,"items":[
                            {"text":"2024-03-15","start":64,"end":74,"confidence":0.75,
                             "coverage":0.93}],"reads":2}}}
                    """));
            server.start();
            DjevStructuredDecisionModel model = DjevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            var response = model.decide(request()
                    .question("id", new SpanQuestion("Invoice ID", null))
                    .question("dates", new SpansQuestion("Dates", null, null))
                    .content(ImageContent.from("aGVsbG8=", "image/jpeg"))
                    .build());

            assertThat(response.answers().get("id")).isInstanceOf(SpanAnswer.class);
            assertThat(((SpanAnswer) response.answers().get("id")).value().text()).isEqualTo("A-1042");
            assertThat(response.answers().get("id").metadata()).containsEntry("coverage", 0.99);
            assertThat(((SpansAnswer) response.answers().get("dates")).value().items())
                    .extracting(SpanAnswer.SpanValue::text).containsExactly("2024-03-15");
            assertThat(((SpansAnswer) response.answers().get("dates")).value().items().get(0).confidence())
                    .isEqualTo(0.75);
            assertThat(((SpansAnswer) response.answers().get("dates")).value().items().get(0).metadata())
                    .containsEntry("coverage", 0.93);
            Map<String, Object> json = fromJson(server.takeRequest().getBody().readUtf8(), Map.class);
            assertThat(json.get("images")).isEqualTo(java.util.List.of("data:image/jpeg;base64,aGVsbG8="));
            assertThat(((Map<?, ?>) ((Map<?, ?>) json.get("questions")).get("id")).get("type"))
                    .isEqualTo("span");
        }
    }

    @Test
    void djev_multipart_sends_request_json_and_image_file() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.7}}}"));
            server.start();
            DjevStructuredDecisionModel model = DjevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).multipart(true).build();
            model.decide(request().content(ImageContent.from("aGVsbG8=", "image/png")).build());

            var posted = server.takeRequest();
            assertThat(posted.getHeader("Content-Type")).startsWith("multipart/form-data");
            assertThat(posted.getBody().readUtf8())
                    .contains("name=\"request\"")
                    .contains("name=\"image0\"")
                    .contains("filename=\"image0.png\"");
        }
    }

    @Test
    void rejects_unsupported_image_mime_type_before_http() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            OpenJevStructuredDecisionModel model = OpenJevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            assertThatThrownBy(() -> model.decide(request()
                    .content(ImageContent.from("aGVsbG8=", "image/gif")).build()))
                    .isInstanceOf(UnsupportedFeatureException.class);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void rejects_malformed_span_offsets_instead_of_truncating_them() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("""
                    {"answers":{"id":{"type":"span","found":true,"text":"A","start":1.5,"end":2}}}
                    """));
            server.start();
            DjevStructuredDecisionModel model = DjevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            assertThatThrownBy(() -> model.decide(StructuredDecisionRequest.builder()
                    .state("A")
                    .question("id", new SpanQuestion("Find A", null))
                    .build())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void openjev_rejects_a_ninth_image_before_http() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            OpenJevStructuredDecisionModel model = OpenJevStructuredDecisionModel.builder()
                    .baseUrl(server.url("/").toString()).build();
            StructuredDecisionRequest.Builder request = request();
            for (int i = 0; i < 9; i++) {
                request.content(ImageContent.from("aGVsbG8=", "image/png"));
            }
            assertThatThrownBy(() -> model.decide(request.build()))
                    .isInstanceOf(UnsupportedFeatureException.class);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    private static StructuredDecisionRequest.Builder request() {
        return StructuredDecisionRequest.builder()
                .state("Invoice #A-1042")
                .question("valid", NoulQuestion.builder().instructions("Valid?").build())
                .question("q", NoulQuestion.builder().instructions("True?").build());
    }
}
