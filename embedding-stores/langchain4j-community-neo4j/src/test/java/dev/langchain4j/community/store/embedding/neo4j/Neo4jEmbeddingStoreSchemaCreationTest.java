package dev.langchain4j.community.store.embedding.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.driver.Values.ofString;

import org.junit.jupiter.api.Test;

class Neo4jEmbeddingStoreSchemaCreationTest extends Neo4jEmbeddingStoreBaseTest {

    @Test
    void should_create_vector_index_if_not_existing() {
        Neo4jEmbeddingStore.builder()
                .label("Document0")
                .embeddingProperty("embedding")
                .indexName("vector0")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var index = session.run("SHOW VECTOR INDEX WHERE 'Document0' IN labelsOrTypes")
                .single()
                .get("name")
                .asString();
        assertThat(index).isEqualTo("vector0");
    }

    @Test
    void should_not_fail_if_vector_index_exist() {
        var createVectorIndexQuery =
                """
                    CREATE VECTOR INDEX vector1
                    FOR (n:Document1) ON n.embedding
                    OPTIONS {
                        indexConfig: {
                            `vector.dimensions`: 384
                        }
                    }
                    """;
        session.run(createVectorIndexQuery);

        Neo4jEmbeddingStore.builder()
                .label("Document1")
                .embeddingProperty("embedding")
                .indexName("vector1")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();
    }

    @Test
    void should_create_unique_index_on_id_if_not_existing() {
        Neo4jEmbeddingStore.builder()
                .label("Document2")
                .embeddingProperty("embedding")
                .indexName("vector2")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var uniqueConstraint = session.run("SHOW CONSTRAINT WHERE 'Document2' IN labelsOrTypes")
                .single();
        var properties = uniqueConstraint.get("properties").asList(ofString());
        var type = uniqueConstraint.get("type").asString();
        assertThat(properties).containsExactly("id");
        assertThat(type).isEqualTo("UNIQUENESS");
    }

    @Test
    void should_not_create_unique_index_if_already_existing() {
        var createUniqueConstraintQuery =
                """
                CREATE CONSTRAINT uidx3
                FOR (n:Document3)
                REQUIRE n.id IS UNIQUE;
                """;
        session.run(createUniqueConstraintQuery);

        Neo4jEmbeddingStore.builder()
                .label("Document3")
                .embeddingProperty("embedding")
                .indexName("vector3")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var constraintName = session.run("SHOW CONSTRAINT WHERE 'Document3' IN labelsOrTypes")
                .single()
                .get("name")
                .asString();
        assertThat(constraintName).isEqualTo("uidx3");
    }

    @Test
    void should_not_fail_if_node_key_constraint_exist() {
        var createNodeKeyConstraintQuery =
                """
                CREATE CONSTRAINT nk4
                FOR (n:Document4)
                REQUIRE n.id IS NODE KEY;
                """;
        session.run(createNodeKeyConstraintQuery);

        Neo4jEmbeddingStore.builder()
                .label("Document4")
                .embeddingProperty("embedding")
                .indexName("vector4")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var constraintName = session.run("SHOW CONSTRAINT WHERE 'Document4' IN labelsOrTypes")
                .single()
                .get("name")
                .asString();
        assertThat(constraintName).isEqualTo("nk4");
    }

    @Test
    void should_create_constraint_on_given_id_property() {
        Neo4jEmbeddingStore.builder()
                .label("Document5")
                .embeddingProperty("embedding")
                .indexName("vector5")
                .idProperty("docId")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var constraintProperties = session.run("SHOW CONSTRAINT WHERE 'Document5' IN labelsOrTypes")
                .single()
                .get("properties")
                .asList(ofString());
        assertThat(constraintProperties).containsExactly("docId");
    }

    @Test
    void should_detect_existing_constraint_if_created_backticked() {
        var createNodeKeyConstraintQuery =
                """
                CREATE CONSTRAINT nk6
                FOR (n:Document6)
                REQUIRE n.`id property` IS NODE KEY;
                """;
        session.run(createNodeKeyConstraintQuery);

        Neo4jEmbeddingStore.builder()
                .label("Document6")
                .embeddingProperty("embedding")
                .indexName("vector6")
                .idProperty("id property")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var constraintName = session.run("SHOW CONSTRAINT WHERE 'Document6' IN labelsOrTypes")
                .single()
                .get("name")
                .asString();
        assertThat(constraintName).isEqualTo("nk6");
    }

    @Test
    void should_detect_existing_vector_index_if_created_backticked() {
        var createVectorIndexQuery =
                """
                    CREATE VECTOR INDEX vector7
                    FOR (n:`Document Seven`) ON n.embedding
                    OPTIONS {
                        indexConfig: {
                            `vector.dimensions`: 384
                        }
                    }
                    """;
        session.run(createVectorIndexQuery);

        Neo4jEmbeddingStore.builder()
                .label("Document Seven")
                .embeddingProperty("embedding")
                .indexName("vector7")
                .dimension(384)
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        var existingVectorIndexes = session.run("SHOW VECTOR INDEX WHERE 'Document Seven' IN labelsOrTypes")
                .list();
        assertThat(existingVectorIndexes).hasSize(1);
    }
}
