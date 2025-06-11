package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

@Internal
class Neo4jGraphSchemaUtils {

    static final String SCHEMA_FROM_META_DATA =
            """
                    CALL apoc.meta.data({maxRels: $maxRels, sample: $sample})
                    YIELD label, other, elementType, type, property
                    WITH label, elementType,
                         apoc.text.join(collect(case when NOT type = "RELATIONSHIP" then property+": "+type else null end),", ") AS properties,
                         collect(case when type = "RELATIONSHIP" AND elementType = "node" then "(:" + label + ")-[:" + property + "]->(:" + toString(other[0]) + ")" else null end) AS patterns
                    WITH elementType AS type,
                        collect(":"+label+" {"+properties+"}") AS entities,
                        apoc.coll.flatten(collect(coalesce(patterns,[]))) AS patterns
                    RETURN collect(case type when "relationship" then entities end)[0] AS relationships,
                        collect(case type when "node" then entities end)[0] AS nodes,
                        collect(case type when "node" then patterns end)[0] as patterns
                    """;

    static Neo4jGraph.StructuredSchema getSchemaFromMetadata(Neo4jGraph graph, Long sample, Long maxRels) {
        final Record record = graph.executeRead(SCHEMA_FROM_META_DATA, Map.of("sample", sample, "maxRels", maxRels))
                .get(0);
        final List<String> nodes = record.get("nodes").asList(Value::asString, List.of());
        final List<String> relationships = record.get("relationships").asList(Value::asString, List.of());
        final List<String> patterns = record.get("patterns").asList(Value::asString, List.of());

        return new Neo4jGraph.StructuredSchema(nodes, relationships, patterns);
    }

    static String toSchemaString(Neo4jGraph.StructuredSchema structuredSchema) {

        final String nodesString = String.join(", ", structuredSchema.nodesProperties());
        final String relationshipsString = String.join(", ", structuredSchema.relationshipsProperties());
        final String patternsString = String.join(", ", structuredSchema.patterns());

        return "Node properties are the following:\n" + nodesString
                + "\n\n" + "Relationship properties are the following:\n"
                + relationshipsString
                + "\n\n" + "The relationships are the following:\n"
                + patternsString;
    }
}
