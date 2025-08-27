package dev.langchain4j.community.model.oracle.oci.genai;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import java.io.IOException;

final class TestEnvProps {
    static final String OCI_GENAI_MODEL_REGION_PROPERTY = "OCI_GENAI_MODEL_REGION";
    static final String OCI_GENAI_MODEL_REGION = System.getenv(OCI_GENAI_MODEL_REGION_PROPERTY);

    static final String OCI_GENAI_COMPARTMENT_ID_PROPERTY = "OCI_GENAI_COMPARTMENT_ID";
    static final String OCI_GENAI_COMPARTMENT_ID = System.getenv(OCI_GENAI_COMPARTMENT_ID_PROPERTY);

    static final String OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY = "OCI_GENAI_GENERIC_MODEL_NAME";
    static final String OCI_GENAI_GENERIC_CHAT_MODEL_NAME = System.getenv(OCI_GENAI_GENERIC_CHAT_MODEL_NAME_PROPERTY);

    static final String OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY = "OCI_GENAI_GENERIC_VISION_MODEL_NAME";
    static final String OCI_GENAI_GENERIC_VISION_MODEL_NAME =
            System.getenv(OCI_GENAI_GENERIC_VISION_MODEL_NAME_PROPERTY);

    static final String OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY = "OCI_GENAI_COHERE_MODEL_NAME";
    static final String OCI_GENAI_COHERE_CHAT_MODEL_NAME = System.getenv(OCI_GENAI_COHERE_CHAT_MODEL_NAME_PROPERTY);
    static final String OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME_PROPERTY =
            "OCI_GENAI_COHERE_MODEL_ALTERNATIVE_NAME";
    static final String OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME =
            System.getenv(OCI_GENAI_COHERE_CHAT_MODEL_ALTERNATIVE_NAME_PROPERTY);

    static final String OCI_GENAI_CONFIG_PROFILE_PROPERTY = "OCI_GENAI_CONFIG_PROFILE";

    static final String NON_EMPTY = ".+";

    static final int SEED = 555341123;

    static AuthenticationDetailsProvider createAuthProvider() {
        var configProfile = System.getenv().getOrDefault(OCI_GENAI_CONFIG_PROFILE_PROPERTY, "DEFAULT");
        try {
            return new ConfigFileAuthenticationDetailsProvider(configProfile);
        } catch (IOException e) {
            throw new RuntimeException("Error when setting up auth provider.", e);
        }
    }
}
