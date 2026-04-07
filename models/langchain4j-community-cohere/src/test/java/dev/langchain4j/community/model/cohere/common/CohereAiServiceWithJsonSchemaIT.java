package dev.langchain4j.community.model.cohere.common;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(CohereChatModel.builder()
                .apiKey(System.getenv("CO_API_KEY"))
                .modelName("command-a-03-2025")
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    @Override
    public boolean supportsRecursion() { return true; }

    @Disabled("Cohere does not support this schema.")
    @Override
    protected void should_extract_pojo_with_missing_data(ChatModel chatModel) {
        // Cohere JSON Schemas required at least one field as required on object schemas.
        // The Map<String, Object> results in an empty object schema, which is illegal in the API.
    }

    @Disabled("Cohere models are not good with float numbers")
    @Override
    protected void should_extract_float_boxed(ChatModel chatModel) {
        // Cohere models are not good with decimal numbers, this test is very flaky.
    }

    @Disabled("Cohere models are not good with float numbers")
    @Override
    protected void should_extract_double_boxed(ChatModel chatModel) {
        // Cohere models are not good with decimal numbers, this test is very flaky.
    }

    @Disabled("Cohere models are not good with float numbers")
    @Override
    protected void should_extract_double_primitive(ChatModel chatModel) {
        // Cohere models are not good with decimal numbers, this test is very flaky.
    }

    @Disabled("Cohere models are not good with float numbers")
    @Override
    protected void should_extract_float_primitive(ChatModel chatModel) {
        // Cohere models are not good with decimal numbers, this test is very flaky.
    }
}
