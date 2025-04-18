package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.internal.Utils.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Torch not compiled with CUDA enabled.")
class XinferenceVisionModelIT extends AbstractXinferenceVisionModelInfrastructure {

    final String CAT_IMAGE_URL =
            "https://img0.baidu.com/it/u=317254799,1407991361&fm=253&fmt=auto&app=138&f=JPEG?w=889&h=500";
    final String DICE_IMAGE_URL =
            "https://img2.baidu.com/it/u=2780516711,2309358387&fm=253&fmt=auto&app=138&f=JPEG?w=450&h=332";

    ChatModel vlModel = XinferenceChatModel.builder()
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
        ChatResponse response = vlModel.chat(userMessage);
        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_base64_image() {
        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);
        // when
        ChatResponse response = vlModel.chat(userMessage);
        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image() {
        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply in one word."), ImageContent.from(CAT_IMAGE_URL));
        // when
        ChatResponse response = vlModel.chat(userMessage);
        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images() {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? "),
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL));

        // when
        ChatResponse response = vlModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {
        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see?"));

        // when
        ChatResponse response = vlModel.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }
}
