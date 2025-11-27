package dev.langchain4j.community.web.search.duckduckgo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineTest {

    private static void testURI(String uriString) {
        assertDoesNotThrow(() -> DuckDuckGoWebSearchEngine.makeURI(uriString));
        final URI uri = DuckDuckGoWebSearchEngine.makeURI(uriString);
        assertNotNull(uri);
        if (uriString.matches(".*\\s+.*")) {
            assertNotEquals(uriString, uri.toString());
        } else {
            assertEquals(uriString, uri.toString());
        }
    }

    @Test
    void test_malformed_urls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> URI.create(
                        "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."));
        assertDoesNotThrow(
                () -> URI.create(
                        "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j"));
        assertDoesNotThrow(() -> URI.create(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."
                        .replaceAll(" ", "%20")));
        assertDoesNotThrow(() -> URI.create(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries."
                        .replaceAll("\\s+", "%20")));
        assertDoesNotThrow(() -> URI.create(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f"));
    }

    @Test
    void test_make_uri() {
        assertThrows(IllegalArgumentException.class, () -> DuckDuckGoWebSearchEngine.makeURI(null));
        assertThrows(IllegalArgumentException.class, () -> DuckDuckGoWebSearchEngine.makeURI(""));
        assertThrows(IllegalArgumentException.class, () -> DuckDuckGoWebSearchEngine.makeURI("  \\t"));
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j");
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries.");
        testURI("https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f");
    }
}
