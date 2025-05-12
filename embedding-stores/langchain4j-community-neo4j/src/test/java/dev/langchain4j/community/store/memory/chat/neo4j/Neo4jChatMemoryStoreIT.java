package dev.langchain4j.community.store.memory.chat.neo4j;

import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_ID_PROP;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_LAST_REL_TYPE;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_MEMORY_LABEL;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_MESSAGE_LABEL;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_MESSAGE_PROP;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_REL_TYPE_NEXT;
import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_SIZE_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class Neo4jChatMemoryStoreIT {

    protected static final String USERNAME = "neo4j";
    protected static final String ADMIN_PASSWORD = "adminPass";
    protected static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");

    protected static Driver driver;
    private Neo4jChatMemoryStore memoryStore;
    private final String messageId = "someUserId";

    @Container
    protected static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(
                    DockerImageName.parse("neo4j:" + NEO4J_VERSION))
            .withPlugins("apoc")
            .withAdminPassword(ADMIN_PASSWORD);

    @BeforeAll
    static void beforeAll() {
        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
    }

    @BeforeEach
    void setUp() {
        memoryStore = Neo4jChatMemoryStore.builder().driver(driver).build();
    }

    @AfterEach
    void afterEach() {
        memoryStore.deleteMessages(messageId);
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();

        final List<Record> nodes = driver.session().run("MATCH (n) RETURN n").list();
        assertThat(nodes).isEmpty();
    }

    @AfterAll
    static void afterAll() {
        driver.close();
    }

    @Test
    void should_set_and_update_messages_into_neo4j() {
        // given
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();

        List<ChatMessage> chatMessages = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages);
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages);

        List<Content> userMsgContents = List.of(new ImageContent("someCatImageUrl"));
        final List<ChatMessage> chatNewMessages =
                List.of(new UserMessage("What do you see in this image?", userMsgContents));
        memoryStore.updateMessages(messageId, chatNewMessages);

        // then
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(4);
        final ArrayList<ChatMessage> chatMessages1 = new ArrayList<>(chatMessages);
        chatMessages1.addAll(chatNewMessages);
        assertThat(messages).isEqualTo(chatMessages1);
    }

    @Test
    void should_init_memory_store_using_withBasicAuth() {
        // given
        List<ChatMessage> chatMessages = createChatMessages();
        final Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();
        memoryStore.updateMessages(messageId, chatMessages);
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages);

        // when
        memoryStore.deleteMessages(messageId);

        // then
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_delete_messages_from_neo4j() {
        // given
        List<ChatMessage> chatMessages = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages);
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages);
        checkEntitiesCreated(
                DEFAULT_ID_PROP,
                DEFAULT_MESSAGE_PROP,
                DEFAULT_MEMORY_LABEL,
                DEFAULT_LAST_REL_TYPE,
                DEFAULT_MESSAGE_LABEL,
                DEFAULT_REL_TYPE_NEXT);

        // when
        memoryStore.deleteMessages(messageId);

        // then
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_only_delete_messages_with_correct_memory_id() {
        final String anotherMessageId = "anotherId";
        final List<ChatMessage> chatMessages1 = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages1);

        final List<ChatMessage> chatMessages2 = createChatMessages();
        memoryStore.updateMessages(anotherMessageId, chatMessages2);

        List<ChatMessage> messagesBefore = memoryStore.getMessages(messageId);
        assertThat(messagesBefore).hasSize(3);
        assertThat(messagesBefore).isEqualTo(chatMessages1);

        List<ChatMessage> messages2Before = memoryStore.getMessages(anotherMessageId);
        assertThat(messages2Before).hasSize(3);
        assertThat(messages2Before).isEqualTo(chatMessages2);

        memoryStore.deleteMessages(messageId);

        List<ChatMessage> messagesAfterDelete = memoryStore.getMessages(messageId);
        assertThat(messagesAfterDelete).isEmpty();

        List<ChatMessage> messages2AfterDelete = memoryStore.getMessages(anotherMessageId);
        assertThat(messages2AfterDelete).hasSize(3);
        assertThat(messages2AfterDelete).isEqualTo(chatMessages2);

        memoryStore.deleteMessages(anotherMessageId);
        List<ChatMessage> messagesAfter2ndDelete = memoryStore.getMessages(anotherMessageId);
        assertThat(messagesAfter2ndDelete).isEmpty();
    }

    @Test
    void should_only_delete_messages_with_custom_labels_and_rel_type() {
        final String memoryLabel = "Label ` to \\ sanitize";
        final String lastMessageRel = "Rel ` to \\ sanitize";
        final String messageLabel = "Second Label ` to  sanitize";
        final String nextMessageRel = "Second Rel \\ to  sanitize";
        final String idPropCustom = "idPropCustom";
        final String msgPropCustom = "msgPropCustom";
        Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                .memoryLabel(memoryLabel)
                .messageLabel(messageLabel)
                .lastMessageRelType(lastMessageRel)
                .nextMessageRelType(nextMessageRel)
                .idProperty(idPropCustom)
                .messageProperty(msgPropCustom)
                .build();
        final List<ChatMessage> chatMessages1 = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages1);
        final List<Record> list =
                driver.session().run("MATCH (n:Memory) RETURN n").list();
        assertThat(list).isEmpty();
        final List<Record> list2 =
                driver.session().run("MATCH (n:Message) RETURN n").list();
        assertThat(list2).isEmpty();

        checkEntitiesCreated(idPropCustom, msgPropCustom, memoryLabel, lastMessageRel, messageLabel, nextMessageRel);

        memoryStore.deleteMessages(messageId);
        List<ChatMessage> messagesAfterDelete = memoryStore.getMessages(messageId);
        assertThat(messagesAfterDelete).isEmpty();
    }

    private void checkEntitiesCreated(
            String idPropToSanitize,
            String msgPropToSanitize,
            String memoryLabel,
            String lastMessageRel,
            String messageLabel,
            String nextMessageRel) {
        // the single() method Throws `NoSuchRecordException`, if there is not exactly one record left in the stream
        final Record record =
                driver.session().run("MATCH p=()-[]->()<-[]-()<-[]-() RETURN p").single();
        final Path path = record.get("p").asPath();
        final Iterator<Node> nodeIterator = path.nodes().iterator();
        Node node = nodeIterator.next();
        Map<String, Object> actualProps = node.asMap();
        assertThat(actualProps).isEqualTo(Map.of(idPropToSanitize, messageId));
        assertThat(node.labels()).containsExactly(memoryLabel);

        node = nodeIterator.next();
        actualProps = node.asMap();
        assertThat(actualProps).containsKey(msgPropToSanitize);
        assertThat(node.labels()).containsExactly(messageLabel);

        node = nodeIterator.next();
        actualProps = node.asMap();
        assertThat(actualProps).containsKey(msgPropToSanitize);
        assertThat(node.labels()).containsExactly(messageLabel);

        node = nodeIterator.next();
        actualProps = node.asMap();
        assertThat(actualProps).containsKey(msgPropToSanitize);
        assertThat(node.labels()).containsExactly(messageLabel);

        assertThat(nodeIterator.hasNext()).isFalse();

        final Iterator<Relationship> relIterator = path.relationships().iterator();
        String relType = relIterator.next().type();
        assertThat(relType).isEqualTo(lastMessageRel);

        relType = relIterator.next().type();
        assertThat(relType).isEqualTo(nextMessageRel);

        relType = relIterator.next().type();
        assertThat(relType).isEqualTo(nextMessageRel);

        assertThat(relIterator.hasNext()).isFalse();
    }

    @Test
    void should_only_search_first_three_messages_besides_last_message() {
        final int size = 3;
        Neo4jChatMemoryStore memoryStore =
                Neo4jChatMemoryStore.builder().driver(driver).size(size).build();

        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize((int) (size + 1));

        final List<ChatMessage> expectedChatMessages = new ArrayList<>();
        expectedChatMessages.add(new AiMessage("baz"));
        expectedChatMessages.addAll(createChatMessages());
        assertThat(messages).isEqualTo(expectedChatMessages);
    }

    @Test
    void should_only_search_first_ten_messages_besides_last_message() {
        Neo4jChatMemoryStore memoryStore =
                Neo4jChatMemoryStore.builder().driver(driver).build();

        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize((int) (DEFAULT_SIZE_VALUE + 1));

        final List<ChatMessage> expectedChatMessages = new ArrayList<>();
        expectedChatMessages.add(new UserMessage("bar"));
        expectedChatMessages.add(new AiMessage("baz"));
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.addAll(createChatMessages());
        assertThat(messages).isEqualTo(expectedChatMessages);
    }

    @Test
    void should_search_all_messages() {
        Neo4jChatMemoryStore memoryStore =
                Neo4jChatMemoryStore.builder().driver(driver).size(0).build();

        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(12);
        assertThat(messages).isEqualTo(chatMessages1);
    }

    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void getMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.getMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(messageId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> memoryStore.updateMessages(messageId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void constructor_driver_null() {
        assertThatThrownBy(() -> Neo4jChatMemoryStore.builder().driver(null).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("driver cannot be null");
    }

    private static List<ChatMessage> createChatMessages() {
        return new ArrayList<>(List.of(new SystemMessage("foo"), new UserMessage("bar"), new AiMessage("baz")));
    }
}
