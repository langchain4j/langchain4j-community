package dev.langchain4j.community.tool.salt;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Low-level client for Salt's REST API (https://saltapp.ai), an end-to-end
 * encrypted chat where humans and AI agents are equal contacts.
 *
 * <p>Salt authenticates an agent by its own {@code api-key} header (see
 * {@code app/models/api_key.rb} and {@code ApplicationController} in
 * salt-api) rather than OAuth or a bearer token.
 */
final class SaltClient {

    private static final String DEFAULT_BASE_URL = "https://saltapp.ai";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;

    private SaltClient(Builder builder) {
        this.baseUri = normalizeBaseUrl(builder.baseUrl);
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        Duration timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        // Built explicitly rather than through the HttpClientBuilderLoader SPI: surfacing Salt's own
        // error sentences depends on JdkHttpClient putting the raw response body into
        // HttpException#getMessage() (see httpErrorMessage), so the client must not be swappable.
        this.httpClient = JdkHttpClient.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /**
     * Creates a new Salt client builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Posts a declarative "blocks" card into a chat the agent is a member of
     * (POST /api/v1/cards). See {@code CARD_PROTOCOL_SPEC.md} and
     * {@code app/models/card.rb} in salt-api for the block vocabulary.
     *
     * @param chatId the chat to post into
     * @param blocks the card's public blocks (section + actions)
     * @param text a plain-text fallback preview for the chat bubble, or {@code null}
     * @return Salt's response JSON (the message the card was attached to)
     */
    JsonNode postCard(String chatId, JsonNode blocks, String text) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("chat_id", ensureNotBlank(chatId, "chatId"));
        payload.set("blocks", blocks);
        if (text != null) {
            payload.put("text", text);
        }
        return post("/api/v1/cards", payload);
    }

    /**
     * Reads the tap history of a card this agent owns (GET /api/v1/cards/:id).
     * Owner-only: an unknown id and a card owned by someone else both surface
     * as "not found" (see {@code Api::V1::CardsController#show} in salt-api).
     *
     * @param cardId the card to read
     * @param after an interaction id or ISO-8601 timestamp cursor, or {@code null} for the full history
     * @return Salt's response JSON ({@code state}, {@code owner_id}, {@code interactions})
     */
    JsonNode getCard(String cardId, String after) {
        String path = "/api/v1/cards/" + encodePathSegment(ensureNotBlank(cardId, "cardId"));
        if (after != null && !after.isBlank()) {
            path = path + "?after=" + encodeQuery(after);
        }
        return get(path);
    }

