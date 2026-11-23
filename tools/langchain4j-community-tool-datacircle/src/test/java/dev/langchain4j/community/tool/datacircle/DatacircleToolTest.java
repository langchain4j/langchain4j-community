package dev.langchain4j.community.tool.datacircle;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DatacircleToolTest {

    private static final String NL = System.lineSeparator();

    private static final String APIS_RESPONSE = """
            [
              {"api": "Employee profile", "slug": "linkedin-profile", "plans": 4933, "providers": 450,
               "sheetPage": "https://datacircle.dev/linkedin-profile-api-pricing",
               "bestPage": "https://datacircle.dev/best-linkedin-profile-api",
               "best": {"provider": "Bridgly", "plan": "Scale", "score": 78.3, "perThousand": 1}},
              {"api": "Employee email", "slug": "email-finder", "plans": 5413, "providers": 412,
               "sheetPage": "https://datacircle.dev/email-finder-api-pricing",
               "bestPage": "https://datacircle.dev/best-email-finder-api",
               "best": {"provider": "Emelia", "plan": "100,000 credits per month", "score": 76.1, "perThousand": 4.99}},
              {"api": "Email validation", "slug": "email-verification", "plans": 1200, "providers": 150,
               "best": null}
            ]
            """;

    private static final String BEST_RESPONSE = """
            {
              "api": "Employee email",
              "slug": "email-finder",
              "sheetPage": "https://datacircle.dev/email-finder-api-pricing",
              "bestPage": "https://datacircle.dev/best-email-finder-api",
              "total": 5413,
              "plans": [
                {"provider": "Emelia", "api": "Employee email", "plan": "100,000 credits per month",
                 "billing": "monthly", "score": 76.1, "rank": 1, "perThousand": 4.99, "per": "call",
                 "notFoundPerThousand": 0, "latencyMs": null,
                 "page": "https://datacircle.dev/find-email-apis/emelia/19-monthly"},
                {"provider": "Emelia", "api": "Employee email", "plan": "50,000 credits per month",
                 "billing": "monthly", "score": 75.5, "rank": 2, "perThousand": 5.98, "per": "call",
                 "notFoundPerThousand": 0, "latencyMs": 812,
                 "page": "https://datacircle.dev/find-email-apis/emelia/19-monthly"}
              ]
            }
            """;

    private static final String SEARCH_RESPONSE = """
            {
              "total": 1926,
              "offset": 10,
              "sheetPage": null,
              "bestPage": null,
              "plans": [
                {"provider": "Hunter", "api": "People search", "plan": "Scale", "billing": "monthly",
                 "score": 65.8, "rank": 57, "perThousand": 1.196, "per": "page", "notFoundPerThousand": 0,
                 "latencyMs": 344, "page": "https://datacircle.dev/people-search-apis/hunter/free-get-domain-search"},
                {"provider": "Hunter", "api": "People search", "plan": "Enterprise", "billing": "contact",
                 "score": null, "rank": null, "perThousand": null, "per": "page", "notFoundPerThousand": null,
                 "latencyMs": null, "page": null}
              ]
            }
            """;

    private static final String PROVIDER_RESPONSE = """
            {
              "provider": "Bright Data",
              "slug": "bright-data",
              "website": "https://brightdata.com",
              "apis": [
                {"api": "Employee profile", "slug": "linkedin-profile", "rank": 76, "plans": [
                  {"plan": "Free Tier", "score": null, "perThousand": 0, "per": "result"},
                  {"plan": "Scale", "score": 38.9, "perThousand": 1.2995, "per": "result"},
                  {"plan": "Pay as you go", "score": 41.4, "perThousand": 1.5, "per": "result"}
                ]},
                {"api": "Employee search", "slug": "linkedin-people-search", "rank": null, "plans": [
                  {"plan": "Enterprise", "score": null, "perThousand": null, "per": "result"}
                ]}
              ]
            }
            """;

    @Test
    void namesEachParameterForTheModel() {
        Map<String, ToolSpecification> specifications =
                ToolSpecifications.toolSpecificationsFrom(DatacircleTool.class).stream()
                        .collect(toMap(ToolSpecification::name, specification -> specification));

        assertThat(specifications).containsOnlyKeys("listApis", "getBestPlans", "searchPlans", "getProvider");
        assertThat(specifications.get("listApis").parameters().properties()).containsOnlyKeys("query");
        assertThat(specifications.get("listApis").parameters().required()).isEmpty();
        assertThat(specifications.get("getBestPlans").parameters().properties()).containsOnlyKeys("api", "limit");
        assertThat(specifications.get("getBestPlans").parameters().required()).containsExactly("api");
        assertThat(specifications.get("searchPlans").parameters().properties())
                .containsOnlyKeys("api", "query", "sort", "limit", "offset");
        assertThat(specifications.get("searchPlans").parameters().required()).isEmpty();
        assertThat(specifications.get("getProvider").parameters().properties()).containsOnlyKeys("provider");
        assertThat(specifications.get("getProvider").parameters().required()).containsExactly("provider");
    }

    @Test
    void listApis_givesEachApiItsSlugCountsAndBestPlan() throws Exception {
        try (TestServer server = startServer(200, APIS_RESPONSE)) {
            String result = tool(server).listApis(null);

            assertThat(result)
                    .isEqualTo("- Employee profile (linkedin-profile): 4,933 plans from 450 providers. "
                            + "Best: Bridgly, Scale, score 78.3/100, $1 per 1,000." + NL
                            + "- Employee email (email-finder): 5,413 plans from 412 providers. "
                            + "Best: Emelia, 100,000 credits per month, score 76.1/100, $4.99 per 1,000." + NL
                            + "- Email validation (email-verification): 1,200 plans from 150 providers." + NL
                            + "3 of 3 APIs. Pass a slug to getBestPlans or searchPlans.");
        }
    }

    @Test
    void listApis_keepsTheApisHoldingEveryWord() throws Exception {
        try (TestServer server = startServer(200, APIS_RESPONSE)) {
            DatacircleTool tool = tool(server);

            assertThat(tool.listApis("EMAIL"))
                    .contains("(email-finder)", "(email-verification)")
                    .doesNotContain("linkedin-profile")
                    .endsWith("2 of 3 APIs. Pass a slug to getBestPlans or searchPlans.");
            assertThat(tool.listApis("email finder"))
                    .contains("(email-finder)")
                    .endsWith("1 of 3 APIs. Pass a slug to getBestPlans or searchPlans.");
            assertThat(tool.listApis("tennis"))
                    .isEqualTo("No API's name or slug holds every word of \"tennis\". "
                            + "Leave the query empty to list 3 APIs.");
        }
    }

    @Test
    void getBestPlans_listsThePlansBestFirstWithTheirPages() throws Exception {
        try (TestServer server = startServer(200, BEST_RESPONSE)) {
            String result = tool(server).getBestPlans("email-finder", 2);

            assertThat(result)
                    .isEqualTo("Employee email (email-finder): the best 2 of 5,413 plans. "
                            + "Sheet: https://datacircle.dev/email-finder-api-pricing" + NL
                            + "1. Emelia, 100,000 credits per month: score 76.1/100, $4.99 per 1,000 calls, "
                            + "$0 per 1,000 not found, monthly. "
                            + "https://datacircle.dev/find-email-apis/emelia/19-monthly" + NL
                            + "2. Emelia, 50,000 credits per month: score 75.5/100, $5.98 per 1,000 calls, "
                            + "$0 per 1,000 not found, monthly, median 812 ms. "
                            + "https://datacircle.dev/find-email-apis/emelia/19-monthly");
        }
    }

    @Test
    void getBestPlans_pointsTheModelToListApisOnAnUnknownSlug() throws Exception {
        try (TestServer server = startServer(404, "{\"error\":\"no API has the slug nope\",\"slugs\":[]}")) {
            String result = tool(server).getBestPlans("nope", null);

            assertThat(result)
                    .isEqualTo("Error: Datacircle API request failed: HTTP 404. no API has the slug nope. "
                            + "Call listApis for every API's slug.");
        }
    }

    @Test
    void searchPlans_saysWhichPlansItShowsAndEachPlansRank() throws Exception {
        try (TestServer server = startServer(200, SEARCH_RESPONSE)) {
            String result = tool(server).searchPlans(null, "hunter", null, 2, 10);

            assertThat(result)
                    .isEqualTo("Found 1,926 plans; showing 11 to 12." + NL
                            + "- Hunter, Scale (People search): rank 57, score 65.8/100, $1.196 per 1,000 pages, "
                            + "$0 per 1,000 not found, monthly, median 344 ms. "
                            + "https://datacircle.dev/people-search-apis/hunter/free-get-domain-search" + NL
                            + "- Hunter, Enterprise (People search): not scored, no listed price, contact.");
        }
    }

    @Test
    void searchPlans_saysWhenNothingIsFound() throws Exception {
        try (TestServer server = startServer(200, "{\"total\":0,\"offset\":0,\"plans\":[]}")) {
            assertThat(tool(server).searchPlans("email-finder", "nobody", "price", null, null))
                    .isEqualTo("No plans found.");
        }
    }

    @Test
    void getProvider_givesItsRankAndBestScoredPlanOnEachApi() throws Exception {
        try (TestServer server = startServer(200, PROVIDER_RESPONSE)) {
            String result = tool(server).getProvider("Bright Data");

            assertThat(result)
                    .isEqualTo("Bright Data (https://brightdata.com) sells 2 APIs. Its rank among each API's providers:"
                            + NL
                            + "- Employee profile (linkedin-profile): rank 76, 3 plans. "
                            + "Best: Pay as you go, score 41.4/100, $1.5 per 1,000 results." + NL
                            + "- Employee search (linkedin-people-search): not ranked, 1 plan.");
        }
    }

    @Test
    void returnsArgumentErrorsToTheModelWithoutCallingTheApi() throws Exception {
        try (TestServer server = startServer(500, "{}")) {
            DatacircleTool tool = tool(server);

            assertThat(tool.searchPlans(null, null, "speed", null, null))
                    .isEqualTo("Error: sort must be one of [price, provider, score]");
            assertThat(tool.getBestPlans("email-finder", 100)).isEqualTo("Error: limit must be between 1 and 50");
            assertThat(tool.getProvider("  ")).startsWith("Error: provider cannot be null or blank");
            assertThat(server.requests()).hasValue(0);
        }
    }

    @Test
    void keepsPricesBelowATenthOfACentExact() throws Exception {
        String body = """
                [{"api": "News search", "slug": "news", "plans": 120, "providers": 30,
                  "best": {"provider": "Feedly", "plan": "Pro", "score": 61.24, "perThousand": 0.00023}},
                 {"api": "Email validation", "slug": "email-verification", "plans": 1200, "providers": 150,
                  "best": {"provider": "Bouncer", "plan": "Pay as you go", "score": 70, "perThousand": 0.00043}}]
                """;
        try (TestServer server = startServer(200, body)) {
            assertThat(tool(server).listApis(null))
                    .contains("Best: Feedly, Pro, score 61.2/100, $0.00023 per 1,000.")
                    .contains("Best: Bouncer, Pay as you go, score 70/100, $0.00043 per 1,000.");
        }
    }

    @Test
    void searchPlans_saysWhenTheOffsetIsPastTheLastPlan() throws Exception {
        try (TestServer server = startServer(200, "{\"total\":147092,\"offset\":999999999,\"plans\":[]}")) {
            assertThat(tool(server).searchPlans(null, null, null, 2, 999999999))
                    .isEqualTo("Found 147,092 plans, but the offset 999999999 is past the last one. "
                            + "Use an offset below 147092.");
        }
        try (TestServer server = startServer(200, "{\"total\":12,\"plans\":[]}")) {
            assertThat(tool(server).searchPlans(null, null, null, null, 40))
                    .isEqualTo("Found 12 plans, but the offset 40 is past the last one. Use an offset below 12.");
        }
    }

    @Test
    void searchPlans_pointsTheModelToListApisOnAnUnknownApi() throws Exception {
        try (TestServer server = startServer(400, "{\"error\":\"api must be one of: Employee profile\"}")) {
            assertThat(tool(server).searchPlans("nope", null, null, null, null))
                    .isEqualTo("Error: Datacircle API request failed: HTTP 400. api must be one of: Employee profile. "
                            + "Call listApis for every API's name and slug.");
        }
    }

    @Test
    void getProvider_saysHowToFindAProviderNamedInOtherLetters() throws Exception {
        try (TestServer server = startServer(200, PROVIDER_RESPONSE)) {
            assertThat(tool(server).getProvider("互亿无线"))
                    .startsWith("Error: Datacircle writes each provider's name in Latin letters")
                    .endsWith("or find the provider with searchPlans.");
            assertThat(server.requests()).hasValue(0);
        }
    }

    @Test
    void getProvider_countsTheApisPastTheHundredItShows() throws Exception {
        String apis = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "{\"api\": \"API " + i + "\", \"slug\": \"api-" + i + "\", \"rank\": 1, \"plans\": []}")
                .collect(Collectors.joining(","));
        try (TestServer server = startServer(200, "{\"provider\": \"Big\", \"apis\": [" + apis + "]}")) {
            String result = tool(server).getProvider("big");

            assertThat(result)
                    .startsWith("Big sells 101 APIs.")
                    .contains("- API 100 (api-100): rank 1, 0 plans.")
                    .doesNotContain("api-101")
                    .endsWith(NL + "And 1 more API.");
        }
    }

    @Test
    void saysSoWhenTheApiLeavesOutACount() throws Exception {
        String body = "[{\"api\": \"Employee email\", \"slug\": \"email-finder\"}]";
        try (TestServer server = startServer(200, body)) {
            assertThat(tool(server).listApis(null))
                    .startsWith("- Employee email (email-finder): ? plans from ? providers.");
        }
        String best = "{\"api\": \"Employee email\", \"slug\": \"email-finder\", \"plans\": ["
                + "{\"provider\": \"Emelia\", \"plan\": \"Pro\", \"rank\": 1, \"score\": 76.1, \"perThousand\": 4.99}]}";
        try (TestServer server = startServer(200, best)) {
            assertThat(tool(server).getBestPlans("email-finder", 1))
                    .startsWith("Employee email (email-finder): the best 1 of ? plans.");
        }
    }

    @Test
    void returnsAnErrorWhenAnAnswerHasAnUnexpectedShape() throws Exception {
        String shape = "Error: The Datacircle API returned an answer of an unexpected shape: ";
        String end = ". Retry later, or read https://api.datacircle.dev/v1/openapi.json.";
        try (TestServer server = startServer(200, "{\"apis\": []}")) {
            assertThat(tool(server).listApis(null)).isEqualTo(shape + "the list of APIs is not a list" + end);
        }
        try (TestServer server = startServer(200, "[{\"name\": \"Employee email\", \"slug\": \"email-finder\"}]")) {
            assertThat(tool(server).listApis(null)).isEqualTo(shape + "api is missing" + end);
        }
        try (TestServer server =
                startServer(200, "{\"api\": \"Employee email\", \"slug\": \"email-finder\", \"results\": []}")) {
            assertThat(tool(server).getBestPlans("email-finder", null)).isEqualTo(shape + "plans is not a list" + end);
        }
        try (TestServer server =
                startServer(200, "{\"total\": 1, \"plans\": [{\"name\": \"Hunter\", \"score\": 76.1}]}")) {
            assertThat(tool(server).searchPlans(null, null, null, null, null))
                    .isEqualTo(shape + "provider is missing" + end);
        }
        try (TestServer server = startServer(200, "{\"provider\": \"Hunter\", \"plans\": []}")) {
            assertThat(tool(server).getProvider("hunter")).isEqualTo(shape + "apis is not a list" + end);
        }
    }

    @Test
    void saysWhenTheApiDoesNotAnswerInTime() throws Exception {
        try (TestServer server = startServer(exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        })) {
            DatacircleTool tool = DatacircleTool.builder()
                    .baseUrl(server.baseUrl())
                    .timeout(Duration.ofSeconds(1))
                    .build();

            assertThat(tool.listApis(null))
                    .isEqualTo("Error: The Datacircle API did not answer within 1 s. Retry in a moment.");
        }
    }

    @Test
    void saysWhenTheConnectionIsRefusedOrLost() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        DatacircleTool refused = DatacircleTool.builder()
                .baseUrl("http://localhost:" + closedPort)
                .timeout(Duration.ofSeconds(5))
                .build();
        assertThat(refused.listApis(null))
                .isEqualTo("Error: The Datacircle API refused the connection. Check the network and the base URL.");

        try (TestServer server = startServer(exchange -> exchange.close())) {
            assertThat(tool(server).listApis(null))
                    .isEqualTo(
                            "Error: The connection to the Datacircle API was lost before it answered. Retry in a moment.");
        }
    }

    @Test
    void returnsTheRateLimitToTheModel() throws Exception {
        try (TestServer server = startServer(429, "{\"error\":\"too many calls\"}")) {
            assertThat(tool(server).listApis(null))
                    .isEqualTo("Error: Datacircle API request failed: HTTP 429. too many calls. "
                            + "The API allows 60 calls a minute from one address. Retry in a minute.");
        }
    }

    private static DatacircleTool tool(TestServer server) {
        return DatacircleTool.builder()
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(int statusCode, String responseBody) throws IOException {
        return startServer(exchange -> {
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
    }

    private static TestServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger requests = new AtomicInteger();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            handler.handle(exchange);
        });
        server.start();
        return new TestServer(server, executor, requests);
    }

    private record TestServer(HttpServer server, ExecutorService executor, AtomicInteger requests)
            implements AutoCloseable {

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
