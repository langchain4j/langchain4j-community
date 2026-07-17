package dev.langchain4j.community.data.document.loader.exa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class ExaDocumentLoaderTest {

    @Test
    void shouldLoadDocumentsWithMetadata() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(twoResultsResponseBody())
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture())).thenReturn(response);

        ExaDocumentLoader loader = baseLoader(httpClient);

        List<Document> documents = loader.loadDocuments("langchain");

        assertThat(documents).hasSize(2);

        Document first = documents.get(0);

        assertThat(first.text()).isEqualTo("LangChain is a framework");
        assertThat(first.metadata().getString(Document.URL)).isEqualTo("https://example.com/1");
        assertThat(first.metadata().getString(ExaDocumentLoader.METADATA_TITLE)).isEqualTo("LangChain Intro");
        assertThat(first.metadata().getString(ExaDocumentLoader.METADATA_EXA_ID))
                .isEqualTo("exa-1");
        assertThat(first.metadata().getDouble(ExaDocumentLoader.METADATA_SCORE)).isEqualTo(0.95);

        List<HttpRequest> requests = requestCaptor.getAllValues();

        assertThat(requests).hasSize(1);

        HttpRequest request = requests.get(0);

        assertThat(request.url()).isEqualTo(ExaDocumentLoader.SEARCH_URL);

        assertThat(request.headers().get("x-api-key")).containsExactly("test-key");

        assertThat(request.headers().get("Content-Type")).containsExactly("application/json");

        assertThat(request.body()).contains("\"query\":\"langchain\"");
    }

    @ParameterizedTest
    @MethodSource("documentResponseProvider")
    void shouldLoadDocumentsBasedOnResponse(String responseBody, int expectedSize, String expectedText) {

        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseBody)
                .build();

        when(httpClient.execute(any(HttpRequest.class))).thenReturn(response);

        ExaDocumentLoader loader = baseLoader(httpClient);

        List<Document> documents = loader.loadDocuments("test");

        assertThat(documents).hasSize(expectedSize);

        if (expectedSize > 0) {
            assertThat(documents.get(0).text()).isEqualTo(expectedText);
        }
    }

    @Test
    void shouldThrowWhenApiReturnsNon2xxStatus() {
        HttpClient httpClient = mock(HttpClient.class);

        when(httpClient.execute(any(HttpRequest.class))).thenThrow(new HttpException(401, "Unauthorized"));

        ExaDocumentLoader loader = baseLoader(httpClient);

        assertThatThrownBy(() -> loader.loadDocuments("langchain"))
                .isInstanceOf(ExaDocumentLoaderException.class)
                .hasMessageContaining("status code 401");
    }

    @Test
    void shouldWrapIOExceptionFromHttpClient() {
        HttpClient httpClient = mock(HttpClient.class);

        when(httpClient.execute(any(HttpRequest.class))).thenThrow(new RuntimeException(new IOException("boom")));

        ExaDocumentLoader loader = baseLoader(httpClient);

        assertThatThrownBy(() -> loader.loadDocuments("langchain"))
                .isInstanceOf(ExaDocumentLoaderException.class)
                .hasMessageContaining("Failed to call Exa API");
    }

    @Test
    void shouldValidateBlankQuery() {
        ExaDocumentLoader loader =
                ExaDocumentLoader.builder().apiKey("test-key").build();

        assertThatThrownBy(() -> loader.loadDocuments(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> documentResponseProvider() {
        return Stream.of(
                Arguments.of(highlightOnlyResponseBody(), 1, "Highlight 1\nHighlight 2"),
                Arguments.of(titleOnlyResponseBody(), 1, "Only Title"),
                Arguments.of("{\"results\":[]}", 0, null));
    }

    private static ExaDocumentLoader baseLoader(HttpClient httpClient) {
        return ExaDocumentLoader.builder()
                .apiKey("test-key")
                .httpClient(httpClient)
                .build();
    }

    private static String twoResultsResponseBody() {
        return "{"
                + "\"results\":["
                + "{"
                + "\"id\":\"exa-1\","
                + "\"title\":\"LangChain Intro\","
                + "\"url\":\"https://example.com/1\","
                + "\"text\":\"LangChain is a framework\","
                + "\"score\":0.95,"
                + "\"author\":\"Hari\""
                + "},"
                + "{"
                + "\"id\":\"exa-2\","
                + "\"title\":\"MCP Guide\","
                + "\"url\":\"https://example.com/2\","
                + "\"text\":\"MCP protocol overview\","
                + "\"score\":0.90"
                + "}"
                + "]"
                + "}";
    }

    private static String highlightOnlyResponseBody() {
        return "{"
                + "\"results\":["
                + "{"
                + "\"id\":\"exa-1\","
                + "\"title\":\"Highlights Only\","
                + "\"url\":\"https://example.com/1\","
                + "\"highlights\":["
                + "\"Highlight 1\","
                + "\"Highlight 2\""
                + "]"
                + "}"
                + "]"
                + "}";
    }

    private static String titleOnlyResponseBody() {
        return "{"
                + "\"results\":["
                + "{"
                + "\"id\":\"exa-1\","
                + "\"title\":\"Only Title\","
                + "\"url\":\"https://example.com/1\""
                + "}"
                + "]"
                + "}";
    }
}
