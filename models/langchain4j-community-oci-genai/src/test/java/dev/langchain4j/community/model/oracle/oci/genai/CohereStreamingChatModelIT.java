package dev.langchain4j.community.model.oracle.oci.genai;

import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME_PROPERTY, matches = NON_EMPTY)
})
public class CohereStreamingChatModelIT extends AbstractStreamingChatModelIT {
    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();

    @Override
    public StreamingChatModel createModelWith(final ChatModelListener listener) {
        return OciGenAiCohereStreamingChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .seed(TestEnvProps.SEED)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected String customModelName() {
        return OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME;
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(OciGenAiCohereStreamingChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.7)
                .topP(1.0)
                .build());
    }

    @Override
    protected StreamingChatModel createModelWith(final ChatRequestParameters parameters) {
        return OciGenAiCohereStreamingChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .seed(TestEnvProps.SEED)
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(final int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Disabled("Know issue: response_format is not supported with RAG")
    @Override
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(StreamingChatModel m) {
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(m);
    }

    @Disabled("GenAi Cohere doesn't return any metadata when max-output token is set to 1")
    @Override
    protected void should_respect_modelName_in_default_model_parameters() {
        super.should_respect_modelName_in_default_model_parameters();
    }

    @Disabled("GenAi Cohere doesn't return any metadata when max-output token is set to 1")
    @Override
    protected void should_respect_modelName_in_chat_request(StreamingChatModel m) {
        super.should_respect_modelName_in_chat_request(m);
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        // Cohere chat models in OCI don't support image content
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // Cohere chat models in OCI don't support image content
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false;
    }

    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertThreads() {
        return false;
    }
}
