package dev.langchain4j.community.model.xinference;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class XinferenceUtils {
    public static final String XINFERENCE_BASE_URL = System.getenv("XINFERENCE_BASE_URL");
    public static final String XINFERENCE_API_KEY = System.getenv("XINFERENCE_BASE_URL");

    public static final String XINFERENCE_IMAGE = "xprobe/xinference:latest";

    public static final String LLM_MODEL_NAME = "qwen2.5-instruct";
    public static final String VISION_MODEL_NAME = "qwen2-vl-instruct";
    public static final String EMBEDDING_MODEL_NAME = "bge-m3";
    public static final String IMAGE_MODEL_NAME = "sd3-medium";
    public static final String RERANK_MODEL_NAME = "bge-reranker-v2-m3";

    private static final Map<String, String> MODEL_LAUNCH_MAP = new HashMap<>() {
        {
            put(LLM_MODEL_NAME, "xinference launch --model-engine vLLM --model-name qwen2.5-instruct --size-in-billions 0_5 --model-format pytorch --quantization none");
            put(VISION_MODEL_NAME, "xinference launch --model-engine Transformers --model-name qwen2-vl-instruct --size-in-billions 2 --model-format pytorch --quantization none");
            put(EMBEDDING_MODEL_NAME, "xinference launch --model-name bge-m3 --model-type embedding");
            put(IMAGE_MODEL_NAME, "xinference launch --model-name sd3-medium --model-type image");
            put(RERANK_MODEL_NAME, "xinference launch --model-name bge-reranker-v2-m3 --model-type rerank");
        }
    };

    public static DockerImageName resolve(String baseImage, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(baseImage);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(localImageName).asCompatibleSubstituteFor(baseImage);
    }

    public static String launchCmd(String modelName) {
        return MODEL_LAUNCH_MAP.get(modelName);
    }
}
