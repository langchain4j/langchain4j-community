package dev.langchain4j.community.tool.darkmoon;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Agent tools for <a href="https://github.com/ASCIT31/Dark-Moon">Darkmoon</a>, an
 * open source (GPL-3.0) autonomous AI penetration testing platform: an agent can
 * list campaigns, read the findings of one campaign, start a pentest and check on
 * a run.
 *
 * <p>The tools talk to the Dashboard API of a Darkmoon instance that you operate
 * yourself; there is no public hosted endpoint. The dashboard and its API are part
 * of Darkmoon Pro, while the engine and CLI are open source.
 *
 * <p>Only run assessments against systems you own or are explicitly authorised to
 * test. Findings can include false positives and must be reviewed by a human.
 *
 * <pre>{@code
 * DarkmoonTool tool = DarkmoonTool.builder()
 *         .baseUrl(System.getenv("DARKMOON_BASE_URL"))
 *         .username(System.getenv("DARKMOON_USERNAME"))
 *         .password(System.getenv("DARKMOON_PASSWORD"))
 *         .build();
 * }</pre>
 */
public final class DarkmoonTool {

    private static final List<String> SEVERITIES = List.of("info", "low", "medium", "high", "critical");
    private static final int MAX_FINDINGS_LISTED = 50;
    private static final int MAX_CAMPAIGNS_LISTED = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    private final DarkmoonClient client;

