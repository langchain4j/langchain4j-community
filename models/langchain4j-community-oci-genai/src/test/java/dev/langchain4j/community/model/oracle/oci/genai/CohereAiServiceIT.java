package dev.langchain4j.community.model.oracle.oci.genai;

import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.NON_EMPTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_COMPARTMENT_ID_PROPERTY;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION;
import static dev.langchain4j.community.model.oracle.oci.genai.TestEnvProps.OCI_GENAI_MODEL_REGION_PROPERTY;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_MODEL_REGION_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COMPARTMENT_ID_PROPERTY, matches = NON_EMPTY),
    @EnabledIfEnvironmentVariable(named = OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY, matches = NON_EMPTY)
})
public class CohereAiServiceIT extends AbstractAiServiceIT {
    static final AuthenticationDetailsProvider authProvider = TestEnvProps.createAuthProvider();

    @Override
    protected List<ChatModel> models() {
        return List.of(OciGenAiCohereChatModel.builder()
                .modelName(OCI_GENAI_COHERE_CHAT_MODEL_NAME)
                .compartmentId(OCI_GENAI_COMPARTMENT_ID)
                .authProvider(authProvider)
                .region(Region.fromRegionCodeOrId(OCI_GENAI_MODEL_REGION))
                .maxTokens(600)
                .temperature(0.7)
                .seed(TestEnvProps.SEED)
                .topP(1.0)
                .build());
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
