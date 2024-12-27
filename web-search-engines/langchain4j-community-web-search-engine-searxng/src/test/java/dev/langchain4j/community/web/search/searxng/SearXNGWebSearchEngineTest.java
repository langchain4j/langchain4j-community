package dev.langchain4j.community.web.search.searxng;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SearXNGWebSearchEngineTest {

    private static void testURI(String uriString) {
        assertDoesNotThrow(() -> SearXNGWebSearchEngine.makeURI(uriString));
        final URI uri = SearXNGWebSearchEngine.makeURI(uriString);
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
        assertThrows(IllegalArgumentException.class, () -> SearXNGWebSearchEngine.makeURI(null));
        assertThrows(IllegalArgumentException.class, () -> SearXNGWebSearchEngine.makeURI(""));
        assertThrows(IllegalArgumentException.class, () -> SearXNGWebSearchEngine.makeURI("  \\t"));
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j");
        testURI(
                "https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f#:~:text=LangChain4j is a groundbreaking Java,by Python and JavaScript libraries.");
        testURI("https://www.linkedin.com/pulse/introduction-langchain4j-supercharging-java-llms-ibrahim-jimoh-kyj4f");
    }
}
