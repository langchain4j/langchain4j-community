package dev.langchain4j.community.store.memory.chat.neo4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Neo4jChatMemoryStore implements ChatMemoryStore {
    public static final String DEFAULT_MEMORY_LABEL = "Memory";
    public static final String DEFAULT_MESSAGE_LABEL = "Message";
    public static final String DEFAULT_LAST_REL_TYPE = "LAST_MESSAGE";
    public static final String DEFAULT_REL_TYPE_NEXT = "NEXT";
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_MESSAGE_PROP = "message";
    public static final String DEFAULT_DATABASE_NAME = "neo4j";
    public static final long DEFAULT_WINDOW_VALUE = 10L;

    private final Driver driver;
    private final SessionConfig config;
    private final String memoryLabel;
    private final String messageLabel;
    private final String lastMessageRelType;
    private final String nextMessageRelType;
    private final String idProperty;
    private final String messageProperty;
    private final long size;
    
    /**
     * Creates an instance of Neo4jChatMemoryStore
     * 
     * @param driver the {@link Driver} (required)
     * @param config the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase(`databaseName`)`)
     * @param memoryLabel the node label to be used for the memory ID (default: "Memory")
     * @param messageLabel the node label to be used for the message (default: "Message")
     * @param idProperty the optional memory ID property name of the node (default: "id")
     * @param messageProperty the property name to be used for the message text (default: "message")
     * @param lastMessageRelType the relationship type to be used to store the last message (default: "LAST_MESSAGE")
     * @param nextMessageRelType the relationship type to be used to store the next messages (default: "NEXT")
     * @param databaseName the optional database name (default: "neo4j")
     * @param size the optional message size to be retrieved from {@link Neo4jChatMemoryStore#getMessages(Object)}} (default: 10)
     *             If the size is 0 or negative, all messages will be retrieved
     */
    public Neo4jChatMemoryStore(final Driver driver, final SessionConfig config, final String memoryLabel, final String messageLabel, final String lastMessageRelType, String nextMessageRelType, String idProperty, String messageProperty, String databaseName, Long size) {
        /* required configs */
        this.driver = ensureNotNull(driver, "driver");

        /* optional configs */
        String dbName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.config = getOrDefault(config, SessionConfig.forDatabase(dbName));
        this.memoryLabel = getOrDefault(memoryLabel, DEFAULT_MEMORY_LABEL);
        this.messageLabel = getOrDefault(messageLabel, DEFAULT_MESSAGE_LABEL);
        this.lastMessageRelType = getOrDefault(lastMessageRelType, DEFAULT_LAST_REL_TYPE);
        this.nextMessageRelType = getOrDefault(nextMessageRelType, DEFAULT_REL_TYPE_NEXT);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.messageProperty = getOrDefault(messageProperty, DEFAULT_MESSAGE_PROP);
        this.size = getOrDefault(size, DEFAULT_WINDOW_VALUE);
    }

    public static Builder builder() {
        return new Builder();
    }

    private void createSessionNode(final Object memoryId) {
        try (var session = session()) {
            final Map<String, Object> params = Map.of("label", memoryLabel, "window", size, "memoryId", memoryId);
            final String query = String.format("MERGE (s:%s {%s: $memoryId})", memoryLabel, idProperty);
            session.run(query, params);
        } catch (Neo4jException e) {
            getDescriptiveProcedureNotFoundError(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryIdObj) {
        final String memoryId = toMemoryIdString(memoryIdObj);
        try (var session = session()) {

            String windowPar = this.size < 1 ? "" : Long.toString(this.size);
            final Map<String, Object> params = Map.of("label", memoryLabel, "window", windowPar, "memoryId", memoryId);

            final String query = String.format("""
                    MATCH (s:%1$s)-[:%2$s]->(lastNode)
                    WHERE s.%5$s = $memoryId MATCH p=(lastNode)<-[:%3$s*0..%4$s]-()
                    WITH p, length(p) AS length
                    ORDER BY length DESC LIMIT 1
                    UNWIND reverse(nodes(p)) AS node
                    RETURN node.%6$s AS msg""",
                    memoryLabel, lastMessageRelType, nextMessageRelType, windowPar, idProperty, messageProperty);
            
            final List<ChatMessage> messages = session.run(query, params)
                    .stream()
                    .map(i -> i.get("msg").asString(null))
                    .filter(Objects::nonNull)
                    .map(ChatMessageDeserializer::messageFromJson)
                    .toList();
            return messages;
        } catch (Neo4jException e) {
            getDescriptiveProcedureNotFoundError(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateMessages(final Object memoryIdObj, final List<ChatMessage> messages) {
        final String memoryId = toMemoryIdString(memoryIdObj);

        ensureNotEmpty(messages, "messages");
        final List<Map<String, String >> messagesValues = messages.stream()
                .map(ChatMessageSerializer::messageToJson)
                .map(i -> Map.of(messageProperty, i))
                .toList();

        createSessionNode(memoryId);

        try (var session = session()) {
            final String query = String.format("""
                    MATCH (s:%1$s) WHERE s.%4$s = $memoryId
                    OPTIONAL MATCH (s)-[lastRel:%2$s]->(lastNode)
                    CALL apoc.create.nodes([$label], $messages)
                    YIELD node
                    WITH collect(node) AS nodes, s, lastNode, lastRel
                    CALL apoc.nodes.link(nodes, $relType, {avoidDuplicates: true})
                    WITH nodes[-1] AS new, s, lastNode, lastRel
                    CREATE (s)-[:%2$s]->(new)
                    WITH new, lastRel, lastNode WHERE lastNode IS NOT NULL
                    CREATE (lastNode)-[:%3$s]->(new)
                    DELETE lastRel

                    """, memoryLabel, lastMessageRelType, nextMessageRelType, idProperty);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", nextMessageRelType, 
                    "label", messageLabel, 
                    "messages", messagesValues);

            session.run(query, params);
        }
    }

    @Override
    public void deleteMessages(final Object memoryIdObj) {
        final String memoryId = toMemoryIdString(memoryIdObj);
        try (var session = session()) {

            final String query = String.format("""
                    MATCH (s:%1$s)
                    WHERE s.%4$s = $memoryId
                    OPTIONAL MATCH p=(s)-[lastRel:%2$s]->(lastNode)<-[:%3$s*0..]-()
                    WITH s, p, length(p) AS length ORDER BY length DESC LIMIT 1
                    //MATCH p=(lastNode)<-[:%3$s*0..]-()
                    DETACH DELETE s, p""", memoryLabel, lastMessageRelType, nextMessageRelType, idProperty);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", lastMessageRelType,
                    "label", memoryLabel);


            session.run(query, params);

        } catch (Neo4jException e) {
            getDescriptiveProcedureNotFoundError(e);
            throw new RuntimeException(e);
        }
    }

    private static void getDescriptiveProcedureNotFoundError(Neo4jException e) {
        if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
            throw new Neo4jException("Please ensure the APOC plugin is installed in Neo4j", e);
        }
    }

    private static String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    private Session session() {
        return this.driver.session(this.config);
    }

    public static class Builder {
        private Driver driver;
        private SessionConfig config;
        private String memoryLabel;
        private String messageLabel;
        private String lastMessageRelType;
        private String nextMessageRelType;
        private String idProperty;
        private String messageProperty;
        private String databaseName;
        private Long size;

        /**
         * @param driver the {@link Driver} (required)
         */
        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }
        
        /**
         * @param config the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase(`databaseName`)`)
         */
        public Builder config(SessionConfig config) {
            this.config = config;
            return this;
        }

        /**
         * @param memoryLabel the node label to be used for the memory ID (default: "Memory")
         */
        public Builder memoryLabel(String memoryLabel) {
            this.memoryLabel = memoryLabel;
            return this;
        }

        /**
         * @param messageLabel the node label to be used for the message (default: "Message")
         */
        public Builder messageLabel(String messageLabel) {
            this.messageLabel = messageLabel;
            return this;
        }

        /**
         * @param idProperty the optional memory ID property name of the node (default: "id")
         */
        public Builder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        /**
         * @param messageProperty the property name to be used for the message text (default: "message")
         */
        public Builder messageProperty(String messageProperty) {
            this.messageProperty = messageProperty;
            return this;
        }


        /**
         * @param lastMessageRelType the relationship type to be used to store the last message (default: "LAST_MESSAGE")
         */
        public Builder lastMessageRelType(String lastMessageRelType) {
            this.lastMessageRelType = lastMessageRelType;
            return this;
        }

        /**
         * @param nextMessageRelType the relationship type to be used to store the next messages (default: "NEXT")
         */
        public Builder nextMessageRelType(String nextMessageRelType) {
            this.nextMessageRelType = nextMessageRelType;
            return this;
        }

        /**
         * @param databaseName the optional database name (default: "neo4j")
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * @param size the optional message size to be retrieved from {@link Neo4jChatMemoryStore#getMessages(Object)}} (default: 10)
         *             If the size is 0 or negative, all messages will be retrieved
         */
        public Builder size(Long size) {
            this.size = size;
            return this;
        }
        
        /**
         * Creates an instance a {@link Driver}, starting from uri, user and password
         *
         * @param uri      the Bolt URI to a Neo4j instance
         * @param user     the Neo4j instance's username
         * @param password the Neo4j instance's password
         */
        public Builder withBasicAuth(String uri, String user, String password) {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            return this;
        }

        public Neo4jChatMemoryStore build() {
            return new Neo4jChatMemoryStore(driver, config, memoryLabel, messageLabel, lastMessageRelType, nextMessageRelType, idProperty, messageProperty, databaseName, size);
        }
    }
    
}
