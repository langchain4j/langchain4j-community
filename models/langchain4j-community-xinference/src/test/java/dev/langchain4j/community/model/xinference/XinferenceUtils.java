package dev.langchain4j.community.model.xinference;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

public class XinferenceUtils {
    public static final String XINFERENCE_BASE_URL = System.getenv("XINFERENCE_BASE_URL");
    public static final String XINFERENCE_API_KEY = System.getenv("XINFERENCE_BASE_URL");
    // CPU
    public static final String XINFERENCE_IMAGE = "xprobe/xinference:latest-cpu";
    // GPU
    //    public static final String XINFERENCE_IMAGE = "xprobe/xinference:latest";

    public static final String CHAT_MODEL_NAME = "qwen2.5-instruct";
    public static final String GENERATE_MODEL_NAME = "qwen2.5";
    public static final String VISION_MODEL_NAME = "qwen2-vl-instruct";
    public static final String IMAGE_MODEL_NAME = "sd3-medium";
    public static final String EMBEDDING_MODEL_NAME = "text2vec-base-chinese";
    public static final String RERANK_MODEL_NAME = "bge-reranker-base";

    private static final Map<String, String> MODEL_LAUNCH_MAP = new HashMap<>() {
        {
            put(
                    CHAT_MODEL_NAME,
                    String.format(
                            "xinference launch --model-engine Transformers --model-name %s --size-in-billions 0_5 --model-format pytorch --quantization none",
                            CHAT_MODEL_NAME));
            put(
                    GENERATE_MODEL_NAME,
                    String.format(
                            "xinference launch --model-engine Transformers --model-name %s --size-in-billions 0_5 --model-format pytorch --quantization none",
                            GENERATE_MODEL_NAME));
            put(
                    VISION_MODEL_NAME,
                    String.format(
                            "xinference launch --model-engine Transformers --model-name %s --size-in-billions 2 --model-format pytorch --quantization none",
                            VISION_MODEL_NAME));
            put(
                    RERANK_MODEL_NAME,
                    String.format("xinference launch --model-name %s --model-type rerank", RERANK_MODEL_NAME));
            put(
                    IMAGE_MODEL_NAME,
                    String.format("xinference launch --model-name %s --model-type image", IMAGE_MODEL_NAME));
            put(
                    EMBEDDING_MODEL_NAME,
                    String.format("xinference launch --model-name %s --model-type embedding", EMBEDDING_MODEL_NAME));
        }
    };

    public static DockerImageName resolve(String baseImage, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(baseImage);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images =
                dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(localImageName).asCompatibleSubstituteFor(baseImage);
    }

    public static String launchCmd(String modelName) {
        return MODEL_LAUNCH_MAP.get(modelName);
    }
}
