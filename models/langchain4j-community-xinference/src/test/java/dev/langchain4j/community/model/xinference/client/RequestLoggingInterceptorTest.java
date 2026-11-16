package dev.langchain4j.community.model.xinference.client;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.Headers;
import org.junit.jupiter.api.Test;

class RequestLoggingInterceptorTest {

    @Test
    void should_mask_secret_headers_by_name() {
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer secret-api-key")
                .add("Proxy-Authorization", "Basic secret-proxy-credentials")
                .add("X-Goog-Api-Key", "secret-goog-key")
                .add("X-Session-Id", "secret-session")
                .add("X-Amz-Signature", "secret-signature")
                .add("Set-Cookie", "sid=secret-cookie")
                .add("Content-Type", "application/json")
                .build();

        String logged = RequestLoggingInterceptor.getHeaders(headers);

        assertThat(logged)
                .doesNotContain(
                        "secret-api-key",
                        "secret-proxy-credentials",
                        "secret-goog-key",
                        "secret-session",
                        "secret-signature",
                        "secret-cookie")
                .contains("[Authorization: Beare...ey]")
                .contains("[Content-Type: application/json]");
    }

    @Test
    void should_show_only_first_and_last_characters_of_secrets_of_20_or_more_characters() {
        Headers headers = new Headers.Builder()
                .add("X-Api-Key", "abcdefg")
                .add("X-Auth-Token", "0123456789abcdefghi")
                .add("Authorization", "0123456789abcdefghij")
                .build();

        assertThat(RequestLoggingInterceptor.getHeaders(headers))
                .isEqualTo("[X-Api-Key: ...], [X-Auth-Token: ...], [Authorization: 01234...ij]");
    }

    @Test
    void should_mask_short_secret_completely() {
        Headers headers = new Headers.Builder().add("X-Auth-Token", "abc").build();

        assertThat(RequestLoggingInterceptor.getHeaders(headers)).isEqualTo("[X-Auth-Token: ...]");
    }
}
