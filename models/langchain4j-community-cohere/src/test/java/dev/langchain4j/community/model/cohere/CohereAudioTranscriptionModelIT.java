package dev.langchain4j.community.model.cohere;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.community.model.CohereAudioTranscriptionModel;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereAudioTranscriptionModelIT {

    private final AudioTranscriptionModel audioTranscriptionModel = CohereAudioTranscriptionModel.builder()
            .apiKey(System.getenv("CO_API_KEY"))
            .modelName("cohere-transcribe-03-2026")
            .language("en")
            .logRequests(true)
            .logResponses(true)
            .build();

    static Stream<Arguments> apiSupportedFileExtensions() {
        return Stream.of(
                Arguments.of(".flac", "audio/flac"),
                Arguments.of(".mp3", "audio/mp3"),
                Arguments.of(".mpeg", "audio/mpeg"),
                Arguments.of(".mpga", "audio/mpga"),
                Arguments.of(".ogg", "audio/ogg"),
                Arguments.of(".wav", "audio/wav"));
    }

    @Test
    void should_support_transcribe_to_text() throws Exception {

        // given
        Path audioPath =
                Path.of(getClass().getClassLoader().getResource("english.mp3").toURI());

        Audio audio = Audio.builder()
                .binaryData(Files.readAllBytes(audioPath))
                .mimeType("audio/mpeg")
                .build();

        // when
        String transcription = audioTranscriptionModel.transcribeToText(audio);

        // then
        assertThat(transcription).containsIgnoringCase("Hello");
    }

    @ParameterizedTest
    @MethodSource("apiSupportedFileExtensions")
    void should_support_all_api_listed_audio_extensions(String fileExtension, String mimeType) throws Exception {

        // given
        Path audioPath = Path.of(getClass()
                .getClassLoader()
                .getResource("english" + fileExtension)
                .toURI());

        Audio audio = Audio.builder()
                .binaryData(Files.readAllBytes(audioPath))
                .mimeType(mimeType)
                .build();

        // when
        String transcription = audioTranscriptionModel.transcribeToText(audio);

        // then
        assertThat(transcription).containsIgnoringCase("Hello");
    }

    @Test
    void should_support_base_64_audio_content() throws Exception {

        // given
        Path audioPath =
                Path.of(getClass().getClassLoader().getResource("english.mp3").toURI());

        String base64AudioContent = Base64.getEncoder().encodeToString(Files.readAllBytes(audioPath));

        Audio audio = Audio.builder()
                .base64Data(base64AudioContent)
                .mimeType("audio/mp3")
                .build();

        // when
        String transcription = audioTranscriptionModel.transcribeToText(audio);

        // then
        assertThat(transcription).containsIgnoringCase("Hello");
    }

    @Test
    void should_throw_on_url_audio_content() {

        // given
        Audio audio = Audio.builder()
                .url("https://upload.wikimedia.org/wikipedia/commons/0/0a/En-us-Jabberwock.oga")
                .build();

        // when - then
        assertThatThrownBy(() -> audioTranscriptionModel.transcribeToText(audio))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_on_null_audio_transcription_requests() {

        // given - when - then
        assertThatThrownBy(() -> audioTranscriptionModel.transcribe(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
