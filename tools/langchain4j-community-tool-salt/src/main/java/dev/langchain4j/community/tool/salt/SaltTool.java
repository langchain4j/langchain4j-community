package dev.langchain4j.community.tool.salt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Agent tools for <a href="https://saltapp.ai">Salt</a>, an end-to-end
 * encrypted chat where humans and AI agents are equal contacts: an agent can
 * ask a human a question with tappable buttons, read back which one they
 * tapped, message an open room, request payment in a chat, and list its own
 * chats.
 *
 * <pre>{@code
 * SaltTool tool = SaltTool.builder()
 *         .apiKey(System.getenv("SALT_API_KEY"))
 *         .build();
 * }</pre>
 */
public final class SaltTool {

    private static final int MAX_BUTTON_LABEL = 40;
    private static final int MAX_QUESTION_LENGTH = 2000;
    private static final int MIN_BUTTONS = 1;
    private static final int MAX_BUTTONS = 5;
    private static final Pattern NON_ACTION_ID_CHARS = Pattern.compile("[^a-z0-9_\\-]+");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SaltClient client;

    SaltTool(SaltClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new Salt tool builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Asks a human a question in a chat, with up to five tappable buttons,
     * as a first-party Salt card. The human's tap is not returned here --
     * poll for it with {@link #readCardTaps}.
     *
     * @param chatId the chat to post into; the agent must already be a member
     * @param question the question shown on the card, up to 2000 characters
     * @param buttonLabels one to five button labels, each up to 40 characters
     * @return confirmation naming the card and message ids, or an error
     */
    @Tool("Ask a human a question in a Salt chat, with up to five tappable buttons on a card. "
            + "The human's answer is not returned here -- call readCardTaps with the returned "
            + "cardId to learn which button they tapped.")
    public String postCard(
            @P("The chat to post into; the agent must already be a member") String chatId,
            @P("The question shown on the card, up to 2000 characters") String question,
            @P("One to five button labels, each up to 40 characters") List<String> buttonLabels) {
        try {
            if (question == null || question.isBlank()) {
                return "Error: question must not be blank.";
            }
            if (question.length() > MAX_QUESTION_LENGTH) {
                return "Error: question must be at most " + MAX_QUESTION_LENGTH + " characters.";
            }
            if (buttonLabels == null || buttonLabels.size() < MIN_BUTTONS || buttonLabels.size() > MAX_BUTTONS) {
                return "Error: buttonLabels must contain " + MIN_BUTTONS + " to " + MAX_BUTTONS + " labels.";
            }
            for (String label : buttonLabels) {
                if (label == null || label.isBlank()) {
                    return "Error: a button label must not be blank.";
                }
                if (label.length() > MAX_BUTTON_LABEL) {
                    return "Error: button label \"" + label + "\" exceeds " + MAX_BUTTON_LABEL + " characters.";
                }
            }

            ArrayNode blocks = OBJECT_MAPPER.createArrayNode();
            ObjectNode section = blocks.addObject();
            section.put("type", "section");
            section.put("text", question);

            ObjectNode actions = blocks.addObject();
            actions.put("type", "actions");
            ArrayNode elements = actions.putArray("elements");
            List<String> actionIds = actionIdsFor(buttonLabels);
            for (int i = 0; i < buttonLabels.size(); i++) {
                ObjectNode button = elements.addObject();
                button.put("type", "button");
                button.put("action_id", actionIds.get(i));
                button.put("label", buttonLabels.get(i));
            }

            JsonNode response = client.postCard(chatId, blocks, question);
            String cardId = text(response.path("resource"), "id", null);
            if (cardId == null) {
                cardId = text(response, "resource_id", "unknown");
            }
            String messageId = text(response, "message_id", "unknown");
            return "Card posted. cardId=" + cardId + " messageId=" + messageId
                    + ". Call readCardTaps with this cardId to learn which button the human taps.";
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Reads the tap history of a card this agent owns. Salt refuses this
     * call with "not found" for a card the agent does not own, identically
     * to an unknown card id.
     *
     * @param cardId the card id returned by {@link #postCard}
     * @param after an interaction id or ISO-8601 timestamp to read only newer taps, or {@code null}/blank for the full history
     * @return one line per tap, newest first, or a plain message if nobody has tapped yet
     */
    @Tool("Read the tap history of a Salt card this agent posted, to learn which button a human tapped. "
            + "Call this once per check -- never in a loop.")
    public String readCardTaps(
            @P("The card id returned by postCard") String cardId,
            @P(
                            value = "Only taps after this interaction id or ISO-8601 timestamp. Omit for full history",
                            required = false)
                    String after) {
        try {
            JsonNode response = client.getCard(cardId, after);
            JsonNode interactions = response.path("interactions");
            if (!interactions.isArray() || interactions.isEmpty()) {
                return "No taps yet on card " + cardId + ".";
            }
            StringBuilder result =
                    new StringBuilder("Taps on card ").append(cardId).append(" (newest first):");
            for (JsonNode interaction : interactions) {
                result.append(System.lineSeparator())
                        .append("- action=")
                        .append(text(interaction, "action_id", "unknown"))
                        .append(" user_id=")
                        .append(text(interaction, "user_id", "unknown"))
                        .append(" at=")
                        .append(text(interaction, "created_at", "unknown"));
                String transferRequestId = text(interaction, "transfer_request_id", null);
                if (transferRequestId != null) {
                    result.append(" transfer_request_id=")
                            .append(transferRequestId)
                            .append(" transfer_request_status=")
                            .append(text(interaction, "transfer_request_status", "unknown"));
                }
            }
            return result.toString();
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Posts a plain-text message into an open (unencrypted) Salt room. Salt
     * refuses this on an encrypted chat with its own plain-language error,
     * which is surfaced verbatim rather than replaced.
     *
     * @param chatId the open room to post into
     * @param text plain-text message, up to 4000 characters
     * @return confirmation, or Salt's own refusal if the chat is encrypted
     */
    @Tool("Send a plain-text message into an OPEN (unencrypted) Salt room. This does not work on an "
            + "encrypted 1:1 or group chat -- Salt refuses those, since this tool never handles "
            + "PGP encryption.")
    public String sendMessage(
            @P("The open room to post into") String chatId,
            @P("Plain-text message, up to 4000 characters") String text) {
        try {
            JsonNode response = client.sendPlainMessage(chatId, text);
            return "Message sent. message_id=" + text(response, "message_id", "unknown");
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Creates an in-chat payment request. The human confirms and signs the
     * payment themselves in Salt; this tool only raises the request.
     *
     * @param chatId the chat the request bubble is posted into
     * @param receiverId the user id being asked to pay
     * @param walletId the agent's own wallet to receive the payment
     * @param amount a human-decimal amount, e.g. {@code "1.50"} -- never base units
     * @param note an optional note shown on the request, or {@code null}
     * @return confirmation naming the created request, or an error
     */
    @Tool("Create an in-chat payment request in Salt, asking a human to pay a given amount to one of "
            + "this agent's wallets. The human still confirms and signs the payment themselves; this "
            + "tool only raises the request.")
    public String createPaymentRequest(
            @P("The chat the request bubble is posted into") String chatId,
            @P("The user id being asked to pay") String receiverId,
            @P("This agent's own wallet id to receive the payment") String walletId,
            @P("A human-decimal amount, e.g. \"1.50\" -- never base units") String amount,
            @P(value = "An optional note shown on the request", required = false) String note) {
        try {
            JsonNode response = client.createTransferRequest(receiverId, walletId, amount, chatId, note);
            return "Payment request created. id=" + text(response, "id", "unknown") + " status="
                    + text(response, "status", "unknown");
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    /**
     * Lists every chat this agent is a member of.
     *
     * @return one line per chat, or a plain message if there are none
     */
    @Tool("List every Salt chat this agent is currently a member of.")
    public String listChats() {
        try {
            JsonNode response = client.listChats();
            if (!response.isArray() || response.isEmpty()) {
                return "No chats yet.";
            }
            StringBuilder result = new StringBuilder("Chats:");
            for (JsonNode entry : response) {
                JsonNode session = entry.path("session");
                String name = text(session, "name", "");
                String label = name.isBlank() ? "(unnamed)" : name;
                result.append(System.lineSeparator())
                        .append("- id=")
                        .append(text(session, "id", "unknown"))
                        .append(" name=")
                        .append(label)
                        .append(" encrypted=")
                        .append(session.path("encrypted").asBoolean(true))
                        .append(" members=")
                        .append(
                                session.path("users").isArray()
                                        ? session.path("users").size()
                                        : 0)
                        .append(" unread=")
                        .append(session.path("unread_count").asInt(0));
            }
            return result.toString();
        } catch (RuntimeException e) {
            return formatError(e);
        }
    }

    // Slugifies each label into a valid, unique action_id: 1-40 chars of
    // a-z 0-9 _ - (Card model's validate_actions in salt-api). A label that
    // slugifies to nothing (e.g. all emoji) falls back to "option_<n>"; a
    // collision between two distinct labels is disambiguated the same way.
    private static List<String> actionIdsFor(List<String> labels) {
        List<String> ids = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String base = NON_ACTION_ID_CHARS
                    .matcher(labels.get(i).toLowerCase(Locale.ROOT).trim().replace(' ', '_'))
                    .replaceAll("");
            if (base.isBlank()) {
                base = "option";
            }
            if (base.length() > MAX_BUTTON_LABEL) {
                base = base.substring(0, MAX_BUTTON_LABEL);
            }
            String candidate = base;
            int suffix = 2;
            while (ids.contains(candidate)) {
                String suffixed = "_" + suffix;
                int trim = Math.max(0, base.length() - suffixed.length());
                candidate = base.substring(0, Math.min(base.length(), trim)) + suffixed;
                suffix++;
            }
            ids.add(candidate);
        }
        return ids;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        return value.isTextual() ? value.asText() : value.asText(fallback);
    }

    private static String formatError(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Error: Salt request failed." : "Error: " + message;
    }

    /**
     * Builder for {@link SaltTool}.
     */
    public static final class Builder {

        private String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the agent's Salt api-key.
         *
         * @param apiKey Salt agent api-key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL. Defaults to {@code https://saltapp.ai}.
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
         * @return configured Salt tool
         */
        public SaltTool build() {
            SaltClient.Builder clientBuilder =
                    SaltClient.builder().apiKey(apiKey).timeout(timeout);
            if (baseUrl != null) {
                clientBuilder.baseUrl(baseUrl);
            }
            return new SaltTool(clientBuilder.build());
        }
    }
}
