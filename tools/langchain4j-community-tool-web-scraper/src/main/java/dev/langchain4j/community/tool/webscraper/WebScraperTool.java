package dev.langchain4j.community.tool.webscraper;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.Objects;

/**
 * A tool that fetches a web page and converts it to a lightweight Markdown representation.
 */
public class WebScraperTool {

    private final WebScraperClient client;

    /**
     * Creates a tool with the default {@link WebScraperClient}.
     */
    public WebScraperTool() {
        this(new WebScraperClient());
    }

    /**
     * Creates a tool with a provided {@link WebScraperClient}.
     *
     * @param client the client used to fetch and convert HTML
     */
    public WebScraperTool(WebScraperClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Fetches the URL and returns a Markdown summary of the page content.
     *
     * @param url the URL to scrape
     * @return Markdown text or a user-friendly error message
     */
    @Tool("Scrape a web page and return Markdown text.")
    public String scrapeUrl(@P("The URL to scrape") String url) {
        try {
            return client.scrapeToMarkdown(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Scraping was interrupted.";
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                return "Error: Failed to fetch URL.";
            }
            return "Error: Failed to fetch URL. " + message;
        }
    }
}
