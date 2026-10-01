package dev.langchain4j.community.tool.salt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Drives every {@link SaltTool} method against a real Salt server without an LLM in the loop, so
 * it needs only an agent api-key ({@code SALT_API_KEY}; {@code SALT_BASE_URL} defaults to
 * https://saltapp.ai). It opens one private, single-member, open (unencrypted) group as scratch
 * space, so nothing it posts reaches another account.
 */
@EnabledIfEnvironmentVariable(named = "SALT_API_KEY", matches = ".+")
class SaltToolLiveIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String UNKNOWN_UUID = "00000000-0000-4000-8000-000000000000";

    @Test
    void exercisesEveryToolAgainstTheLiveApi() throws Exception {
        String apiKey = System.getenv("SALT_API_KEY");
        String baseUrl = System.getenv().getOrDefault("SALT_BASE_URL", "https://saltapp.ai");
        SaltTool tool = SaltTool.builder().apiKey(apiKey).baseUrl(baseUrl).build();

        String chatId = createScratchRoom(baseUrl, apiKey, false);

        String chats = tool.listChats();
        System.out.println("listChats -> " + chats);
        assertThat(chats).startsWith("Chats:").contains("id=" + chatId + " name=SALT-langchain4j-it encrypted=false");

        String sent = tool.sendMessage(chatId, "hello from the langchain4j integration test");
        System.out.println("sendMessage -> " + sent);
        assertThat(sent).startsWith("Message sent. message_id=").doesNotContain("unknown");

        String posted = tool.postCard(chatId, "Ship it?", List.of("Yes please!", "yes", "👍"));
        System.out.println("postCard -> " + posted);
        assertThat(posted).startsWith("Card posted. cardId=").doesNotContain("unknown");
        String cardId = posted.replaceAll(".*cardId=(\\S+) .*", "$1");

        String taps = tool.readCardTaps(cardId, null);
        System.out.println("readCardTaps -> " + taps);
        assertThat(taps).isEqualTo("No taps yet on card " + cardId + ".");

        String unknownCard = tool.readCardTaps(UNKNOWN_UUID, null);
        System.out.println("readCardTaps(unknown) -> " + unknownCard);
        assertThat(unknownCard).isEqualTo("Error: Not found");

        String unknownRoom = tool.sendMessage(UNKNOWN_UUID, "hi");
        System.out.println("sendMessage(unknown chat) -> " + unknownRoom);
        assertThat(unknownRoom).startsWith("Error:");

        String request = tool.createPaymentRequest(chatId, UNKNOWN_UUID, UNKNOWN_UUID, "1.50", "it");
        System.out.println("createPaymentRequest(unknown ids) -> " + request);
        assertThat(request).startsWith("Error:");

        String encryptedRoomId = createScratchRoom(baseUrl, apiKey, true);
        String refused = tool.sendMessage(encryptedRoomId, "plain text into an encrypted room");
        System.out.println("sendMessage(encrypted room) -> " + refused);
        assertThat(refused).isEqualTo("Error: This room is encrypted. Messages must be sent encrypted.");

        String badKey = new SaltTool(SaltClient.builder()
                        .apiKey("not-a-real-key")
                        .baseUrl(baseUrl)
                        .build())
                .listChats();
        System.out.println("listChats(bad key) -> " + badKey);
        assertThat(badKey).startsWith("Error:");
    }

    private static String createScratchRoom(String baseUrl, String apiKey, boolean encrypted) throws Exception {
        String body = OBJECT_MAPPER
                .createObjectNode()
                .put("name", "SALT-langchain4j-it")
                .put("public", false)
                .put("encrypted", encrypted)
                .set("contact_ids", OBJECT_MAPPER.createArrayNode())
                .toString();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/chats"))
                                .header("api-key", apiKey)
                                .header("Content-Type", "application/json")
                                .header("Accept", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("scratch room creation: " + response.body())
                .isBetween(200, 201);
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        JsonNode id = json.path("session").path("id").isMissingNode()
                ? json.path("id")
                : json.path("session").path("id");
        assertThat(id.asText()).isNotBlank();
        return id.asText();
    }
}