    DarkmoonTool(DarkmoonClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new Darkmoon tool builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lists the campaigns known to the dashboard, newest first.
     *
     * @return one line per campaign, or a plain message if there is none
     */
    @Tool("List the Darkmoon pentest campaigns, newest first, with their id, date and status. "
            + "Use a campaign id with getDarkmoonFindings to read its findings.")
    public String listDarkmoonCampaigns() {
        try {
            JsonNode data = client.listCampaigns().path("data");
            if (!data.isArray() || data.isEmpty()) {
                return "No campaigns yet.";
            }
            List<JsonNode> campaigns = new ArrayList<>();
            data.forEach(campaigns::add);
            campaigns.sort(
                    Comparator.comparing((JsonNode c) -> text(c, "date", "")).reversed());
            StringBuilder result = new StringBuilder("Campaigns (newest first):");
            int listed = 0;
            for (JsonNode campaign : campaigns) {
                if (listed == MAX_CAMPAIGNS_LISTED) {
                    result.append(System.lineSeparator())
                            .append("... and ")
                            .append(campaigns.size() - listed)
                            .append(" older campaigns not shown.");
                    break;
                }
                result.append(System.lineSeparator())
                        .append("- id=")
                        .append(text(campaign, "id", "unknown"))
                        .append(" date=")
                        .append(text(campaign, "date", "unknown"))
                        .append(" status=")
                        .append(text(campaign, "status", "unknown"));
                listed++;
            }
            return result.toString();
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads the findings of one campaign, most severe first, optionally
     * restricted to a minimum severity.
     *
     * @param campaignId the campaign id returned by {@link #listDarkmoonCampaigns}
     * @param minSeverity the lowest severity to report (info, low, medium, high or critical), or {@code null}/blank for all
     * @return a severity summary followed by one entry per finding, or an error
     */
    @Tool("Read the findings of one Darkmoon campaign, most severe first, with severity, CVSS score, status "
            + "(exploited, confirmed or unconfirmed), category, endpoint and CVE. Findings may be false "
            + "positives and need human review.")
    public String getDarkmoonFindings(
            @P("The campaign id returned by listDarkmoonCampaigns") String campaignId,
            @P(
                            value =
                                    "Only report findings at or above this severity: info, low, medium, high or critical. "
                                            + "Omit for all findings",
                            required = false)
                    String minSeverity) {
        try {
            if (campaignId == null || campaignId.isBlank()) {
                return "Error: campaignId must not be blank.";
            }
            int threshold = -1;
            if (minSeverity != null && !minSeverity.isBlank()) {
                threshold = SEVERITIES.indexOf(minSeverity.trim().toLowerCase(Locale.ROOT));
                if (threshold < 0) {
                    return "Error: minSeverity must be one of " + String.join(", ", SEVERITIES) + ".";
                }
            }

            JsonNode response = client.listVulnerabilities(campaignId.trim());
            List<JsonNode> findings = new ArrayList<>();
            for (JsonNode finding : response.path("data")) {
                if (rank(finding) >= threshold) {
                    findings.add(finding);
                }
            }
            if (findings.isEmpty()) {
                return "No findings in campaign " + campaignId.trim()
                        + (threshold >= 0 ? " at or above " + SEVERITIES.get(threshold) : "") + ".";
            }
            findings.sort(Comparator.comparingInt(DarkmoonTool::rank).reversed());

            StringBuilder result = new StringBuilder("Findings in campaign ")
                    .append(campaignId.trim())
                    .append(": ")
                    .append(findings.size())
                    .append(" (")
                    .append(severityCounts(findings))
                    .append(")");
            int listed = 0;
            for (JsonNode finding : findings) {
                if (listed == MAX_FINDINGS_LISTED) {
                    result.append(System.lineSeparator())
                            .append("... and ")
                            .append(findings.size() - listed)
                            .append(" more findings not shown.");
                    break;
                }
                result.append(System.lineSeparator()).append(formatFinding(finding));
                listed++;
            }
            return result.toString();
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Starts a pentest against one target. The run continues on the Darkmoon
     * side; check on it with {@link #getDarkmoonRunStatus}.
     *
     * @param target the host or URL to assess; the user must be authorised to test it
     * @param program an optional program name or rules of engagement, or {@code null}
     * @return the run id, or the dashboard's own refusal (for example an out-of-scope target)
     */
    @Tool("Start an autonomous Darkmoon pentest against ONE target and return its run id. Only use a target "
            + "the user owns or is explicitly authorised to test, and only when the user asked for a pentest. "
            + "The run continues in the background: poll getDarkmoonRunStatus, never in a tight loop.")
    public String startDarkmoonPentest(
            @P("The host or URL to assess. Must be authorised by the user") String target,
            @P(value = "Optional program name or rules of engagement", required = false) String program) {
        try {
            if (target == null || target.isBlank()) {
                return "Error: target must not be blank.";
            }
            JsonNode response = client.startCampaign(target.trim(), program);
            String runId = text(response, "run_id", null);
            if (runId == null) {
                return "Error: Darkmoon did not return a run id.";
            }
            return "Pentest started. runId=" + runId
                    + ". Call getDarkmoonRunStatus with this runId to follow it, then listDarkmoonCampaigns "
                    + "and getDarkmoonFindings once it has completed.";
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reports whether a run is still going, from its event log.
     *
     * @param runId the run id returned by {@link #startDarkmoonPentest}
     * @return running, completed or failed, with the number of events logged so far
     */
    @Tool("Check whether a Darkmoon pentest run started with startDarkmoonPentest is still running, has "
            + "completed or has failed. Call it once per check, never in a loop.")
    public String getDarkmoonRunStatus(@P("The run id returned by startDarkmoonPentest") String runId) {
        try {
            if (runId == null || runId.isBlank()) {
                return "Error: runId must not be blank.";
            }
            JsonNode events;
            try {
                events = client.getRunLog(runId.trim()).path("data");
            } catch (DarkmoonClientException e) {
                if (e.statusCode() == 404) {
                    return "Run " + runId.trim() + " has no log yet: it is still starting, or the run id is unknown.";
                }
                throw e;
            }
            String state = "running";
            int count = 0;
            for (JsonNode event : events) {
                count++;
                String type = text(event, "type", "");
                if ("run_completed".equals(type)) {
                    state = "completed";
                } else if ("run_error".equals(type)) {
                    state = "failed";
                }
            }
            return "Run " + runId.trim() + " is " + state + " (" + count + " events logged).";
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    private static String formatFinding(JsonNode finding) {
        StringBuilder line = new StringBuilder("- [")
                .append(text(finding, "severity", "unknown").toUpperCase(Locale.ROOT))
                .append("] ")
                .append(text(finding, "title", "(untitled)"));
        JsonNode cvss = finding.path("cvss_score");
        if (cvss.isNumber()) {
            line.append(" cvss=").append(cvss.asText());
        }
        line.append(" status=").append(text(finding, "status", "unknown"));
        appendField(line, "category", text(finding, "category", null));
        appendField(line, "endpoint", text(finding, "endpoint", null));
        appendField(line, "cve", text(finding, "cve", null));
        String description = text(finding, "description", null);
        if (description != null) {
            String flat = description.replaceAll("\\s+", " ").trim();
            if (flat.length() > MAX_DESCRIPTION_LENGTH) {
                flat = flat.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
            }
            line.append(System.lineSeparator()).append("  ").append(flat);
        }
        return line.toString();
    }

    private static void appendField(StringBuilder line, String name, String value) {
        if (value != null) {
            line.append(' ').append(name).append('=').append(value);
        }
    }

    private static String severityCounts(List<JsonNode> findings) {
        int[] counts = new int[SEVERITIES.size()];
        int unknown = 0;
        for (JsonNode finding : findings) {
            int rank = SEVERITIES.indexOf(text(finding, "severity", "").toLowerCase(Locale.ROOT));
            if (rank >= 0) {
                counts[rank]++;
            } else {
                unknown++;
            }
        }
        List<String> parts = new ArrayList<>();
        for (int i = SEVERITIES.size() - 1; i >= 0; i--) {
            if (counts[i] > 0) {
                parts.add(counts[i] + " " + SEVERITIES.get(i));
            }
        }
        if (unknown > 0) {
            parts.add(unknown + " unrated");
        }
        return String.join(", ", parts);
    }

    // A finding with a missing or unknown severity ranks -1: below every real severity, so a minSeverity filter
    // drops it, while an unfiltered read (threshold -1) still reports it.
    private static int rank(JsonNode finding) {
        return SEVERITIES.indexOf(text(finding, "severity", "").toLowerCase(Locale.ROOT));
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private static String formatError(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Error: Darkmoon request failed." : "Error: " + message;
    }

    /**
     * Builder for {@link DarkmoonTool}.
     */
    public static final class Builder {

        private String baseUrl;
        private String username;
        private String password;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the dashboard API base URL of your Darkmoon instance, for example {@code http://localhost:8000}.
         *
         * @param baseUrl dashboard API base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the dashboard username.
         *
         * @param username dashboard username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the dashboard password.
         *
         * @param password dashboard password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
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
         * @return configured Darkmoon tool
         */
        public DarkmoonTool build() {
            return new DarkmoonTool(DarkmoonClient.builder()
                    .baseUrl(baseUrl)
                    .username(username)
                    .password(password)
                    .timeout(timeout)
                    .build());
        }
    }
}
