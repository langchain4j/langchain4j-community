package dev.langchain4j.community.tool.webscraper;

import static dev.langchain4j.http.client.HttpMethod.GET;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Fetches HTML content and converts it to a lightweight Markdown representation.
 */
public final class WebScraperClient {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "section", "article", "header", "main", "aside", "figure", "figcaption", "blockquote", "pre");
    private static final Set<String> LIST_TAGS = Set.of("ul", "ol");
    private static final String NOISE_SELECTOR = "script, style, nav, footer, iframe";

    private final HttpClient httpClient;

    public WebScraperClient() {
        this(HttpClientBuilderLoader.loadHttpClientBuilder().build());
    }

    public WebScraperClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /**
     * Fetches HTML from the provided URL.
     */
    public String fetchHtml(String url) throws IOException, InterruptedException {
        return fetchHtml(URI.create(url));
    }

    /**
     * Fetches HTML from the provided URI.
     */
    public String fetchHtml(URI uri) throws IOException, InterruptedException {
        Objects.requireNonNull(uri, "uri");
        HttpRequest request = HttpRequest.builder()
                .method(GET)
                .url(uri.toString())
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .addHeader("User-Agent", "langchain4j-community-web-scraper")
                .build();
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            return body == null ? "" : body;
        } catch (HttpException e) {
            throw new IOException("Unexpected HTTP status: " + e.statusCode(), e);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException interruptedException) {
                InterruptedException wrapper = new InterruptedException(interruptedException.getMessage());
                wrapper.initCause(interruptedException);
                throw wrapper;
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    /**
     * Fetches HTML from the URL and returns a Markdown representation.
     */
    public String scrapeToMarkdown(String url) throws IOException, InterruptedException {
        return scrapeToMarkdown(URI.create(url));
    }

    /**
     * Fetches HTML from the URI and returns a Markdown representation.
     */
    public String scrapeToMarkdown(URI uri) throws IOException, InterruptedException {
        String html = fetchHtml(uri);
        return htmlToMarkdown(html, uri);
    }

    /**
     * Converts HTML to Markdown without a base URI.
     */
    public String htmlToMarkdown(String html) {
        return htmlToMarkdown(html, null);
    }

    /**
     * Converts HTML to Markdown using the base URI to resolve relative links.
     */
    public String htmlToMarkdown(String html, URI baseUri) {
        Objects.requireNonNull(html, "html");
        Document document = baseUri == null ? Jsoup.parse(html) : Jsoup.parse(html, baseUri.toString());
        document.select(NOISE_SELECTOR).remove();

        Element root = document.body();
        if (root == null) {
            root = document;
        }

        StringBuilder out = new StringBuilder();
        appendChildren(root, out, 0);
        return trimResult(out);
    }

    private static void appendChildren(Element element, StringBuilder out, int listDepth) {
        for (Node child : element.childNodes()) {
            appendNode(child, out, listDepth);
        }
    }

    private static void appendNode(Node node, StringBuilder out, int listDepth) {
        if (node instanceof TextNode textNode) {
            appendText(out, textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase(Locale.ROOT);

        if (isHeading(tag)) {
            appendHeading(element, out, headingLevel(tag));
            return;
        }

        if ("a".equals(tag)) {
            appendLink(element, out);
            return;
        }

        if ("br".equals(tag)) {
            ensureLineBreak(out);
            return;
        }

        if (LIST_TAGS.contains(tag)) {
            appendList(element, out, listDepth + 1);
            return;
        }

        if ("li".equals(tag)) {
            appendListItem(element, out, listDepth);
            return;
        }

        if ("p".equals(tag) || BLOCK_TAGS.contains(tag)) {
            appendParagraphLike(element, out);
            return;
        }

        appendInlineChildren(element, out);
    }

    private static void appendHeading(Element element, StringBuilder out, int level) {
        String content = collectInlineText(element);
        if (content.isBlank()) {
            return;
        }
        ensureBlankLine(out);
        out.append("#".repeat(level)).append(' ').append(content);
        ensureBlankLine(out);
    }

    private static void appendParagraphLike(Element element, StringBuilder out) {
        String content = collectInlineText(element);
        if (content.isBlank()) {
            return;
        }
        ensureBlankLine(out);
        out.append(content);
        ensureBlankLine(out);
    }

    private static void appendList(Element list, StringBuilder out, int listDepth) {
        if (listDepth <= 1) {
            ensureBlankLine(out);
        } else {
            ensureLineBreak(out);
        }
        for (Element child : list.children()) {
            if ("li".equals(child.tagName())) {
                appendListItem(child, out, listDepth);
            }
        }
        ensureLineBreak(out);
    }

    private static void appendListItem(Element item, StringBuilder out, int listDepth) {
        String content = collectInlineTextExcludingLists(item);
        if (!content.isBlank()) {
            appendIndent(out, listDepth);
            out.append("- ").append(content);
            ensureLineBreak(out);
        }

        List<Element> nestedLists = new ArrayList<>();
        for (Node child : item.childNodes()) {
            if (child instanceof Element childElement) {
                String tag = childElement.tagName().toLowerCase(Locale.ROOT);
                if (LIST_TAGS.contains(tag)) {
                    nestedLists.add(childElement);
                }
            }
        }

        for (Element nestedList : nestedLists) {
            appendList(nestedList, out, listDepth + 1);
        }
    }

    private static void appendLink(Element element, StringBuilder out) {
        String text = element.text().trim();
        String href = element.hasAttr("href") ? element.absUrl("href") : "";
        if (href.isEmpty()) {
            href = element.attr("href");
        }

        if (text.isBlank()) {
            text = href;
        }

        if (href.isBlank()) {
            appendText(out, text);
            return;
        }

        appendText(out, "[" + text + "](" + href + ")");
    }

    private static void appendInlineChildren(Element element, StringBuilder out) {
        for (Node child : element.childNodes()) {
            appendInlineNode(child, out);
        }
    }

    private static void appendInlineNode(Node node, StringBuilder out) {
        if (node instanceof TextNode textNode) {
            appendText(out, textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName().toLowerCase(Locale.ROOT);
        if ("a".equals(tag)) {
            appendLink(element, out);
            return;
        }
        if ("br".equals(tag)) {
            ensureLineBreak(out);
            return;
        }
        if (LIST_TAGS.contains(tag)) {
            return;
        }

        for (Node child : element.childNodes()) {
            appendInlineNode(child, out);
        }
    }

    private static String collectInlineText(Element element) {
        StringBuilder buffer = new StringBuilder();
        appendInlineChildren(element, buffer);
        return buffer.toString().trim();
    }

    private static String collectInlineTextExcludingLists(Element element) {
        StringBuilder buffer = new StringBuilder();
        for (Node child : element.childNodes()) {
            if (child instanceof Element childElement) {
                String tag = childElement.tagName().toLowerCase(Locale.ROOT);
                if (LIST_TAGS.contains(tag)) {
                    continue;
                }
            }
            appendInlineNode(child, buffer);
        }
        return buffer.toString().trim();
    }

    private static boolean isHeading(String tag) {
        return tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1));
    }

    private static int headingLevel(String tag) {
        int level = Character.getNumericValue(tag.charAt(1));
        if (level < 1) {
            return 1;
        }
        if (level > 6) {
            return 6;
        }
        return level;
    }

    private static void appendIndent(StringBuilder out, int listDepth) {
        if (listDepth <= 1) {
            return;
        }
        out.append("  ".repeat(listDepth - 1));
    }

    private static void appendText(StringBuilder out, String text) {
        String normalized = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last != '\n' && last != ' ') {
                out.append(' ');
            }
        }
        out.append(normalized);
    }

    private static void ensureLineBreak(StringBuilder out) {
        if (out.length() == 0) {
            return;
        }
        if (out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
    }

    private static void ensureBlankLine(StringBuilder out) {
        if (out.length() == 0) {
            return;
        }
        int newlineCount = 0;
        for (int i = out.length() - 1; i >= 0; i--) {
            if (out.charAt(i) == '\n') {
                newlineCount++;
            } else {
                break;
            }
        }
        if (newlineCount == 0) {
            out.append('\n').append('\n');
        } else if (newlineCount == 1) {
            out.append('\n');
        }
    }

    private static String trimResult(StringBuilder out) {
        String result = out.toString();
        result = result.replaceAll("[ \\t]+(?=\\n)", "");
        return result.strip();
    }
}
