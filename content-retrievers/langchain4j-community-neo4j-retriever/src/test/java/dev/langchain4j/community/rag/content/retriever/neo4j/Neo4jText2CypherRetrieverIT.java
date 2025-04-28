package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.Session;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class Neo4jText2CypherRetrieverIT extends Neo4jText2CypherRetrieverBaseTest {

    private static final ChatModel OPEN_AI_CHAT_MODEL = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsed() {

        // With
        Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(OPEN_AI_CHAT_MODEL)
                .build();

        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");

        // When
        List<Content> contents = neo4jContentRetriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsedWithExamples() {
        try (Session session = driver.session()) {
            createConstraints(session);
            loadCSVData(session);
        }

        // With
        ChatModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        List<String> examples = List.of(
                """
            # Which streamer has the most followers?
            MATCH (s:Stream)
            RETURN s.name AS streamer
            ORDER BY s.followers DESC LIMIT 1
            """,
                """
            # How many streamers are from Norway?
            MATCH (s:Stream)-[:HAS_LANGUAGE]->(:Language {{name: 'Norwegian'}})
            RETURN count(s) AS streamers
            Note: Do not include any explanations or apologies in your responses.
            Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
            Do not include any text except the generated Cypher statement.
            """);
        final String textQuery = "Which streamer from Italy has the most followers?";
        Query query = new Query(textQuery);

        Neo4jText2CypherRetriever neo4jContentRetrieverWithoutExample = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(openAiChatModel)
                .build();
        List<Content> contentsWithoutExample = neo4jContentRetrieverWithoutExample.retrieve(query);
        assertThat(contentsWithoutExample).isEmpty();

        // When
        Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
                .graph(graph)
                .chatModel(OPEN_AI_CHAT_MODEL)
                .examples(examples)
                .build();

        // Then
        // retry mechanism since the result is not deterministic
        final String text = RetryUtils.withRetry(
                () -> {
                    List<Content> contents = neo4jContentRetriever.retrieve(query);
                    assertThat(contents).hasSize(1);
                    return contents.get(0).textSegment().text();
                },
                5);

        // check validity of the response
        final String name = driver.session()
                .run(
                        "MATCH (s:Stream)-[:HAS_LANGUAGE]->(l:Language {name: 'Italian'}) RETURN s.name ORDER BY s.followers DESC LIMIT 1")
                .single()
                .values()
                .get(0)
                .toString();
        assertThat(text).isEqualTo(name);
    }

    private static void createConstraints(Session session) {
        session.executeWrite(tx -> {
            tx.run("CREATE CONSTRAINT FOR (c:Customer) REQUIRE c.customerID IS UNIQUE");
            tx.run("CREATE CONSTRAINT FOR (e:Employee) REQUIRE e.employeeID IS UNIQUE");
            tx.run("CREATE CONSTRAINT FOR (p:Product) REQUIRE p.productID IS UNIQUE");
            tx.run("CREATE CONSTRAINT FOR (o:Order) REQUIRE o.orderID IS UNIQUE");
            tx.run("CREATE CONSTRAINT FOR (s:Supplier) REQUIRE s.supplierID IS UNIQUE");
            tx.run("CREATE CONSTRAINT FOR (sh:Shipper) REQUIRE sh.shipperID IS UNIQUE");
            return null;
        });
    }

    private static void loadCSVData(Session session) {
        String csvUrl = "https://data.neo4j.com/northwind/";

        session.executeWrite(tx -> {
            // Load Customers
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'customers.csv' AS row
                        CREATE (:Customer {
                            customerID: row.customerID,
                            companyName: row.companyName,
                            contactName: row.contactName,
                            contactTitle: row.contactTitle,
                            address: row.address,
                            city: row.city,
                            region: row.region,
                            postalCode: row.postalCode,
                            country: row.country,
                            phone: row.phone
                        })
                    """,
                    Map.of("path", csvUrl));

            // Load Employees
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'employees.csv' AS row
                        CREATE (:Employee {
                            employeeID: toInteger(row.employeeID),
                            lastName: row.lastName,
                            firstName: row.firstName,
                            title: row.title,
                            city: row.city,
                            country: row.country
                        })
                    """,
                    Map.of("path", csvUrl));

            // Load Products
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'products.csv' AS row
                        CREATE (:Product {
                            productID: toInteger(row.productID),
                            productName: row.productName,
                            unitPrice: toFloat(row.unitPrice),
                            quantityPerUnit: row.quantityPerUnit,
                            unitsInStock: toInteger(row.unitsInStock)
                        })
                    """,
                    Map.of("path", csvUrl));

            // Load Suppliers
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'suppliers.csv' AS row
                        CREATE (:Supplier {
                            supplierID: toInteger(row.supplierID),
                            companyName: row.companyName,
                            contactName: row.contactName,
                            city: row.city,
                            country: row.country
                        })
                    """,
                    Map.of("path", csvUrl));

            // Load Orders
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'orders.csv' AS row
                        CREATE (:Order {
                            orderID: toInteger(row.orderID),
                            orderDate: row.orderDate,
                            shippedDate: row.shippedDate
                        })
                    """,
                    Map.of("path", csvUrl));

            return null;
        });

        session.executeWrite(tx -> {
            // Create Relationships: Customers -> Orders
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'orders.csv' AS row
                        MATCH (c:Customer {customerID: row.customerID})
                        MATCH (o:Order {orderID: toInteger(row.orderID)})
                        CREATE (c)-[:PLACED]->(o)
                    """,
                    Map.of("path", csvUrl));

            // Create Relationships: Orders -> Products
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'order-details.csv' AS row
                        MATCH (o:Order {orderID: toInteger(row.orderID)})
                        MATCH (p:Product {productID: toInteger(row.productID)})
                        CREATE (o)-[:CONTAINS {quantity: toInteger(row.quantity), unitPrice: toFloat(row.unitPrice)}]->(p)
                    """,
                    Map.of("path", csvUrl));

            // Create Relationships: Products -> Suppliers
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'products.csv' AS row
                        MATCH (p:Product {productID: toInteger(row.productID)})
                        MATCH (s:Supplier {supplierID: toInteger(row.supplierID)})
                        CREATE (s)-[:SUPPLIES]->(p)
                    """,
                    Map.of("path", csvUrl));

            // Create Relationships: Employees -> Orders (Processed Orders)
            tx.run(
                    """
                        LOAD CSV WITH HEADERS FROM $path + 'orders.csv' AS row
                        MATCH (e:Employee {employeeID: toInteger(row.employeeID)})
                        MATCH (o:Order {orderID: toInteger(row.orderID)})
                        CREATE (e)-[:PROCESSED]->(o)
                    """,
                    Map.of("path", csvUrl));

            return null;
        });
    }
}