    /**
     * Posts a plain-text message into an OPEN (unencrypted) room
     * (POST /api/v1/messages). {@code encrypted: false} is sent explicitly so
     * that an encrypted chat refuses the call outright (salt-api's own "This
     * room is encrypted. Messages must be sent encrypted." error) rather than
     * silently storing plain text as if it were ciphertext.
     *
     * @param chatId the open room to post into
     * @param text plain-text message body, capped by Salt at 4000 characters
     * @return Salt's response JSON (the created message)
     */
    JsonNode sendPlainMessage(String chatId, String text) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("chat_id", ensureNotBlank(chatId, "chatId"));
        payload.put("message", ensureNotBlank(text, "text"));
        payload.put("encrypted", false);
        return post("/api/v1/messages", payload);
    }

    /**
     * Creates an in-chat payment request (POST /api/v1/transfer_requests).
     *
     * @param receiverId the user id being asked to pay
     * @param walletId the requester's own wallet to receive the payment
     * @param amount a human-decimal amount (e.g. {@code "1.50"}), never base units
     * @param chatId the chat the request bubble is posted into
     * @param note an optional note shown on the request
     * @return Salt's response JSON (the created transfer request)
     */
    JsonNode createTransferRequest(String receiverId, String walletId, String amount, String chatId, String note) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("receiver_id", ensureNotBlank(receiverId, "receiverId"));
        payload.put("wallet_id", ensureNotBlank(walletId, "walletId"));
        payload.put("amount", ensureNotBlank(amount, "amount"));
        payload.put("chat_id", ensureNotBlank(chatId, "chatId"));
        if (note != null) {
            payload.put("message", note);
        }
        return post("/api/v1/transfer_requests", payload);
    }

    /**
     * Lists every chat this agent is a member of (GET /api/v1/chats). Salt
     * answers with a bare JSON array, each entry shaped
     * {@code {session: {...}, recent_message: {...}}}.
     *
     * @return Salt's response JSON (an array)
     */
    JsonNode listChats() {
        return get("/api/v1/chats");
    }

    private JsonNode get(String path) {
        return send(requestBuilder(path).method(GET).build());
    }

    private JsonNode post(String path, ObjectNode payload) {
        HttpRequest request = requestBuilder(path)
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .body(payload.toString())
                .build();
        return send(request);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.builder()
                .url(baseUri.resolve(path).toString())
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "langchain4j-community-tool-salt")
                .addHeader("api-key", apiKey);
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new SaltClientException("Salt returned an empty response.");
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpException e) {
            throw new SaltClientException(e.statusCode(), httpErrorMessage(e), e);
        } catch (JsonProcessingException e) {
            throw new SaltClientException("Salt returned invalid JSON.", e);
        } catch (SaltClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SaltClientException("Salt request failed.", e);
        }
    }

    // Salt's error responses are always a small JSON object -- {"error": "..."}
    // or {"errors": ["..."]} -- carrying a plain, model-safe sentence (see
    // e.g. Api::V1::MessagesController#create's "This room is encrypted.
    // Messages must be sent encrypted." and CardsController#show's 404). That
    // sentence is surfaced verbatim rather than replaced with a generic
    // status-code message, since salt-api's own copy is already written for
    // a reader (here, a model) to act on. JdkHttpClient (langchain4j-http-client-jdk)
    // puts the raw response body verbatim into HttpException's own message --
    // there is no separate accessor for it -- so that's what's parsed here.
    private static String httpErrorMessage(HttpException e) {
        String fromBody = errorFromBody(e.getMessage());
        if (fromBody != null) {
            return fromBody;
        }
        String guidance =
                switch (e.statusCode()) {
                    case 401 -> "Authentication failed. Check the agent's api-key.";
                    case 403 -> "Salt refused this request for this agent.";
                    case 404 -> "Not found.";
                    case 422 -> "Salt rejected the request as invalid.";
                    case 429 -> "Rate limited. Retry later.";
                    default -> "Request could not be completed.";
                };
        return "Salt request failed: HTTP " + e.statusCode() + ". " + guidance;
    }

    private static String errorFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            JsonNode error = node.path("error");
            if (error.isTextual() && !error.asText().isBlank()) {
                return error.asText();
            }
            JsonNode errors = node.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                StringBuilder joined = new StringBuilder();
                for (JsonNode item : errors) {
                    if (!joined.isEmpty()) {
                        joined.append(' ');
                    }
                    joined.append(item.asText());
                }
                return joined.toString();
            }
        } catch (JsonProcessingException ignored) {
            // Not JSON, or not the expected shape -- fall through to the generic message.
        }
        return null;
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private static URI normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        URI uri = URI.create(value.endsWith("/") ? value : value + "/");
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must include a host");
        }
        return uri;
    }

    /**
     * Builder for {@link SaltClient}.
     */
    static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the agent's Salt api-key.
         *
         * @param apiKey Salt agent api-key
         * @return this builder
         */
        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL. Defaults to {@code https://saltapp.ai}.
         *
         * @param baseUrl API base URL
         * @return this builder
         */
        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets connection and read timeouts.
         *
         * @param timeout connection and read timeout
         * @return this builder
         */
        Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return configured Salt client
         */
        SaltClient build() {
            return new SaltClient(this);
        }
    }
}
