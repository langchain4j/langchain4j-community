package dev.langchain4j.community.model.util;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.community.model.client.transcription.CohereTranscriptionRequest;
import dev.langchain4j.data.audio.Audio;

@Internal
public class CohereAudioUtils {

    private CohereAudioUtils() {}

    public static String mapAudioFileName(Audio audio) {
        return "audio_file" + toAudioFileExtension(audio.mimeType());
    }

    public static String toAudioFileExtension(String mimeType) {
        if (mimeType == null) {
            return "";
        }

        return switch (mimeType) {
            case "audio/flac" -> ".flac";
            case "audio/mpeg", "audio/mpeg3", "audio/mp3" -> ".mp3";
            case "audio/mpga" -> ".mpga";
            case "audio/ogg" -> ".ogg";
            case "audio/x-wav", "audio/wave", "audio/wav" -> ".wav";
            default -> "";
        };
    }

    public static byte[] toAudioByteContent(Audio audio) {
        if (audio.binaryData() != null && audio.binaryData().length != 0) {
            return audio.binaryData();
        }

        if (!isNullOrBlank(audio.base64Data())) {
            try {
                return java.util.Base64.getDecoder().decode(audio.base64Data());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base-64 data provided", e);
            }
        }

        if (audio.url() != null) {
            throw new IllegalArgumentException("Cohere Transcription API does not support URL-based audio. "
                    + "Please provide audio either in binary or base-64 encoded data");
        }

        throw new IllegalArgumentException(
                "No audio content was found. " + "Please provide audio either in binary or base-64 encoded data");
    }

    public static void validate(CohereTranscriptionRequest request) {
        if (isNullOrBlank(request.getLanguage())) {
            throw new IllegalArgumentException("Language of the audio input is required");
        }

        if (request.getTemperature() != null && (request.getTemperature() < 0 || request.getTemperature() > 1)) {
            throw new IllegalArgumentException("Temperature must be between 0 and 1");
        }
    }
}
