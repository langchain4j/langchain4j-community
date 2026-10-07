package dev.langchain4j.community.tool.darkmoon;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DarkmoonToolTest {

    private static final String LOGIN_OK = "{\"token\":\"tok-1\"}";

    private static final String CAMPAIGNS = """
            {"data":[
              {"id":"camp_20260901_a","date":"2026-09-01T10:00:00Z","status":"completed"},
              {"id":"camp_20260922_b","date":"2026-09-22T08:30:00Z","status":"running"}
            ],"total":2}
            """;

    private static final String FINDINGS = """
            {"data":[
              {"title":"Reflected XSS in search","severity":"medium","cvss_score":6.1,"status":"confirmed",
               "category":"xss_reflected","endpoint":"https://app.example.com/search","description":"The q parameter\\nis reflected."},
              {"title":"SQL injection in login","severity":"critical","cvss_score":9.8,"status":"exploited",
               "category":"sql_injection","endpoint":"https://app.example.com/login","cve":"CVE-2026-0001"},
              {"title":"Missing security headers","severity":"low","status":"unconfirmed"},
              {"title":"Server banner","severity":"info","status":"confirmed"},
              {"title":"Odd finding","status":"unconfirmed"}
            ],"total":5,"stats":{"by_severity":{"critical":1,"medium":1,"low":1,"info":1}}}
            """;

    @Test
    void listCampaigns_listsNewestFirst() throws Exception {
        try (TestServer server = startServer(Map.of("/api/v1/campaigns", new Reply(200, CAMPAIGNS)))) {
            String result = tool(server).listDarkmoonCampaigns();

            assertThat(result).startsWith("Campaigns (newest first):");
            assertThat(result.indexOf("camp_20260922_b")).isLessThan(result.indexOf("camp_20260901_a"));
            assertThat(result)
                    .contains("id=camp_20260922_b date=2026-09-22T08:30:00Z status=running")
                    .contains("id=camp_20260901_a date=2026-09-01T10:00:00Z status=completed");
        }
    }

    @Test
    void listCampaigns_reportsNoCampaignsYet() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/campaigns", new Reply(200, "{\"data\":[],\"total\":0}")))) {
            assertThat(tool(server).listDarkmoonCampaigns()).isEqualTo("No campaigns yet.");
        }
    }

    @Test
    void listCampaigns_failsLoudlyWhenDataIsNotAnArray() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/campaigns", new Reply(200, "{\"items\":[],\"total\":0}")))) {
            assertThat(tool(server).listDarkmoonCampaigns())
                    .isEqualTo("Error: Darkmoon returned an unrecognised response shape.");
        }
    }

    @Test
    void listCampaigns_capsTheListing() throws Exception {
        StringBuilder data = new StringBuilder("{\"data\":[");
        for (int i = 0; i < 53; i++) {
            data.append(i == 0 ? "" : ",")
                    .append("{\"id\":\"c")
                    .append(i)
                    .append("\",\"date\":\"2026-01-01T00:00:")
                    .append(String.format("%02d", i))
                    .append("Z\"}");
        }
        data.append("]}");
        try (TestServer server = startServer(Map.of("/api/v1/campaigns", new Reply(200, data.toString())))) {
            String result = tool(server).listDarkmoonCampaigns();

            assertThat(result).contains("... and 3 older campaigns not shown.");
            assertThat(result).contains("id=c52 ").doesNotContain("id=c0 ");
        }
    }

    @Test
    void getFindings_summarisesAndSortsMostSevereFirst() throws Exception {
        try (TestServer server = startServer(Map.of("/api/v1/vulnerabilities", new Reply(200, FINDINGS)))) {
            String result = tool(server).getDarkmoonFindings("camp_20260922_b", null);

            assertThat(result)
                    .startsWith(
                            "Findings in campaign camp_20260922_b: 5 (1 critical, 1 medium, 1 low, 1 info, 1 unrated)")
                    .contains("[CRITICAL] SQL injection in login cvss=9.8 status=exploited category=sql_injection "
                            + "endpoint=https://app.example.com/login cve=CVE-2026-0001")
                    .contains("[MEDIUM] Reflected XSS in search cvss=6.1 status=confirmed")
                    .contains("  The q parameter is reflected.")
                    .contains("[UNKNOWN] Odd finding status=unconfirmed");
            assertThat(result.indexOf("[CRITICAL]")).isLessThan(result.indexOf("[MEDIUM]"));
            assertThat(result.indexOf("[MEDIUM]")).isLessThan(result.indexOf("[LOW]"));
            assertThat(result.indexOf("[INFO]")).isLessThan(result.indexOf("[UNKNOWN]"));
        }
    }

    @Test
    void getFindings_filtersByMinimumSeverity() throws Exception {
        try (TestServer server = startServer(Map.of("/api/v1/vulnerabilities", new Reply(200, FINDINGS)))) {
            String result = tool(server).getDarkmoonFindings("camp_1", " Medium ");

            assertThat(result).startsWith("Findings in campaign camp_1: 2 (1 critical, 1 medium)");
            assertThat(result).doesNotContain("Missing security headers").doesNotContain("Odd finding");
        }
    }

    @Test
    void getFindings_saysWhenNothingMatches() throws Exception {
        try (TestServer server = startServer(Map.of(
                "/api/v1/vulnerabilities",
                new Reply(200, "{\"data\":[{\"title\":\"x\",\"severity\":\"low\"}],\"total\":1}")))) {
            assertThat(tool(server).getDarkmoonFindings("camp_1", "high"))
                    .isEqualTo("No findings in campaign camp_1 at or above high.");
            assertThat(tool(server).getDarkmoonFindings("camp_1", null)).startsWith("Findings in campaign camp_1: 1");
        }
    }

    @Test
    void getFindings_truncatesLongDescriptionsAndLongLists() throws Exception {
        StringBuilder data = new StringBuilder("{\"data\":[");
        for (int i = 0; i < 52; i++) {
            data.append(i == 0 ? "" : ",")
                    .append("{\"title\":\"f")
                    .append(i)
                    .append("\",\"severity\":\"low\",\"description\":\"")
                    .append("d".repeat(400))
                    .append("\"}");
        }
        data.append("]}");
        try (TestServer server = startServer(Map.of("/api/v1/vulnerabilities", new Reply(200, data.toString())))) {
            String result = tool(server).getDarkmoonFindings("camp_1", null);

            assertThat(result).contains("... and 2 more findings not shown.");
            assertThat(result).contains("d".repeat(300) + "...").doesNotContain("d".repeat(301));
        }
    }

    @Test
    void getFindings_failsLoudlyWhenDataIsNotAnArray() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/vulnerabilities", new Reply(200, "{\"data\":null,\"total\":0}")))) {
            assertThat(tool(server).getDarkmoonFindings("camp_1", null))
                    .isEqualTo("Error: Darkmoon returned an unrecognised response shape.");
        }
    }

    @Test
    void getFindings_rejectsBadArgumentsWithoutCallingTheDashboard() {
        DarkmoonTool tool = unreachableTool();

        assertThat(tool.getDarkmoonFindings(" ", null)).isEqualTo("Error: campaignId must not be blank.");
        assertThat(tool.getDarkmoonFindings("camp_1", "severe"))
                .isEqualTo("Error: minSeverity must be one of info, low, medium, high, critical.");
    }

    @Test
    void startPentest_returnsTheRunId() throws Exception {
        try (TestServer server = startServer(
                Map.of("/api/v1/run/campaign", new Reply(200, "{\"run_id\":\"run_1\",\"pid\":7,\"command\":\"x\"}")))) {
            String result = tool(server).startDarkmoonPentest("https://staging.example.com", null);

            assertThat(result).contains("runId=run_1").contains("getDarkmoonRunStatus");
        }
    }

    @Test
    void startPentest_surfacesAScopeRefusal() throws Exception {
        try (TestServer server = startServer(Map.of(
                "/api/v1/run/campaign", new Reply(403, "{\"detail\":\"Target is not in the allowed scope\"}")))) {
            assertThat(tool(server).startDarkmoonPentest("example.org", null))
                    .isEqualTo("Error: Darkmoon request failed: HTTP 403. Target is not in the allowed scope");
        }
    }

    @Test
    void startPentest_reportsAMissingRunId() throws Exception {
        try (TestServer server = startServer(Map.of("/api/v1/run/campaign", new Reply(200, "{}")))) {
            assertThat(tool(server).startDarkmoonPentest("example.com", null))
                    .isEqualTo("Error: Darkmoon did not return a run id.");
        }
    }

    @Test
    void startPentest_rejectsBlankTarget() {
        assertThat(unreachableTool().startDarkmoonPentest("  ", null)).isEqualTo("Error: target must not be blank.");
    }

    @Test
    void runStatus_reportsRunning() throws Exception {
        String log = "{\"data\":[{\"type\":\"run_started\"},{\"type\":\"subagent_text\"}],\"total\":2}";
        try (TestServer server = startServer(Map.of("/api/v1/run/logs/run_1", new Reply(200, log)))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1")).isEqualTo("Run run_1 is running (2 events logged).");
        }
    }

    @Test
    void runStatus_reportsCompleted() throws Exception {
        String log = "{\"data\":[{\"type\":\"run_started\"},{\"type\":\"run_completed\"}],\"total\":2}";
        try (TestServer server = startServer(Map.of("/api/v1/run/logs/run_1", new Reply(200, log)))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1"))
                    .isEqualTo("Run run_1 is completed (2 events logged).");
        }
    }

    @Test
    void runStatus_reportsFailed() throws Exception {
        String log = "{\"data\":[{\"type\":\"run_started\"},{\"type\":\"run_error\"}],\"total\":2}";
        try (TestServer server = startServer(Map.of("/api/v1/run/logs/run_1", new Reply(200, log)))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1")).isEqualTo("Run run_1 is failed (2 events logged).");
        }
    }

    @Test
    void runStatus_treatsA404AsNotStartedYetAndAppendsTheDashboardDetail() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/run/logs/run_1", new Reply(404, "{\"detail\":\"Run log not found\"}")))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1"))
                    .startsWith("Run run_1 has no log yet: it is still starting, the run id is unknown, "
                            + "or the dashboard API path is misconfigured.")
                    .contains("HTTP 404")
                    .contains("Run log not found");
        }
    }

    @Test
    void runStatus_failsLoudlyWhenThe200DataIsEmpty() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/run/logs/run_1", new Reply(200, "{\"data\":[],\"total\":0}")))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1"))
                    .isEqualTo("Error: Darkmoon returned an unrecognised response shape.");
        }
    }

    @Test
    void runStatus_failsLoudlyWhenDataIsNotAnArray() throws Exception {
        try (TestServer server =
                startServer(Map.of("/api/v1/run/logs/run_1", new Reply(200, "{\"events\":[{\"type\":\"x\"}]}")))) {
            assertThat(tool(server).getDarkmoonRunStatus("run_1"))
                    .isEqualTo("Error: Darkmoon returned an unrecognised response shape.");
        }
    }

    @Test
    void runStatus_rejectsBlankRunId() {
        assertThat(unreachableTool().getDarkmoonRunStatus(null)).isEqualTo("Error: runId must not be blank.");
    }

    @Test
    void badCredentials_areReportedAsAnErrorString() throws Exception {
        try (TestServer server = startServer(Map.of(), new Reply(401, "{\"detail\":\"Invalid credentials\"}"))) {
            assertThat(tool(server).listDarkmoonCampaigns())
                    .isEqualTo("Error: Darkmoon request failed: HTTP 401. Invalid credentials");
        }
    }

    @Test
    void unreachableDashboard_isReportedAsAnErrorString() {
        assertThat(unreachableTool().listDarkmoonCampaigns()).startsWith("Error: ");
    }

    private static DarkmoonTool unreachableTool() {
        return DarkmoonTool.builder()
                .baseUrl("http://localhost:1")
                .username("analyst")
                .password("secret")
                .timeout(Duration.ofSeconds(2))
                .build();
    }

    private static DarkmoonTool tool(TestServer server) {
        return DarkmoonTool.builder()
                .baseUrl(server.baseUrl())
                .username("analyst")
                .password("secret")
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(Map<String, Reply> routes) throws IOException {
        return startServer(routes, new Reply(404, "{\"detail\":\"Not Found\"}"));
    }

    private static TestServer startServer(Map<String, Reply> routes, Reply fallback) throws IOException {
        Map<String, Reply> all = new ConcurrentHashMap<>(routes);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            Reply reply;
            if (path.equals("/api/v1/auth/login")) {
                reply = fallback.status() == 401 ? fallback : new Reply(200, LOGIN_OK);
            } else {
                reply = all.getOrDefault(path, fallback);
            }
            byte[] responseBytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return new TestServer(server, executor);
    }

    private record Reply(int status, String body) {}

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
