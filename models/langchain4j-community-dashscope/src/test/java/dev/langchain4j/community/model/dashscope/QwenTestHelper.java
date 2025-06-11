package dev.langchain4j.community.model.dashscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.video.Video;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class QwenTestHelper {

    public static Stream<Arguments> languageModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_MAX),
                Arguments.of(QwenModelName.QWEN_LONG));
    }

    public static Stream<Arguments> nonMultimodalChatModelNameProvider() {
        return Stream.of(
                Arguments.of(QwenModelName.QWEN_TURBO),
                Arguments.of(QwenModelName.QWEN_PLUS),
                Arguments.of(QwenModelName.QWEN_MAX),
                Arguments.of(QwenModelName.QWEN_LONG),
                Arguments.of(QwenModelName.QWEN3_32B));
    }

    public static Stream<Arguments> reasoningChatModelNameProvider() {
        // Only streaming output is supported.
        return Stream.of(Arguments.of(QwenModelName.QWEN3_32B), Arguments.of(QwenModelName.QWEN3_235B_A22B));
    }

    public static Stream<Arguments> functionCallChatModelNameProvider() {
        return Stream.of(Arguments.of(QwenModelName.QWEN_MAX));
    }

    public static Stream<Arguments> vlChatModelNameProvider() {
        return Stream.of(Arguments.of(QwenModelName.QWEN_VL_MAX));
    }

    public static Stream<Arguments> mtChatModelNameProvider() {
        return Stream.of(Arguments.of(QwenModelName.QWEN_MT_TURBO), Arguments.of(QwenModelName.QWEN_MT_PLUS));
    }

    public static Stream<Arguments> audioChatModelNameProvider() {
        return Stream.of(Arguments.of(QwenModelName.QWEN_AUDIO_TURBO_LATEST));
    }

    public static Stream<Arguments> embeddingModelNameProvider() {
        return Stream.of(Arguments.of(QwenModelName.TEXT_EMBEDDING_V3));
    }

    public static String apiKey() {
        return System.getenv("DASHSCOPE_API_KEY");
    }

    public static List<ChatMessage> chatMessages() {
        List<ChatMessage> messages = new LinkedList<>();
        messages.add(SystemMessage.from("Your name is Jack." + " You like to answer other people's questions briefly."
                + " It's rainy today."
                + " Your reply should end with \"That's all!\"."));
        messages.add(UserMessage.from("Hello. What's your name?"));
        messages.add(AiMessage.from("Jack. That's all!"));
        messages.add(UserMessage.from("How about the weather today?"));
        return messages;
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageUrl() {
        Image image = Image.builder()
                .url("https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg")
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture?");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static List<ChatMessage> multimodalChatMessagesWithImageData() {
        Image image = Image.builder()
                .base64Data(multimodalImageData())
                .mimeType("image/jpeg")
                .build();
        ImageContent imageContent = ImageContent.from(image);
        TextContent textContent = TextContent.from("What animal is in the picture?");
        return Collections.singletonList(UserMessage.from(imageContent, textContent));
    }

    public static String multimodalImageData() {
        return getBase64DataFromResource("/parrot.jpg");
    }

    public static List<ChatMessage> multimodalChatMessagesWithAudioUrl() {
        Audio audio = Audio.builder()
                .url("https://dashscope.oss-cn-beijing.aliyuncs.com/audios/welcome.mp3")
                .build();
        AudioContent audioContent = AudioContent.from(audio);
        TextContent textContent =
                TextContent.from("What is this audio saying? Please note that the audio language is Chinese.");
        return Collections.singletonList(UserMessage.from(audioContent, textContent));
    }

    public static List<ChatMessage> multimodalChatMessagesWithAudioData() {
        Audio audio = Audio.builder()
                .base64Data(multimodalAudioData())
                .mimeType("audio/mp3")
                .build();
        AudioContent audioContent = AudioContent.from(audio);
        TextContent textContent =
                TextContent.from("What is this audio saying? Please note that the audio language is Chinese.");
        return Collections.singletonList(UserMessage.from(audioContent, textContent));
    }

    public static String multimodalAudioData() {
        return getBase64DataFromResource("/welcome.mp3");
    }

    public static List<ChatMessage> multimodalChatMessagesWithVideoUrl() {
        Video video = Video.builder()
                .url("https://cdn.wanx.aliyuncs.com/upload/commons/parrot.mp4")
                .build();
        VideoContent videoContent = VideoContent.from(video);
        TextContent textContent = TextContent.from("What animal is in the video?");
        return Collections.singletonList(UserMessage.from(videoContent, textContent));
    }

    public static List<ChatMessage> multimodalChatMessagesWithVideoData() {
        Video video = Video.builder()
                .base64Data(multimodalVideoData())
                .mimeType("video/mp4")
                .build();
        VideoContent videoContent = VideoContent.from(video);
        TextContent textContent = TextContent.from("What animal is in the video?");
        return Collections.singletonList(UserMessage.from(videoContent, textContent));
    }

    public static String multimodalVideoData() {
        return getBase64DataFromResource("/parrot.mp4");
    }

    private static String getBase64DataFromResource(String path) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = QwenTestHelper.class.getResourceAsStream(path)) {
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
