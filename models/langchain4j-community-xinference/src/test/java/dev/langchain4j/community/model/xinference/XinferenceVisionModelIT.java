package dev.langchain4j.community.model.xinference;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.Base64;

import static dev.langchain4j.community.model.xinference.XinferenceUtils.VISION_MODEL_NAME;
import static dev.langchain4j.community.model.xinference.XinferenceUtils.XINFERENCE_API_KEY;
import static dev.langchain4j.internal.Utils.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "XINFERENCE_BASE_URL", matches = ".+")
class XinferenceVisionModelIT extends AbstractXinferenceVisionModelInfrastructure {

    final String CAT_IMAGE_URL = "https://img0.baidu.com/it/u=317254799,1407991361&fm=253&fmt=auto&app=138&f=JPEG?w=889&h=500";
    final String DICE_IMAGE_URL = "https://img2.baidu.com/it/u=2780516711,2309358387&fm=253&fmt=auto&app=138&f=JPEG?w=450&h=332";

    ChatLanguageModel vlModel = XinferenceChatModel.builder()
            .baseUrl(baseUrl())
            .apiKey(apiKey())
            .modelName(modelName())
            .logRequests(true)
            .logResponses(true)
            .timeout(Duration.ofMinutes(3))
            .maxRetries(1)
            .build();

    @Test
    void should_accept_image_url() {
        // given
        ImageContent imageContent = ImageContent.from(CAT_IMAGE_URL);
        UserMessage userMessage = UserMessage.from(imageContent);
        // when
        Response<AiMessage> response = vlModel.generate(userMessage);
        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        Response<AiMessage> response = vlModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image() {

        // given
        UserMessage userMessage = UserMessage.from(TextContent.from("What do you see? Reply in one word."), ImageContent.from(CAT_IMAGE_URL));

        // when
        Response<AiMessage> response = vlModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images() {

        // given
        UserMessage userMessage = UserMessage.from(TextContent.from("What do you see? "), ImageContent.from(CAT_IMAGE_URL), ImageContent.from(DICE_IMAGE_URL));

        // when
        Response<AiMessage> response = vlModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {
        // given
        UserMessage userMessage = UserMessage.from(ImageContent.from(CAT_IMAGE_URL), ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"), TextContent.from("What do you see?"));

        // when
        Response<AiMessage> response = vlModel.generate(userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }
}
