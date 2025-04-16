package dev.langchain4j.community.data.document.parser.llamaparse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LLAMA_PARSE_API_KEY", matches = ".+")
class LlamaParseClientIT {

    private static final Logger log = LoggerFactory.getLogger(LlamaParseClientIT.class);

    @Test
    void shouldParseUploadAndGetMarkdown() {
        String API_KEY = System.getenv("LLAMA_PARSE_API_KEY");

        assertThat(API_KEY).isNotNull().isNotBlank();

        LlamaParseClient client = new LlamaParseClient(API_KEY);

        Path path = toPath("files/sample.pdf");
        String parsingInstructions = "The provided document is a PDF sample containing Lorem Ipsum text.";

        log.info("Uploading the file...");
        LlamaParseResponse responseBody = client.upload(path, parsingInstructions);
        String jobId = responseBody.id;
        String status = responseBody.status;

        assertThat(jobId).isNotBlank();
        // assertThat(status).isEqualTo("SUCCESS");

        log.info("Waiting for parsing...");
        with().pollInterval(Duration.ofSeconds(3))
                .await("check success status")
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(client.jobStatus(jobId).status).isEqualTo("SUCCESS"));

        log.info("Getting markdown result...");
        LlamaParseMarkdownResponse response = client.markdownResult(jobId);
        String markdown = response.markdown;
        assertThat(markdown.length()).isGreaterThan(0);

        log.info("Test completed...");
    }

    private Path toPath(String fileName) {
        try {
            return Paths.get(getClass().getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
