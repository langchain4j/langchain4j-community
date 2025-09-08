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

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_MODEL_REGION_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class GenericChatModelIT extends AbstractChatModelIT {

    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();

    @Override
    protected String customModelName() {
        return OCI_GENAI_GENERIC_VISION_MODEL_NAME;
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(OciGenAiChatModel.builder()
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
    protected ChatModel createModelWith(final ChatRequestParameters parameters) {
        return OciGenAiChatModel.builder()
                .modelName(OCI_GENAI_GENERIC_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .seed(TestEnvProps.SEED)
                .defaultRequestParameters(parameters)
                .build();
    }

    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(OciGenAiChatModel.builder()
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
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    @Disabled("Model specific behaviour")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(ChatModel model) {
        super.should_fail_if_images_as_public_URLs_are_not_supported(model);
    }

    @Override
    @Disabled("Not supported by testing model")
    protected void should_execute_multiple_tools_in_parallel_then_answer(ChatModel model) {
        super.should_execute_multiple_tools_in_parallel_then_answer(model);
    }
}
