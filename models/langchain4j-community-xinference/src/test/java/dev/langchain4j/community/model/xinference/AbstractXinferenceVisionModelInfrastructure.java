package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.XinferenceUtils.*;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

class AbstractXinferenceVisionModelInfrastructure {
    private static final String LOCAL_IMAGE = String.format("tc-%s-%s", XINFERENCE_IMAGE, modelName());
    static XinferenceContainer container;

    static {
        if (isNullOrEmpty(XINFERENCE_BASE_URL)) {
            container = new XinferenceContainer(resolve(XINFERENCE_IMAGE, LOCAL_IMAGE))
                    .withModel(modelName());
            container.start();
            container.commitToImage(LOCAL_IMAGE);
        }
    }

    public static String baseUrl() {
        if (isNullOrEmpty(XINFERENCE_BASE_URL)) {
            return container.getEndpoint();
        } else {
            return XINFERENCE_BASE_URL;
        }
    }

    public static String apiKey() {
        return XINFERENCE_API_KEY;
    }

    public static String modelName() {
        return VISION_MODEL_NAME;
    }
}
