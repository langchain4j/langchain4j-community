package dev.langchain4j.community.mcp.server;

import static dev.langchain4j.mcp.client.McpToolMetadataKeys.DESTRUCTIVE_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.IDEMPOTENT_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.OPEN_WORLD_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.READ_ONLY_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.TITLE;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.TITLE_ANNOTATION;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolSchemaMapperTest {

    @Test
    void should_map_tool_specification_to_mcp_schema() {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("createPerson")
                .description("Creates a person record")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "Person name")
                        .addIntegerProperty("age", "Person age")
                        .required("name", "age")
                        .build())
                .build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        assertThat(mapped)
                .containsEntry("name", "createPerson")
                .containsEntry("description", "Creates a person record");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) mapped.get("inputSchema");
        assertThat(inputSchema).containsEntry("type", "object");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        assertThat(properties).containsKeys("name", "age");

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) inputSchema.get("required");
        assertThat(required).containsExactlyInAnyOrder("name", "age");
    }

    @Test
    void should_map_metadata_to_title_annotations_and_meta() {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("doSomething")
                .metadata(Map.of(
                        TITLE,
                        "Do Something",
                        TITLE_ANNOTATION,
                        "Do Something (Annotation)",
                        READ_ONLY_HINT,
                        true,
                        IDEMPOTENT_HINT,
                        true,
                        OPEN_WORLD_HINT,
                        true,
                        DESTRUCTIVE_HINT,
                        false,
                        "x-custom",
                        "customValue"))
                .build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        assertThat(mapped).containsEntry("title", "Do Something");

        @SuppressWarnings("unchecked")
        Map<String, Object> annotations = (Map<String, Object>) mapped.get("annotations");
        assertThat(annotations)
                .containsEntry("title", "Do Something (Annotation)")
                .containsEntry(READ_ONLY_HINT, true)
                .containsEntry(IDEMPOTENT_HINT, true)
                .containsEntry(OPEN_WORLD_HINT, true)
                .containsEntry(DESTRUCTIVE_HINT, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) mapped.get("_meta");
        assertThat(meta).containsEntry("x-custom", "customValue");
    }

    @Test
    void should_not_add_metadata_fields_when_metadata_is_empty() {
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("noop").metadata(Map.of()).build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        assertThat(mapped).doesNotContainKeys("annotations", "_meta", "title");
    }

    @Test
    void should_not_include_description_when_description_is_null() {
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("noop").build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        assertThat(mapped).containsEntry("name", "noop").doesNotContainKey("description");
    }

    @Test
    void should_use_empty_object_schema_when_parameters_are_null() {
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("noop").parameters(null).build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) mapped.get("inputSchema");
        assertThat(inputSchema).containsEntry("type", "object");
    }

    @Test
    void should_only_add_annotations_when_metadata_contains_only_hints() {
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("noop")
                .metadata(Map.of(READ_ONLY_HINT, true))
                .build();

        Map<String, Object> mapped =
                McpToolSchemaMapper.toMcpTools(List.of(toolSpecification)).get(0);

        assertThat(mapped).containsKey("annotations").doesNotContainKeys("title", "_meta");
    }
}
