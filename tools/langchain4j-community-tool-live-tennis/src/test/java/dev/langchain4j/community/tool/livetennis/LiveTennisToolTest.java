package dev.langchain4j.community.tool.livetennis;

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

class LiveTennisToolTest {

    private static final String LIVE_MATCH_RESPONSE = """
            {
              "data": [{
                "id": 187701,
                "tournament": "Wimbledon",
                "tour": "atp",
                "surface": "grass",
                "indoor": false,
                "round": "Round of 16",
                "round_code": "R16",
                "status": "live",
                "draw": "singles",
                "players": {
                  "p1": {"id": 11, "name": "Carlos Alcaraz", "ranking": 2},
                  "p2": {"id": 22, "name": "Jannik Sinner", "ranking": 1}
                },
                "score": {
                  "sets": [1, 0],
                  "games": [[6, 3], [4, 4]],
                  "points": ["30", "15"],
                  "server": 1,
                  "is_tiebreak": false
                }
              }],
              "meta": {"limit": 10, "offset": 0, "count": 1, "total": 3, "has_more": true}
            }
            """;

    @Test
    void getLiveMatches_formatsThePlayerMajorScore() throws Exception {
        try (TestServer server = startServer(200, LIVE_MATCH_RESPONSE)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getLiveMatches("atp", "singles", 10);

            assertThat(result)
                    .startsWith("- [187701] Wimbledon (atp, grass, singles) Round of 16: "
                            + "Carlos Alcaraz (#2) vs Jannik Sinner (#1) — ")
                    .contains("sets 1-0; games 6-4 3-4; points 30-15; player 1 serving")
                    .endsWith("Showing 1 of 3; more available, raise the limit to see them.");
        }
    }

    @Test
    void getLiveMatches_saysSoWhenNothingIsInPlay() throws Exception {
        try (TestServer server = startServer(200, "{\"data\":[],\"meta\":{\"count\":0,\"has_more\":false}}")) {
            LiveTennisTool tool = tool(server);

            assertThat(tool.getLiveMatches(null, null, null)).isEqualTo("No matches are in play right now.");
        }
    }

