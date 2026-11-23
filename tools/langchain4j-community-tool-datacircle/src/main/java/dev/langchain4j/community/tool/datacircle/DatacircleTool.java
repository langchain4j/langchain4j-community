package dev.langchain4j.community.tool.datacircle;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Agent tools for the open <a href="https://datacircle.dev">Datacircle</a> API, which benchmarks the GTM APIs
 * (email finders, company enrichment, LinkedIn data and more) and ranks every plan of each by its score out of 100
 * and its price per 1,000.
 *
 * <p>The API needs no key and no account. It allows 60 calls a minute from one address. Its spec is at
 * {@code https://api.datacircle.dev/v1/openapi.json}.
 *
 * <pre>{@code
 * DatacircleTool tool = DatacircleTool.builder().build();
 * }</pre>
 */
public final class DatacircleTool {

    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_PROVIDER_APIS = 100;

    private final DatacircleClient client;

    DatacircleTool(DatacircleClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new Datacircle tool builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lists the GTM APIs Datacircle ranks, each with its slug, its counts and its best plan.
     *
     * @param query words that must all appear in the API's name or slug
     * @return agent-friendly API summaries
     */
    @Tool("List the GTM APIs that Datacircle ranks (email finder, company enrichment, LinkedIn profile and more), "
            + "each with its slug, how many plans and providers it has, and its best plan.")
    public String listApis(
            @P(
                            name = "query",
                            value = "Words that must all appear in the API's name or slug, like email or linkedin. "
                                    + "Leave empty to list every API",
                            required = false)
                    String query) {
        try {
            return formatApis(client.listApis(), query);
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads one API's best plans, best first by score.
     *
     * @param api the API's slug
     * @param limit number of plans
     * @return agent-friendly ranked plans
     */
    @Tool("Get the best plans of one GTM API, best first by Datacircle's score out of 100, "
            + "with each plan's price per 1,000 and its page.")
    public String getBestPlans(
            @P(name = "api", value = "The API's slug, as listApis gives it, like email-finder") String api,
            @P(name = "limit", value = "Number of plans from 1 through 50. Defaults to 10", required = false)
                    Integer limit) {
        try {
            return formatBestPlans(client.getBestPlans(api, limit));
        } catch (RuntimeException e) {
            return formatError(e) + (isStatus(e, 404) ? " Call listApis for every API's slug." : "");
        }
    }

    /**
     * Searches the plans of every API, or of one, by provider or plan name.
     *
     * @param api the API's name or slug
     * @param query words in the provider's or plan's name
     * @param sort score, provider or price
     * @param limit number of plans
     * @param offset number of found plans to skip
     * @return agent-friendly plan list
     */
    @Tool("Search the plans of the GTM APIs on Datacircle by API, and by words in the provider's or plan's name, "
            + "sorted by score (best first), provider (A to Z) or price (lowest $ per 1,000 first).")
    public String searchPlans(
            @P(
                            name = "api",
                            value = "The API's name or slug, like email-finder. A provider's name goes in query. "
                                    + "Defaults to every API",
                            required = false)
                    String api,
            @P(
                            name = "query",
                            value = "Words that must all appear in the provider's or plan's name, like hunter",
                            required = false)
                    String query,
            @P(name = "sort", value = "Sort: score, provider or price. Defaults to score", required = false)
                    String sort,
            @P(name = "limit", value = "Number of plans from 1 through 50. Defaults to 10", required = false)
                    Integer limit,
            @P(name = "offset", value = "Number of found plans to skip first. Defaults to 0", required = false)
                    Integer offset) {
        try {
            return formatSearch(client.searchPlans(api, query, sort, limit, offset), offset == null ? 0 : offset);
        } catch (RuntimeException e) {
            boolean unknownApi = api != null && !api.isBlank() && (isStatus(e, 400) || isStatus(e, 404));
            return formatError(e) + (unknownApi ? " Call listApis for every API's name and slug." : "");
        }
    }

    /**
     * Reads one provider's rank on each API it sells.
     *
     * @param provider the provider's name or slug
     * @return agent-friendly provider ranks
     */
    @Tool("Get one provider's rank on each GTM API it sells on Datacircle, with its best plan there. "
            + "A provider is a company that sells the APIs, like Hunter or Bright Data.")
    public String getProvider(
            @P(name = "provider", value = "The provider's name or slug, like Bright Data or bright-data")
                    String provider) {
        try {
            return formatProvider(client.getProvider(provider));
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    private static String formatApis(JsonNode apis, String query) {
        if (!apis.isArray()) {
            throw unexpected("the list of APIs is not a list");
        }
        if (apis.isEmpty()) {
            return "The Datacircle API returned no APIs.";
        }
        List<String> words = words(query);
        List<String> lines = new ArrayList<>();
        for (JsonNode api : apis) {
            String name = required(api, "api");
            String slug = required(api, "slug");
            if (!matchesAll(name + " " + slug, words)) {
                continue;
            }
            StringBuilder line = new StringBuilder("- ")
                    .append(truncate(name))
                    .append(" (")
                    .append(slug)
                    .append("): ")
                    .append(count(api.path("plans"), "plan"))
                    .append(" from ")
                    .append(count(api.path("providers"), "provider"))
                    .append('.');
            JsonNode best = api.path("best");
            if (best.isObject()) {
                line.append(" Best: ")
                        .append(truncate(required(best, "provider")))
                        .append(", ")
                        .append(truncate(required(best, "plan")))
                        .append(", ")
                        .append(score(best.path("score")))
                        .append(", ")
                        .append(price(best.path("perThousand"), null))
                        .append('.');
            }
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            return "No API's name or slug holds every word of \"" + query.trim() + "\". Leave the query empty to list "
                    + apis.size() + " APIs.";
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator() + lines.size() + " of " + apis.size()
                + " APIs. Pass a slug to getBestPlans or searchPlans.";
    }

    private static String formatBestPlans(JsonNode response) {
        JsonNode plans = list(response, "plans");
        StringBuilder result = new StringBuilder()
                .append(required(response, "api"))
                .append(" (")
                .append(required(response, "slug"))
                .append("): ");
        if (plans.isEmpty()) {
            return result.append("no plans found.").toString();
        }
        result.append("the best ")
                .append(plans.size())
                .append(" of ")
                .append(count(response.path("total"), "plan"))
                .append('.');
        appendIfPresent(result, "Sheet", text(response, "sheetPage", ""));
        for (JsonNode plan : plans) {
            result.append(System.lineSeparator()).append(formatPlan(plan, false));
        }
        return result.toString();
    }

    private static String formatSearch(JsonNode response, int requestedOffset) {
        JsonNode plans = list(response, "plans");
        JsonNode total = response.path("total");
        int offset = response.path("offset").isInt() ? response.path("offset").asInt() : requestedOffset;
        if (plans.isEmpty()) {
            if (total.isNumber() && total.asLong() > 0) {
                return "Found " + count(total, "plan") + ", but the offset " + offset
                        + " is past the last one. Use an offset below " + total.asLong() + ".";
            }
            return "No plans found.";
        }
        StringBuilder result = new StringBuilder("Found ")
                .append(count(total, "plan"))
                .append("; showing ")
                .append(offset + 1)
                .append(" to ")
                .append(offset + plans.size())
                .append('.');
        appendIfPresent(result, "Sheet", text(response, "sheetPage", ""));
        for (JsonNode plan : plans) {
            result.append(System.lineSeparator()).append(formatPlan(plan, true));
        }
        return result.toString();
    }

    private static String formatProvider(JsonNode response) {
        JsonNode apis = list(response, "apis");
        StringBuilder result = new StringBuilder(required(response, "provider"));
        String website = text(response, "website", "");
        if (!website.isEmpty()) {
            result.append(" (").append(website).append(')');
        }
        if (apis.isEmpty()) {
            return result.append(" sells no API on Datacircle.").toString();
        }
        result.append(" sells ").append(plural(apis.size(), "API")).append(". Its rank among each API's providers:");
        int shown = 0;
        for (JsonNode api : apis) {
            if (shown == MAX_PROVIDER_APIS) {
                result.append(System.lineSeparator())
                        .append("And ")
                        .append(plural(apis.size() - shown, "more API"))
                        .append('.');
                break;
            }
            shown++;
            JsonNode rank = api.path("rank");
            JsonNode plans = list(api, "plans");
            result.append(System.lineSeparator())
                    .append("- ")
                    .append(truncate(required(api, "api")))
                    .append(" (")
                    .append(required(api, "slug"))
                    .append("): ")
                    .append(rank.isNumber() ? "rank " + rank.asInt() : "not ranked")
                    .append(", ")
                    .append(plural(plans.size(), "plan"))
                    .append('.');
            JsonNode best = bestScored(plans);
            if (best != null) {
                result.append(" Best: ")
                        .append(truncate(required(best, "plan")))
                        .append(", ")
                        .append(score(best.path("score")))
                        .append(", ")
                        .append(price(best.path("perThousand"), best.path("per")))
                        .append('.');
            }
        }
        return result.toString();
    }

    private static JsonNode bestScored(JsonNode plans) {
        JsonNode best = null;
        for (JsonNode plan : plans) {
            if (plan.path("score").isNumber()
                    && (best == null
                            || plan.path("score").asDouble()
                                    > best.path("score").asDouble())) {
                best = plan;
            }
        }
        return best;
    }

    private static String formatPlan(JsonNode plan, boolean withApi) {
        StringBuilder line = new StringBuilder();
        JsonNode rank = plan.path("rank");
        line.append(!withApi && rank.isNumber() ? rank.asInt() + ". " : "- ")
                .append(truncate(required(plan, "provider")))
                .append(", ")
                .append(truncate(required(plan, "plan")));
        if (withApi) {
            line.append(" (").append(truncate(required(plan, "api"))).append(')');
        }
        line.append(": ");
        if (withApi && rank.isNumber()) {
            line.append("rank ").append(rank.asInt()).append(", ");
        }
        line.append(score(plan.path("score"))).append(", ").append(price(plan.path("perThousand"), plan.path("per")));
        JsonNode notFound = plan.path("notFoundPerThousand");
        if (notFound.isNumber()) {
            line.append(", ").append(dollars(notFound)).append(" per 1,000 not found");
        }
        String billing = text(plan, "billing", "");
        if (!billing.isEmpty()) {
            line.append(", ").append(billing);
        }
        JsonNode latency = plan.path("latencyMs");
        if (latency.isNumber()) {
            line.append(", median ").append(latency.asLong()).append(" ms");
        }
        line.append('.');
        String page = text(plan, "page", "");
        if (!page.isEmpty()) {
            line.append(' ').append(page);
        }
        return line.toString();
    }

    private static String score(JsonNode score) {
        return score.isNumber() ? "score " + decimal(score, 1) + "/100" : "not scored";
    }

    private static String price(JsonNode perThousand, JsonNode per) {
        if (!perThousand.isNumber()) {
            return "no listed price";
        }
        String unit = per == null || !per.isTextual() ? "" : " " + per.asText() + "s";
        return dollars(perThousand) + " per 1,000" + unit;
    }

    private static String dollars(JsonNode amount) {
        return "$" + amount.decimalValue().stripTrailingZeros().toPlainString();
    }

    private static String decimal(JsonNode number, int scale) {
        BigDecimal value =
                number.decimalValue().setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        return value.toPlainString();
    }

    private static String count(JsonNode number, String word) {
        return number.isNumber() ? plural(number.asLong(), word) : "? " + word + "s";
    }

    private static String plural(long count, String word) {
        return String.format(Locale.ROOT, "%,d", count) + " " + word + (count == 1 ? "" : "s");
    }

    private static List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(fold(query).trim().split("[\\s-]+"));
    }

    private static boolean matchesAll(String text, List<String> words) {
        String folded = fold(text);
        return words.stream().allMatch(folded::contains);
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static void appendIfPresent(StringBuilder result, String label, String value) {
        if (!value.isEmpty()) {
            result.append(' ').append(label).append(": ").append(value);
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw unexpected(field + " is missing");
        }
        return value.asText();
    }

    private static JsonNode list(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw unexpected(field + " is not a list");
        }
        return value;
    }

    private static DatacircleClientException unexpected(String what) {
        return new DatacircleClientException("The Datacircle API returned an answer of an unexpected shape: " + what
                + ". Retry later, or read https://api.datacircle.dev/v1/openapi.json.");
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH - 3) + "...";
    }

    private static boolean isStatus(RuntimeException error, int statusCode) {
        return error instanceof DatacircleClientException e && e.statusCode() == statusCode;
    }

    private static String formatError(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Error: the Datacircle API request failed." : "Error: " + message;
    }

    /**
     * Builder for {@link DatacircleTool}.
     */
    public static final class Builder {

        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the API base URL. Defaults to {@code https://api.datacircle.dev/v1}.
         *
         * @param baseUrl API base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets connection and read timeouts.
         *
         * @param timeout connection and read timeout
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Builds the tool.
         *
         * @return configured Datacircle tool
         */
        public DatacircleTool build() {
            DatacircleClient.Builder clientBuilder = DatacircleClient.builder().timeout(timeout);
            if (baseUrl != null) {
                clientBuilder.baseUrl(baseUrl);
            }
            return new DatacircleTool(clientBuilder.build());
        }
    }
}
