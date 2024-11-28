package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.image.ResponseFormat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.community.model.xinference.client.utils.JsonUtil.toJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceImageModelIT extends AbstractModelInfrastructure {

    final String NEGATIVE_PROMPT = "lowres, text, error, cropped, worst quality, low quality, jpeg artifacts, ugly, duplicate, morbid, mutilated, out of frame, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, mutation, deformed, blurry, dehydrated, bad anatomy, bad proportions, extra limbs, cloned face, disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, extra legs, fused fingers, too many fingers, long neck, username, watermark, signature";
    final Map<String, Object> KW_ARGS = Map.of("num_inference_steps", 20, "guidance_scale", 7.5, "seed", System.currentTimeMillis());

    final XinferenceImageModel.XinferenceImageModelBuilder modelBuilder = XinferenceImageModel.builder()
            .modelName(IMAGE_MODEL_NAME)
            .baseUrl(XINFERENCE_BASE_URL)
            .size("256x256")
            .timeout(Duration.ofMinutes(3))
            .logRequests(true)
            .logResponses(true);

    @Test
    void simple_image_generation_works() {
        final ImageModel model = modelBuilder.responseFormat(ResponseFormat.URL).build();
        Response<Image> response = model.generate("Beautiful house on country side");
        URI remoteImage = response.content().url();
        assertThat(remoteImage).isNotNull();
    }

    @Test
    void multiple_images_generation_with_base64_works() {
        final ImageModel model = modelBuilder.responseFormat(ResponseFormat.B64_JSON).build();
        Response<List<Image>> response = model.generate("Cute red parrot sings", 2);
        assertThat(response.content()).hasSize(2);
        Image localImage1 = response.content().get(0);
        assertThat(localImage1.base64Data()).isNotNull().isBase64();
        Image localImage2 = response.content().get(1);
        assertThat(localImage2.base64Data()).isNotNull().isBase64();
    }

    @Test
    void simple_image_edit_works() {
        final ImageModel model = modelBuilder.responseFormat(ResponseFormat.B64_JSON).build();
        Image image = Image.builder()
                .base64Data(multimodalImageData("/image.jpg"))
                .build();
        final Response<Image> response = model.edit(image, "Put on black-framed glasses.");
        assertThat(response.content().base64Data()).isNotNull().isBase64();
    }

    @Test
    void simple_image_edit_with_mask_works() {
        final ImageModel model = modelBuilder.responseFormat(ResponseFormat.URL).kwargs(toJson(KW_ARGS)).negativePrompt(NEGATIVE_PROMPT).build();
        final Image sourceImage = Image.builder().url("https://pub-1fb693cb11cc46b2b2f656f51e015a2c.r2.dev/dog.png").build();
        final Image maskImage = Image.builder().url("https://pub-1fb693cb11cc46b2b2f656f51e015a2c.r2.dev/dog.png").build();
        final Response<Image> response = model.edit(sourceImage, maskImage, "Face of a yellow cat, high resolution, sitting on a park bench");
        assertThat(response.content().url()).isNotNull();
    }

    private static String multimodalImageData(String filePath) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = XinferenceImageModel.class.getResourceAsStream(filePath)) {
            assertThat(in).isNotNull();
            byte[] data = new byte[512];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
        } catch (IOException e) {
            fail("", e.getMessage());
        }

        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }
}
