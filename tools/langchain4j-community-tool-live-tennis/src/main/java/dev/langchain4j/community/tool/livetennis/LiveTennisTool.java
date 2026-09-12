package dev.langchain4j.community.tool.livetennis;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Agent tools for read-only tennis data from the Live Tennis API: live scores, match detail,
 * upcoming fixtures, published ranking tables and head-to-head records.
 *
 * <p>Endpoint access is plan-tiered. Live matches, match detail and fixtures are on the free plan
 * (30 requests/minute, 100/day); head-to-head requires a paid history plan and the ranking table
 * requires the next plan above it. A call above the key's plan is reported back to the model as an
 * error rather than an empty result.
 *
 * <pre>{@code
 * LiveTennisTool tool = LiveTennisTool.builder()
 *         .apiKey(System.getenv("LIVE_TENNIS_API_KEY"))
 *         .build();
 * }</pre>
 */
public final class LiveTennisTool {

    private static final String UNKNOWN_CELL = "?";
    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_MEETINGS = 10;
    private static final int MAX_SETS = 5;

    private final LiveTennisClient client;

    LiveTennisTool(LiveTennisClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new Live Tennis tool builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lists the tennis matches currently in play, with their live scores.
     *
     * @param tour tour filter
     * @param draw singles or doubles
     * @param limit number of matches
     * @return agent-friendly live match summaries
     */
    @Tool("List the tennis matches currently in play, with live scores.")
    public String getLiveMatches(
            @P(value = "Tour: atp, wta, challenger, itf or juniors. Defaults to all tours", required = false)
                    String tour,
            @P(value = "Draw: singles or doubles. Defaults to both", required = false) String draw,
            @P(value = "Number of matches from 1 through 200. Defaults to 10", required = false) Integer limit) {
        try {
            return formatMatchList(client.listLiveMatches(tour, draw, limit), "No matches are in play right now.");
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads the full detail of one match, including its current score.
     *
     * @param matchId the match id
     * @return agent-friendly match detail
     */
    @Tool("Get full detail and the current score of one tennis match by its numeric match id.")
    public String getMatch(@P("Numeric match id, as returned by the live match or fixture tools") long matchId) {
        try {
            return formatMatchDetail(client.getMatch(matchId));
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Lists upcoming scheduled fixtures, earliest first.
     *
     * @param tour tour filter
     * @param draw singles or doubles
     * @param limit number of fixtures
     * @return agent-friendly fixture summaries
     */
    @Tool("List upcoming scheduled tennis fixtures, earliest first.")
    public String getUpcomingFixtures(
            @P(value = "Tour: atp, wta, challenger, itf or juniors. Defaults to all tours", required = false)
                    String tour,
            @P(value = "Draw: singles or doubles. Defaults to both", required = false) String draw,
            @P(value = "Number of fixtures from 1 through 200. Defaults to 10", required = false) Integer limit) {
        try {
            return formatFixtures(client.listFixtures(tour, draw, limit));
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads one published ranking table in rank order.
     *
     * @param system ranking system
     * @param asOf publication date
     * @param limit number of ranked players
     * @return agent-friendly ranking table
     */
    @Tool("Get a published tennis ranking table in rank order (ATP, WTA, their doubles tables, or ITF circuits).")
    public String getRankings(
            @P("Ranking system: atp, wta, atp_doubles, wta_doubles, itf_jt, itf_mt or itf_wt") String system,
            @P(value = "Table date as YYYY-MM-DD. Defaults to the latest table", required = false) String asOf,
            @P(value = "Number of ranked players from 1 through 200. Defaults to 10", required = false) Integer limit) {
        try {
            return formatRankings(client.listRankings(system, asOf, limit), system);
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads the head-to-head record between two players.
     *
     * @param player1 first player name
     * @param player2 second player name
     * @return agent-friendly head-to-head record
     */
    @Tool("Get the head-to-head record between two tennis players, by name, back to 1968.")
    public String getHeadToHead(
            @P("First player's name or surname, at least 3 characters") String player1,
            @P("Second player's name or surname, at least 3 characters") String player2) {
        try {
            return formatHeadToHead(client.getHeadToHead(player1, player2), player1, player2);
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    private static String formatMatchList(JsonNode response, String emptyMessage) {
        JsonNode matches = response.path("data");
        if (!matches.isArray() || matches.isEmpty()) {
            return emptyMessage;
        }

        StringBuilder result = new StringBuilder();
        for (JsonNode match : matches) {
            if (!result.isEmpty()) {
                result.append(System.lineSeparator());
            }
            result.append("- [")
                    .append(match.path("id").asLong())
                    .append("] ")
                    .append(matchContext(match))
                    .append(": ")
                    .append(playerName(match, "p1"))
                    .append(" vs ")
                    .append(playerName(match, "p2"))
                    .append(" — ")
                    .append(formatScore(match.path("score")));
        }
        return result.append(System.lineSeparator())
                .append(paginationNote(response.path("meta")))
                .toString();
    }

    private static String formatMatchDetail(JsonNode match) {
        StringBuilder result = new StringBuilder("Match ")
                .append(match.path("id").asLong())
                .append(": ")
                .append(playerName(match, "p1"))
                .append(" vs ")
                .append(playerName(match, "p2"))
                .append(System.lineSeparator())
                .append(matchContext(match))
                .append(System.lineSeparator())
                .append("Status: ")
                .append(text(match, "status", "unknown"));

        appendIfPresent(result, "Ended as", text(match, "event_status", ""));
        appendIfPresent(result, "Outcome", text(match, "outcome", ""));
        appendIfPresent(result, "Scheduled", text(match, "scheduled_time", ""));
        result.append(System.lineSeparator()).append("Score: ").append(formatScore(match.path("score")));

        JsonNode winner = match.path("winner");
        if (winner.isInt()) {
            result.append(System.lineSeparator())
                    .append("Winner: ")
                    .append(playerName(match, winner.asInt() == 2 ? "p2" : "p1"));
        }
        return result.toString();
    }

    private static String formatFixtures(JsonNode response) {
        JsonNode fixtures = response.path("data");
        if (!fixtures.isArray() || fixtures.isEmpty()) {
            return "No upcoming fixtures found.";
        }

        StringBuilder result = new StringBuilder();
        for (JsonNode fixture : fixtures) {
            String start = text(fixture, "start_time", text(fixture, "event_date", "time to be confirmed"));
            if (!result.isEmpty()) {
                result.append(System.lineSeparator());
            }
            result.append("- [")
                    .append(fixture.path("id").asLong())
                    .append("] ")
                    .append(start)
                    .append(" ")
                    .append(truncate(text(fixture, "tournament", "unknown tournament")))
                    .append(roundSuffix(fixture))
                    .append(surfaceSuffix(fixture))
                    .append(": ")
                    .append(truncate(text(fixture, "player1_name", "unknown")))
                    .append(" vs ")
                    .append(truncate(text(fixture, "player2_name", "unknown")));
        }
        return result.append(System.lineSeparator())
                .append(paginationNote(response.path("meta")))
                .toString();
    }

    private static String formatRankings(JsonNode response, String requestedSystem) {
        JsonNode records = response.path("data");
        if (!records.isArray() || records.isEmpty()) {
            return "No ranking records found.";
        }

        String effectiveDate =
                text(response.path("meta").path("coverage"), "effective_date", "an unstated publication week");
        StringBuilder result = new StringBuilder(requestedSystem.trim().toLowerCase(Locale.ROOT))
                .append(" ranking table, published ")
                .append(effectiveDate)
                .append(":");
        for (JsonNode record : records) {
            result.append(System.lineSeparator()).append("- ");
            JsonNode rank = record.path("rank");
            result.append(rank.isInt() ? rank.asInt() + "." : "unranked.")
                    .append(" ")
                    .append(truncate(text(record, "player_name", "unknown player")));
            JsonNode points = record.path("points");
            if (points.isInt()) {
                result.append(" — ").append(points.asInt()).append(" points");
            }
            JsonNode rating = record.path("rating");
            if (rating.isNumber()) {
                result.append(" — rating ").append(rating.asDouble());
            }
        }
        return result.append(System.lineSeparator())
                .append(paginationNote(response.path("meta")))
                .toString();
    }

    private static String formatHeadToHead(JsonNode response, String requested1, String requested2) {
        JsonNode players = response.path("players");
        if (!players.isObject()) {
            return "No head-to-head record found for " + truncate(requested1) + " and " + truncate(requested2) + ".";
        }
        String name1 = truncate(text(players.path("p1"), "name", requested1));
        String name2 = truncate(text(players.path("p2"), "name", requested2));

        JsonNode totals = response.path("totals");
        StringBuilder result = new StringBuilder("Head-to-head: ")
                .append(name1)
                .append(" vs ")
                .append(name2)
                .append(System.lineSeparator())
                .append("Record: ")
                .append(totals.path("p1_wins").asInt(0))
                .append("-")
                .append(totals.path("p2_wins").asInt(0))
                .append(" over ")
                .append(totals.path("meetings").asInt(0))
                .append(" decided meetings");
        int undecided = totals.path("undecided").asInt(0);
        if (undecided > 0) {
            result.append(" (").append(undecided).append(" with no derivable winner)");
        }

        JsonNode bySurface = response.path("by_surface");
        if (bySurface.isObject() && !bySurface.isEmpty()) {
            result.append(System.lineSeparator()).append("By surface:");
            for (Map.Entry<String, JsonNode> surface : bySurface.properties()) {
                result.append(" ")
                        .append(surface.getKey())
                        .append(" ")
                        .append(surface.getValue().path("p1").asInt(0))
                        .append("-")
                        .append(surface.getValue().path("p2").asInt(0));
            }
        }

        JsonNode meetings = response.path("meetings");
        if (meetings.isArray() && !meetings.isEmpty()) {
            result.append(System.lineSeparator()).append("Recent meetings, newest first:");
            int shown = 0;
            for (JsonNode meeting : meetings) {
                if (shown++ == MAX_MEETINGS) {
                    result.append(System.lineSeparator())
                            .append("(")
                            .append(meetings.size() - MAX_MEETINGS)
                            .append(" older meetings not shown)");
                    break;
                }
                JsonNode winner = meeting.path("winner");
                String verdict = winner.isInt() ? (winner.asInt() == 2 ? name2 : name1) + " won" : "no winner recorded";
                result.append(System.lineSeparator())
                        .append("- ")
                        .append(text(meeting, "date", "undated"))
                        .append(" ")
                        .append(truncate(text(meeting, "tournament", "unknown tournament")))
                        .append(roundSuffix(meeting))
                        .append(surfaceSuffix(meeting))
                        .append(": ")
                        .append(verdict)
                        .append(scoreSuffix(meeting));
            }
        }
        return result.toString();
    }

    private static String matchContext(JsonNode match) {
        StringBuilder context =
                new StringBuilder(truncate(text(match, "tournament", "unknown tournament"))).append(" (");
        context.append(text(match, "tour", "tour unstated"));
        appendCommaSeparated(context, text(match, "surface", ""));
        appendCommaSeparated(context, text(match, "draw", ""));
        appendCommaSeparated(context, match.path("indoor").asBoolean(false) ? "indoor" : "");
        return context.append(")").append(roundSuffix(match)).toString();
    }

    private static void appendCommaSeparated(StringBuilder builder, String value) {
        if (!value.isBlank()) {
            builder.append(", ").append(value);
        }
    }

    private static void appendIfPresent(StringBuilder builder, String label, String value) {
        if (!value.isBlank()) {
            builder.append(System.lineSeparator()).append(label).append(": ").append(value);
        }
    }

    private static String roundSuffix(JsonNode node) {
        String round = text(node, "round", text(node, "round_code", ""));
        return round.isBlank() ? "" : " " + truncate(round);
    }

    private static String surfaceSuffix(JsonNode node) {
        String surface = text(node, "surface", "");
        return surface.isBlank() ? "" : " [" + surface + "]";
    }

    private static String scoreSuffix(JsonNode meeting) {
        String score = text(meeting, "score", "");
        String outcome = text(meeting, "outcome", "");
        StringBuilder suffix = new StringBuilder();
        if (!score.isBlank()) {
            suffix.append(" ").append(truncate(score));
        }
        if (!outcome.isBlank() && !"completed".equals(outcome)) {
            suffix.append(" (").append(outcome).append(")");
        }
        return suffix.toString();
    }

    private static String formatScore(JsonNode score) {
        if (!score.isObject()) {
            return "no score yet";
        }

        StringBuilder result = new StringBuilder();
        String sets = sidePair(score.path("sets"));
        if (!sets.isBlank()) {
            result.append("sets ").append(sets);
        }
        String games = formatGames(score.path("games"));
        if (!games.isBlank()) {
            appendSegment(result, "games " + games);
        }
        String points = sidePair(score.path("points"));
        if (!points.isBlank()) {
            appendSegment(result, (score.path("is_tiebreak").asBoolean(false) ? "tiebreak " : "points ") + points);
        }
        JsonNode server = score.path("server");
        if (server.isInt()) {
            appendSegment(result, "player " + server.asInt() + " serving");
        }
        return result.isEmpty() ? "no score yet" : result.toString();
    }

    private static void appendSegment(StringBuilder result, String segment) {
        if (!result.isEmpty()) {
            result.append("; ");
        }
        result.append(segment);
    }

    /**
     * Formats the player-major {@code games} array as per-set pairs: {@code [[6,3],[4,4]]} reads
     * {@code 6-4 3-4}.
     */
    private static String formatGames(JsonNode games) {
        if (!games.isArray() || games.size() < 2) {
            return "";
        }
        JsonNode p1 = games.get(0);
        JsonNode p2 = games.get(1);
        if (!p1.isArray() || !p2.isArray()) {
            return "";
        }
        int setCount = Math.min(MAX_SETS, Math.max(p1.size(), p2.size()));
        StringBuilder result = new StringBuilder();
        for (int set = 0; set < setCount; set++) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(cell(p1.path(set))).append("-").append(cell(p2.path(set)));
        }
        return result.toString();
    }

    /** Formats a two-entry player-major array, such as {@code sets} or {@code points}. */
    private static String sidePair(JsonNode pair) {
        if (!pair.isArray() || pair.size() < 2) {
            return "";
        }
        String p1 = cell(pair.get(0));
        String p2 = cell(pair.get(1));
        return UNKNOWN_CELL.equals(p1) && UNKNOWN_CELL.equals(p2) ? "" : p1 + "-" + p2;
    }

    /** Renders one score cell, or {@code ?} where the API reports a null the score shape allows. */
    private static String cell(JsonNode value) {
        return value.isNumber() || value.isTextual() ? value.asText() : UNKNOWN_CELL;
    }

    private static String playerName(JsonNode match, String side) {
        JsonNode player = match.path("players").path(side);
        String name = truncate(text(player, "name", "unknown"));
        JsonNode ranking = player.path("ranking");
        return ranking.isInt() ? name + " (#" + ranking.asInt() + ")" : name;
    }

    private static String paginationNote(JsonNode meta) {
        int count = meta.path("count").asInt(0);
        StringBuilder note = new StringBuilder("Showing ").append(count);
        JsonNode total = meta.path("total");
        if (total.isInt()) {
            note.append(" of ").append(total.asInt());
        }
        if (meta.path("has_more").asBoolean(false)) {
            note.append("; more available, raise the limit to see them");
        }
        return note.append(".").toString();
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

    private static String formatError(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? "Error: the Live Tennis API request failed."
                : "Error: " + message;
    }

    /**
     * Builder for {@link LiveTennisTool}.
     */
    public static final class Builder {

        private String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the Live Tennis API key, sent as the {@code X-API-Key} header.
         *
         * @param apiKey Live Tennis API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL. Defaults to {@code https://api.livetennisapi.com/api/public/v1}.
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
         * @return configured Live Tennis tool
         */
        public LiveTennisTool build() {
            LiveTennisClient.Builder clientBuilder =
                    LiveTennisClient.builder().apiKey(apiKey).timeout(timeout);
            if (baseUrl != null) {
                clientBuilder.baseUrl(baseUrl);
            }
            return new LiveTennisTool(clientBuilder.build());
        }
    }
}
