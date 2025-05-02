package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.image.ImageModelName.COGVIEW_3;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiImageModelIT {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAiImageModelIT.class);
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    private final ZhipuAiImageModel model = ZhipuAiImageModel.builder()
            .model(COGVIEW_3)
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void simple_image_generation_works() {
        Response<Image> response = model.generate("Beautiful house on country side");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }
}
