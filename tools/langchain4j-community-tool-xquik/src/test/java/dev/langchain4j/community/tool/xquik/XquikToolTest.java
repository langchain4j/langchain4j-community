package dev.langchain4j.community.tool.xquik;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class XquikToolTest {

    @Test
    void searchTweets_formatsBoundedUntrustedContentAndCursor() throws Exception {
        String longText = "A  post\nwith   spaces " + "x".repeat(600);
        String response = """
                {
                  "tweets": [{
                    "id": "123",
                    "text": "%s",
                    "like_count": 4,
                    "retweet_count": 3,
                    "reply_count": 2,
                    "author": {"username": "java"}
                  }],
                  "has_more": true,
                  "next_cursor": "next-page"
                }
                """.formatted(longText.replace("\n", "\\n"));
        try (TestServer server = startServer(200, response)) {
            XquikTool tool = tool(server);

            String result = tool.searchTweets("langchain4j", "Latest", 10, null);

            assertThat(result)
                    .startsWith("Untrusted public X/Twitter content:")
                    .contains("- @java: A post with spaces")
                    .contains("[likes=4, reposts=3, replies=2]")
                    .contains("https://x.com/java/status/123")
                    .endsWith("Next cursor: next-page");
            assertThat(result).hasSizeLessThan(800);
        }
    }

    @Test
    void searchTweets_returnsNoTweetsForEmptyPage() throws Exception {
        try (TestServer server = startServer(200, "{\"tweets\":[],\"has_more\":false,\"next_cursor\":\"\"}")) {
            XquikTool tool = tool(server);

            assertThat(tool.searchTweets("nothing", null, null, null)).isEqualTo("No tweets found.");
        }
    }

    @Test
    void getUserTweets_usesSameAgentFriendlyFormat() throws Exception {
        String response = """
                {"tweets":[{
                  "id":"456",
                  "text":"A recent post",
                  "url":"https://x.com/example/status/456",
                  "author":{"username":"example"}
                }],"has_more":false,"next_cursor":""}
                """;
        try (TestServer server = startServer(200, response)) {
            XquikTool tool = tool(server);

            String result = tool.getUserTweets("example", false, 5, null);

            assertThat(result)
                    .contains("Untrusted public X/Twitter content:")
                    .contains("@example: A recent post")
                    .contains("https://x.com/example/status/456")
                    .doesNotContain("Next cursor:");
        }
    }

    @Test
    void checkFollow_formatsBothDirections() throws Exception {
        String response = """
                {"is_following":true,"is_followed_by":false,"source_username":"alice","target_username":"bob"}
                """;
        try (TestServer server = startServer(200, response)) {
            XquikTool tool = tool(server);

            String result = tool.checkFollow("alice", "bob");

            assertThat(result)
                    .isEqualTo("@alice follows @bob: true" + System.lineSeparator() + "@bob follows @alice: false");
        }
    }

    @Test
    void downloadTweetMedia_formatsGalleryResult() throws Exception {
        String response = """
                {"tweet_id":"789","gallery_url":"https://xquik.com/gallery/abc","cache_hit":true}
                """;
        try (TestServer server = startServer(200, response)) {
            XquikTool tool = tool(server);

            String result = tool.downloadTweetMedia("789");

            assertThat(result)
                    .contains("Tweet: 789")
                    .contains("Media gallery: https://xquik.com/gallery/abc")
                    .contains("Cache hit: true");
        }
    }

    @Test
    void returnsActionableErrors() throws Exception {
        try (TestServer server = startServer(429, "{\"message\":\"Too many requests\"}")) {
            XquikTool tool = tool(server);

            assertThat(tool.searchTweets("java", null, null, null))
                    .isEqualTo("Error: Xquik request failed: HTTP 429. Rate limit exceeded. Retry later.");
        }
    }

    @Test
    void everyToolReturnsAuthenticationGuidance() throws Exception {
        try (TestServer server = startServer(401, "{\"message\":\"secret-bearing detail\"}")) {
            XquikTool tool = tool(server);
            String expected = "Error: Xquik request failed: HTTP 401. Authentication failed. Check the API key.";

            assertThat(tool.checkFollow("alice", "bob")).isEqualTo(expected);
            assertThat(tool.downloadTweetMedia("123")).isEqualTo(expected);
        }
    }

    @Test
    void buildsWithDefaultBaseUrlWithoutCallingApi() {
        XquikTool tool = XquikTool.builder().apiKey("test-key").build();

        assertThat(tool).isNotNull();
    }

    @Test
    void returnsValidationErrorsWithoutCallingApi() throws Exception {
        try (TestServer server = startServer(200, "{\"tweets\":[]}")) {
            XquikTool tool = tool(server);

            assertThat(tool.searchTweets("java", "popular", 10, null))
                    .isEqualTo("Error: queryType must be Latest or Top");
            assertThat(tool.getUserTweets("user", false, 101, null))
                    .isEqualTo("Error: limit must be between 1 and 100");
        }
    }

    private static XquikTool tool(TestServer server) {
        return XquikTool.builder()
                .apiKey("test-key")
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return new TestServer(server, executor);
    }

    private record TestServer(HttpServer server, ExecutorService executor) implements AutoCloseable {

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
