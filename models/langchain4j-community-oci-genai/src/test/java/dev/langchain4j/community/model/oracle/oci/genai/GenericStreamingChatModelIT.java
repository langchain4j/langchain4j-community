package dev.langchain4j.community.model.oracle.oci.genai;

import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_CHAT_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_VISION_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION_PROPERTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_MODEL_REGION_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class GenericStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();

    @Override
    public StreamingChatModel createModelWith(final ChatModelListener listener) {
        return OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(1)).onPartialToolCall(any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\": \"Munich\"}"));
    }

    @Override
    protected String customModelName() {
        return OCI_GENAI_GENERIC_VISION_MODEL_NAME;
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.7)
                .topP(1.0)
                .build());
    }

    @Override
    protected StreamingChatModel createModelWith(final ChatRequestParameters parameters) {
        return OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .defaultRequestParameters(parameters)
                .build();
    }

    protected List<StreamingChatModel> modelsSupportingImageInputs() {
        return List.of(OciGenAiStreamingChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_VISION_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .maxTokens(600)
                .temperature(0.7)
                .topP(1.0)
                .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(final int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // Only url encoded base64 is supported
        return false;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        // Only url encoded base64 is supported
        return false;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        // At most 1 image(s) may be provided in one request
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false;
    }

    @Override
    protected boolean assertToolId(StreamingChatModel model) {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    protected boolean assertThreads() {
        return false;
    }

    @Override
    protected boolean assertTokenUsage() {
        return false;
    }

    @Override
    @Disabled("Enable when token usage is supported by SDK")
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {
        super.should_respect_maxOutputTokens_in_default_model_parameters();
    }

    @Override
    @Disabled("Enable when token usage is supported by SDK")
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {
        super.should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters();
    }

    @Override
    @Disabled("Enable when token usage is supported by SDK")
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        super.should_respect_maxOutputTokens_in_chat_request(model);
    }

    @Override
    @Disabled("Enable when token usage is supported by SDK")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {
        super.should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(model);
    }

    @Override
    @Disabled("Model specific behaviour")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatModel model) {
        super.should_fail_if_images_as_public_URLs_are_not_supported(model);
    }

    @Override
    @Disabled("Not supported by testing model")
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {
        super.should_execute_multiple_tools_in_parallel_then_answer(model);
    }
}
