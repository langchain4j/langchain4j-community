package dev.langchain4j.community.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import java.net.URI;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineTest {

    private static void testURI(String uriString) {
        assertThatCode(() -> DuckDuckGoWebSearchEngine.makeURI(uriString)).doesNotThrowAnyException();
        final URI uri = DuckDuckGoWebSearchEngine.makeURI(uriString);
        assertThat(uri).isNotNull();
        if (uriString.matches(".*\\s+.*")) {
            assertThat(uri.toString()).isNotEqualTo(uriString);
        } else {
            assertThat(uri).hasToString(uriString);
        }
    }

    @Test
    void should_malformed_urls() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> URI.create(
                                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."));
        assertThatCode(
                        () -> URI.create(
                                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j"))
                .doesNotThrowAnyException();
        assertThatCode(() -> URI.create(
                        "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."
                                .replaceAll(" ", "%20")))
                .doesNotThrowAnyException();
        assertThatCode(() -> URI.create(
                        "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."
                                .replaceAll("\\s+", "%20")))
                .doesNotThrowAnyException();
        assertThatCode(
                        () -> URI.create(
                                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_make_uri() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DuckDuckGoWebSearchEngine.makeURI(null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DuckDuckGoWebSearchEngine.makeURI(""));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DuckDuckGoWebSearchEngine.makeURI("  \\t"));
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j");
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries.");
        testURI("https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f");
    }
}
