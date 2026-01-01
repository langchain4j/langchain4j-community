package dev.langchain4j.community.tool.webscraper;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class WebScraperToolIT {

    private static final Logger logger = LoggerFactory.getLogger(WebScraperToolIT.class);

    private final OpenAiChatModel model = buildModel(false);
    private final OpenAiChatModel strictToolsModel = buildModel(true);

    private HttpServer server;

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @AfterEach
    void tearDown() {
        stopServer();
    }

    @Test
    void should_scrape_page_with_links_and_lists() throws IOException {
        String heading = "WEB_SCRAPER_HEADING_" + UUID.randomUUID();
        String itemOne = "ITEM_ONE_" + UUID.randomUUID();
        String itemTwo = "ITEM_TWO_" + UUID.randomUUID();
        String linkText = "DOCS_" + UUID.randomUUID();
        String scriptToken = "SCRIPT_" + UUID.randomUUID();
        String navToken = "NAV_" + UUID.randomUUID();
        String html = "<html><body>"
                + "<script>" + scriptToken + "</script>"
                + "<nav>" + navToken + "</nav>"
                + "<h1>" + heading + "</h1>"
                + "<p>Intro paragraph.</p>"
                + "<ul><li>" + itemOne + "</li><li>" + itemTwo + "</li></ul>"
                + "<p><a href=\"/docs\">" + linkText + "</a></p>"
                + "</body></html>";

        String baseUrl = startServer(new Route("/page", 200, html));
        String url = baseUrl + "/page";

        Result<String> result = assistantWithStrictTools()
                .chat("Use the web scraper tool to fetch " + url + ". "
                        + "Return the heading text, the list items, and the link URL.");

        ToolExecution toolExecution = toolExecutionForUrl(result, url);
        assertThat(toolExecution.request().name()).isEqualTo("scrapeUrl");
        assertThat(toolExecution.request().arguments()).contains(url);
        assertThat(toolExecution.result()).contains(heading, itemOne, itemTwo, baseUrl + "/docs");
        assertThat(toolExecution.result()).doesNotContain(scriptToken, navToken);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_scrape_two_pages_and_return_both_headings() throws IOException {
        String headingOne = "HEADING_ONE_" + UUID.randomUUID();
        String headingTwo = "HEADING_TWO_" + UUID.randomUUID();
        String htmlOne = "<html><body><h1>" + headingOne + "</h1></body></html>";
        String htmlTwo = "<html><body><h1>" + headingTwo + "</h1></body></html>";

        String baseUrl = startServer(new Route("/page-one", 200, htmlOne), new Route("/page-two", 200, htmlTwo));
        String urlOne = baseUrl + "/page-one";
        String urlTwo = baseUrl + "/page-two";

        Result<String> result = assistantWithStrictTools()
                .chat("Use the web scraper tool to fetch these URLs:\n"
                        + urlOne + "\n"
                        + urlTwo + "\n"
                        + "Return the two headings separated by a comma.");

        assertThat(result.toolExecutions()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.toolExecutions())
                .extracting(execution -> execution.request().arguments())
                .anySatisfy(arguments -> assertThat(arguments).contains(urlOne))
                .anySatisfy(arguments -> assertThat(arguments).contains(urlTwo));
        assertThat(result.toolExecutions())
                .extracting(ToolExecution::result)
                .anySatisfy(text -> assertThat(text).contains(headingOne))
                .anySatisfy(text -> assertThat(text).contains(headingTwo));
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_not_found() throws IOException {
        String baseUrl = startServer(new Route("/missing", 404, "<html><body>Not Found</body></html>"));
        String url = baseUrl + "/missing";

        Result<String> result = assistantWithStrictTools()
                .chat("Use the web scraper tool to fetch " + url + ". " + "Return the tool output only.");

        ToolExecution toolExecution = toolExecutionForUrl(result, url);
        assertThat(toolExecution.result())
                .contains("Error: Failed to fetch URL")
                .contains("404");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_server_error() throws IOException {
        String baseUrl = startServer(new Route("/boom", 500, "<html><body>Server Error</body></html>"));
        String url = baseUrl + "/boom";

        Result<String> result = assistantWithStrictTools()
                .chat("Use the web scraper tool to fetch " + url + ". " + "Return the tool output only.");

        ToolExecution toolExecution = toolExecutionForUrl(result, url);
        assertThat(toolExecution.result())
                .contains("Error: Failed to fetch URL")
                .contains("500");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_not_call_tool_for_unrelated_question() {
        Result<String> result = assistant().chat("Do not call any tools. Answer only with the number: 2 + 2 = ?");

        assertThat(result.toolExecutions()).isEmpty();
        assertThat(result.content()).contains("4");
        logger.info(result.content());
    }

    private Assistant assistant() {
        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new WebScraperTool())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private Assistant assistantWithStrictTools() {
        return AiServices.builder(Assistant.class)
                .chatModel(strictToolsModel)
                .tools(new WebScraperTool())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    private ToolExecution toolExecutionForUrl(Result<String> result, String url) {
        assertThat(result.toolExecutions()).isNotEmpty();
        return result.toolExecutions().stream()
                .filter(execution -> execution.request().arguments() != null
                        && execution.request().arguments().contains(url))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tool execution for url: " + url));
    }

    private OpenAiChatModel buildModel(boolean strictTools) {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(strictTools)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private String startServer(Route... routes) throws IOException {
        stopServer();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        for (Route route : routes) {
            String body = route.body == null ? "" : route.body;
            server.createContext(route.path, exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(route.status, bytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(bytes);
                }
            });
        }
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static final class Route {
        private final String path;
        private final int status;
        private final String body;

        private Route(String path, int status, String body) {
            this.path = path;
            this.status = status;
            this.body = body;
        }
    }
}