    @Test
    void getLiveMatches_readsATiebreakAsATiebreak() throws Exception {
        String response = """
                {"data":[{
                  "id": 5,
                  "tournament": "US Open",
                  "tour": "wta",
                  "players": {"p1": {"name": "A"}, "p2": {"name": "B"}},
                  "score": {"sets":[1,1],"games":[[6,6],[4,6]],"points":["5","3"],"server":2,"is_tiebreak":true}
                }],"meta":{"count":1,"has_more":false}}
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            assertThat(tool.getLiveMatches(null, null, null)).contains("tiebreak 5-3; player 2 serving");
        }
    }

    @Test
    void getLiveMatches_toleratesTheNullScoreShapesTheApiDocuments() throws Exception {
        String response = """
                {"data":[
                  {"id":1,"tournament":"T","tour":"itf","players":{"p1":{"name":"A"},"p2":{"name":"B"}},
                   "score":{"sets":[2,0],"games":[[],[]],"points":[null,null],"server":null,"is_tiebreak":false}},
                  {"id":2,"tournament":"T","tour":"itf","players":{"p1":{"name":"C"},"p2":{"name":"D"}},
                   "score":null}
                ],"meta":{"count":2,"has_more":false}}
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getLiveMatches(null, null, null);

            assertThat(result).contains("A vs B — sets 2-0").contains("C vs D — no score yet");
        }
    }

    @Test
    void getMatch_reportsTheSettlementFields() throws Exception {
        String response = """
                {
                  "id": 187701,
                  "tournament": "Roland Garros",
                  "tour": "atp",
                  "surface": "clay",
                  "indoor": false,
                  "round": "QF",
                  "status": "completed",
                  "event_status": "Retired",
                  "outcome": "retired",
                  "draw": "singles",
                  "scheduled_time": "2026-06-02T11:00:00Z",
                  "players": {"p1": {"name": "Alice Smith"}, "p2": {"name": "Bob Jones"}},
                  "score": {"sets":[2,0],"games":[[6,6],[3,4]],"points":[null,null],"server":null},
                  "winner": 1
                }
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getMatch(187701);

            assertThat(result)
                    .contains("Match 187701: Alice Smith vs Bob Jones")
                    .contains("Roland Garros (atp, clay, singles) QF")
                    .contains("Status: completed")
                    .contains("Ended as: Retired")
                    .contains("Outcome: retired")
                    .contains("Scheduled: 2026-06-02T11:00:00Z")
                    .contains("Score: sets 2-0; games 6-3 6-4")
                    .contains("Winner: Alice Smith");
        }
    }

    @Test
    void getUpcomingFixtures_usesStartTimeAndFallsBackToTheDate() throws Exception {
        String response = """
                {"data":[
                  {"id":190001,"start_time":"2026-09-15T11:00:00Z","event_date":"2026-09-15",
                   "tournament":"US Open","round":"QF","surface":"hard",
                   "player1_name":"Coco Gauff","player2_name":"Elena Rybakina"},
                  {"id":190002,"start_time":null,"event_date":"2026-09-16",
                   "tournament":"US Open","round":"SF","surface":"hard",
                   "player1_name":"Iga Swiatek","player2_name":null}
                ],"meta":{"count":2,"total":2,"has_more":false}}
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getUpcomingFixtures("wta", "singles", 2);

            assertThat(result)
                    .contains("- [190001] 2026-09-15T11:00:00Z US Open QF [hard]: Coco Gauff vs Elena Rybakina")
                    .contains("- [190002] 2026-09-16 US Open SF [hard]: Iga Swiatek vs unknown")
                    .endsWith("Showing 2 of 2.");
        }
    }

    @Test
    void getUpcomingFixtures_saysSoWhenThereAreNone() throws Exception {
        try (TestServer server = startServer(200, "{\"data\":[],\"meta\":{\"count\":0}}")) {
            assertThat(tool(server).getUpcomingFixtures(null, null, null)).isEqualTo("No upcoming fixtures found.");
        }
    }

    @Test
    void getRankings_printsTheTableWithItsPublicationWeek() throws Exception {
        String response = """
                {"data":[
                  {"player_id":11,"player_name":"Jannik Sinner","system":"atp","rank":1,"points":11500},
                  {"player_id":null,"player_name":"Carlos Alcaraz","system":"atp","rank":2,"points":9900}
                ],"meta":{"count":2,"total":100,"has_more":true,
                  "coverage":{"as_of":"2026-09-08","effective_date":"2026-09-08"}}}
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getRankings("ATP", "2026-09-08", 2);

            assertThat(result)
                    .startsWith("atp ranking table, published 2026-09-08:")
                    .contains("- 1. Jannik Sinner — 11500 points")
                    .contains("- 2. Carlos Alcaraz — 9900 points")
                    .endsWith("Showing 2 of 100; more available, raise the limit to see them.");
        }
    }

    @Test
    void getHeadToHead_summarisesTheRecordAndRecentMeetings() throws Exception {
        String response = """
                {
                  "players": {"p1": {"name": "Novak Djokovic"}, "p2": {"name": "Rafael Nadal"}},
                  "totals": {"p1_wins": 31, "p2_wins": 29, "meetings": 60, "undecided": 1},
                  "by_surface": {"hard": {"p1": 20, "p2": 7}, "clay": {"p1": 9, "p2": 20}},
                  "meetings": [{
                    "era": "archive", "date": "2022-05-31", "tournament": "Roland Garros",
                    "round": "QF", "surface": "clay", "score": "6-2 4-6 6-2 7-6(4)",
                    "outcome": "completed", "winner": 2
                  }, {
                    "era": "current", "date": "2023-06-09", "tournament": "Roland Garros",
                    "round": "SF", "surface": "clay", "score": null,
                    "outcome": "walkover", "winner": null
                  }]
                }
                """;
        try (TestServer server = startServer(200, response)) {
            LiveTennisTool tool = tool(server);

            String result = tool.getHeadToHead("Djokovic", "Nadal");

            assertThat(result)
                    .startsWith("Head-to-head: Novak Djokovic vs Rafael Nadal")
                    .contains("Record: 31-29 over 60 decided meetings (1 with no derivable winner)")
                    .contains("By surface: hard 20-7 clay 9-20")
                    .contains("- 2022-05-31 Roland Garros QF [clay]: Rafael Nadal won 6-2 4-6 6-2 7-6(4)")
                    .contains("- 2023-06-09 Roland Garros SF [clay]: no winner recorded (walkover)");
        }
    }

    @Test
    void getHeadToHead_reportsAnUnmatchedPairHonestly() throws Exception {
        try (TestServer server = startServer(200, "{\"players\":null,\"totals\":{}}")) {
            assertThat(tool(server).getHeadToHead("Nobody", "Nemo"))
                    .isEqualTo("No head-to-head record found for Nobody and Nemo.");
        }
    }

    @Test
    void everyToolTurnsAPlanRefusalIntoAnActionableMessage() throws Exception {
        try (TestServer server = startServer(403, "{\"error\":\"upgrade_required\"}")) {
            LiveTennisTool tool = tool(server);
            String expected = "Error: Live Tennis API request failed: HTTP 403. "
                    + "The API key's plan does not include this endpoint.";

            assertThat(tool.getLiveMatches(null, null, null)).isEqualTo(expected);
            assertThat(tool.getMatch(187701)).isEqualTo(expected);
            assertThat(tool.getUpcomingFixtures(null, null, null)).isEqualTo(expected);
            assertThat(tool.getRankings("atp", null, null)).isEqualTo(expected);
            assertThat(tool.getHeadToHead("Djokovic", "Nadal")).isEqualTo(expected);
        }
    }

    @Test
    void reportsTheDailyQuotaCeilingRatherThanThrowing() throws Exception {
        try (TestServer server = startServer(429, "{\"error\":\"rate_limited\",\"scope\":\"day\"}")) {
            assertThat(tool(server).getLiveMatches(null, null, null))
                    .isEqualTo("Error: Live Tennis API request failed: HTTP 429. "
                            + "Rate limit or daily quota exceeded. Retry later.");
        }
    }

    @Test
    void returnsValidationErrorsAsToolOutput() throws Exception {
        try (TestServer server = startServer(200, "{\"data\":[]}")) {
            LiveTennisTool tool = tool(server);

            assertThat(tool.getLiveMatches("premier", null, null))
                    .isEqualTo("Error: tour must be one of [atp, challenger, itf, juniors, wta]");
            assertThat(tool.getUpcomingFixtures(null, null, 500)).isEqualTo("Error: limit must be between 1 and 200");
            assertThat(tool.getRankings("elo", null, null))
                    .isEqualTo("Error: system must be one of "
                            + "[atp, atp_doubles, itf_jt, itf_mt, itf_wt, wta, wta_doubles]");
            assertThat(tool.getHeadToHead("Li", "Nadal"))
                    .isEqualTo("Error: player1 must be at least 3 characters, so it can identify one player");
        }
    }

    @Test
    void boundsTheTextItHandsBackToTheModel() throws Exception {
        String response =
                "{\"data\":[{\"id\":1,\"tournament\":\"%s\",\"tour\":\"itf\",\"players\":{\"p1\":{\"name\":\"A\"},\"p2\":{\"name\":\"B\"}}}],\"meta\":{\"count\":1}}"
                        .formatted("T".repeat(400));
        try (TestServer server = startServer(200, response)) {
            String result = tool(server).getLiveMatches(null, null, null);

            assertThat(result).contains("T".repeat(117) + "...").hasSizeLessThan(300);
        }
    }

    @Test
    void buildsWithTheDefaultBaseUrlWithoutCallingTheApi() {
        LiveTennisTool tool = LiveTennisTool.builder().apiKey("test-key").build();

        assertThat(tool).isNotNull();
    }

    private static LiveTennisTool tool(TestServer server) {
        return LiveTennisTool.builder()
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
