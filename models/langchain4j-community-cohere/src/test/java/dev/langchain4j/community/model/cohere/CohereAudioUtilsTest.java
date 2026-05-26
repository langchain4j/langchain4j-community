package dev.langchain4j.community.model.cohere;

import static dev.langchain4j.community.model.util.CohereAudioUtils.mapAudioFileName;
import static dev.langchain4j.community.model.util.CohereAudioUtils.toAudioFileExtension;
import static dev.langchain4j.community.model.util.CohereAudioUtils.validate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.model.client.transcription.CohereTranscriptionRequest;
import dev.langchain4j.data.audio.Audio;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CohereAudioUtilsTest {

    static Stream<Arguments> expectedAudioExtensionConversions() {
        return Stream.of(
                Arguments.of("audio/flac", ".flac"),
                Arguments.of("audio/mpeg", ".mp3"),
                Arguments.of("audio/mpeg3", ".mp3"),
                Arguments.of("audio/mp3", ".mp3"),
                Arguments.of("audio/mpga", ".mpga"),
                Arguments.of("audio/ogg", ".ogg"),
                Arguments.of("audio/wav", ".wav"),
                Arguments.of("audio/x-wav", ".wav"),
                Arguments.of("audio/wave", ".wav"));
    }

    static Stream<Arguments> expectedFileNames() {
        return Stream.of(
                Arguments.of("audio/flac", "audio_file.flac"),
                Arguments.of("audio/mpeg", "audio_file.mp3"),
                Arguments.of("audio/mpeg3", "audio_file.mp3"),
                Arguments.of("audio/mp3", "audio_file.mp3"),
                Arguments.of("audio/mpga", "audio_file.mpga"),
                Arguments.of("audio/ogg", "audio_file.ogg"),
                Arguments.of("audio/wav", "audio_file.wav"),
                Arguments.of("audio/x-wav", "audio_file.wav"),
                Arguments.of("audio/wave", "audio_file.wav"));
    }

    static Stream<Arguments> invalidTranscriptionRequests() {
        Audio audio = Audio.builder().base64Data("mock").mimeType("audio/mp3").build();

        return Stream.of(
                Arguments.of(CohereTranscriptionRequest.builder()
                        .model("cohere-transcribe-03-2026")
                        .language(null) // Mising language (required)
                        .audio(audio)
                        .build()),
                Arguments.of(CohereTranscriptionRequest.builder()
                        .model("cohere-transcribe-03-2026")
                        .language("   ") // Invalid language string
                        .audio(audio)
                        .build()),
                Arguments.of(CohereTranscriptionRequest.builder()
                        .model("cohere-transcribe-03-2026")
                        .language("en")
                        .temperature(-0.1) // Invalid temperature
                        .audio(audio)
                        .build()),
                Arguments.of(CohereTranscriptionRequest.builder()
                        .model("cohere-transcribe-03-2026")
                        .language("en")
                        .temperature(1.1) // Invalid temperature
                        .audio(audio)
                        .build()));
    }

    @ParameterizedTest
    @MethodSource("expectedAudioExtensionConversions")
    void should_map_from_mimetype_to_supported_audio_file_extension(String mimeType, String expectedExtension) {

        // given - when
        String result = toAudioFileExtension(mimeType);

        // then
        assertThat(result).isEqualTo(expectedExtension);
    }

    @ParameterizedTest
    @MethodSource("expectedFileNames")
    void should_generate_file_name_based_on_audio_file_mime_type(String mimeType, String expectedName) {

        // given
        Audio audio = Audio.builder().base64Data("mock").mimeType(mimeType).build();

        // when
        String result = mapAudioFileName(audio);

        // then
        assertThat(result).isEqualTo(expectedName);
    }

    @ParameterizedTest
    @MethodSource("invalidTranscriptionRequests")
    void should_throw_on_invalid_transcription_request(CohereTranscriptionRequest request) {

        assertThatThrownBy(() -> validate(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
