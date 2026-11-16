package dev.langchain4j.community.web.search.brave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void engineBuilder_toString_should_mask_api_key() {
        String toString =
                BraveWebSearchEngine.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void engineBuilder_toString_should_render_null_api_key_as_null() {
        String toString = BraveWebSearchEngine.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void builder_toString_should_mask_secrets_in_base_url() {
        String toString = BraveWebSearchEngine.builder()
                .baseUrl("https://example.test/search?token=secret-token")
                .toString();

        assertThat(toString).doesNotContain("secret-token").contains("token=********");
    }

    @Test
    void clientBuilder_toString_should_mask_api_key() {
        String toString = BraveClient.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void clientBuilder_toString_should_render_null_api_key_as_null() {
        String toString = BraveClient.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void should_mask_secret_headers_by_name() {
        Map<String, List<String>> headers = Map.of(
                "X-Subscription-Token", List.of("secret-api-key"),
                "Set-Cookie", List.of("a=secret-cookie-1", "b=secret-cookie-2"),
                "X-Session-Id", List.of("secret-session"),
                "X-Request-Signature", List.of("secret-signature"),
                "X-Client-Key", List.of("secret-client-key"),
                "Content-Type", List.of("application/json"));

        Map<String, List<String>> masked = BraveLoggingHttpClient.maskHeaders(headers);

        assertThat(masked)
                .containsOnly(
                        entry("X-Subscription-Token", List.of("********")),
                        entry("Set-Cookie", List.of("********", "********")),
                        entry("X-Session-Id", List.of("********")),
                        entry("X-Request-Signature", List.of("********")),
                        entry("X-Client-Key", List.of("********")),
                        entry("Content-Type", List.of("application/json")));
    }
}
