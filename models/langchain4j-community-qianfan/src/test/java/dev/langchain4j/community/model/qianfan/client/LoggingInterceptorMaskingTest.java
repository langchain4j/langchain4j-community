package dev.langchain4j.community.model.qianfan.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.qianfan.client.chat.ChatTokenResponse;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class LoggingInterceptorMaskingTest {

    @Test
    void should_mask_secret_headers_by_name_regardless_of_value_format() {
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer bce-v3/ALTAK-secret-api-key")
                .add("api-key", "secret-api-key")
                .add("X-Bce-Signature", "secret-signature")
                .add("Set-Cookie", "BAIDUID=secret-cookie")
                .add("Content-Type", "application/json")
                .build();

        String logged = RequestLoggingInterceptor.inOneLine(headers);

        assertThat(logged)
                .doesNotContain("secret-api-key", "secret-signature", "secret-cookie")
                .contains("[Authorization: Beare...ey]")
                .contains("[Content-Type: application/json]");
    }

    @Test
    void should_mask_credentials_in_token_request_url() {
        HttpUrl url = HttpUrl.get("https://aip.baidubce.com/oauth/2.0/token"
                + "?grant_type=client_credentials&client_id=secret-client-id&client_secret=secret-client-secret");

        String logged = RequestLoggingInterceptor.maskUrl(url);

        assertThat(logged)
                .doesNotContain("secret-client-id", "secret-client-secret")
                .isEqualTo("https://aip.baidubce.com/oauth/2.0/token"
                        + "?grant_type=client_credentials&client_id=...&client_secret=secre...et");
    }

    @Test
    void should_mask_access_token_in_request_url() {
        HttpUrl url = HttpUrl.get(
                "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions?access_token=secret-access-token");

        String logged = RequestLoggingInterceptor.maskUrl(url);

        assertThat(logged)
                .doesNotContain("secret-access-token")
                .isEqualTo(
                        "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions?access_token=...");
    }

    @Test
    void should_show_only_first_and_last_characters_of_secrets_of_20_or_more_characters() {
        assertThat(RequestLoggingInterceptor.maskSecret("abcdefg")).isEqualTo("...");
        assertThat(RequestLoggingInterceptor.maskSecret("0123456789abcdefghi")).isEqualTo("...");
        assertThat(RequestLoggingInterceptor.maskSecret("0123456789abcdefghij")).isEqualTo("01234...ij");
    }

    @Test
    void should_keep_url_without_query_unchanged() {
        HttpUrl url = HttpUrl.get("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/embedding-v1");

        assertThat(RequestLoggingInterceptor.maskUrl(url)).isEqualTo(url.toString());
    }

    @Test
    void should_mask_tokens_in_token_response_body() {
        String body = "{\"refresh_token\":\"secret-refresh-token\",\"expires_in\":2592000,"
                + "\"session_key\":\"secret-session-key\",\"access_token\":\"secret-access-token\","
                + "\"scope\":\"public brain_all_scope\",\"session_secret\":\"secret-session-secret\"}";

        String logged = ResponseLoggingInterceptor.maskSecretFields(body);

        assertThat(logged)
                .doesNotContain(
                        "secret-refresh-token", "secret-session-key", "secret-access-token", "secret-session-secret")
                .isEqualTo("{\"refresh_token\":\"secre...en\",\"expires_in\":2592000,"
                        + "\"session_key\":\"...\",\"access_token\":\"...\","
                        + "\"scope\":\"public brain_all_scope\",\"session_secret\":\"secre...et\"}");
    }

    @Test
    void should_not_change_body_without_secret_fields() {
        String body = "{\"id\":\"as-123\",\"result\":\"line 1\\nline \\\"2\\\"\",\"usage\":{\"total_tokens\":5}}";

        assertThat(ResponseLoggingInterceptor.maskSecretFields(body)).isEqualTo(body);
    }

    @Test
    void should_mask_body_with_long_strings_and_many_escapes() {
        String longResult = "line\\n".repeat(100_000);
        String body = "{\"result\":\"" + longResult + "\",\"access_token\":\"secret-access-token\"}";

        String logged = ResponseLoggingInterceptor.maskSecretFields(body);

        assertThat(logged).doesNotContain("secret-access-token").contains(longResult);
    }

    @Test
    void token_response_toString_should_not_contain_tokens() {
        ChatTokenResponse response = new ChatTokenResponse();
        response.setRefreshToken("secret-refresh-token");
        response.setSessionKey("secret-session-key");
        response.setAccessToken("secret-access-token");
        response.setSessionSecret("secret-session-secret");
        response.setScope("public");
        response.setExpiresIn(2592000);

        assertThat(response.toString())
                .doesNotContain(
                        "secret-refresh-token", "secret-session-key", "secret-access-token", "secret-session-secret")
                .contains("accessToken=***", "scope='public'", "expiresIn=2592000");
    }

    @Test
    void token_response_toString_should_render_missing_tokens_as_null() {
        assertThat(new ChatTokenResponse().toString()).contains("accessToken=null", "refreshToken=null");
    }
}
