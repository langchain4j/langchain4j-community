package dev.langchain4j.community.model.typesafe;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

record SystemOneRequest(Object state, String model, Map<String, SystemOneQuestion> questions) {}

record SystemOneQuestion(String type, String instructions, Object criteria) {}

final class SystemOneResponse {

    @JsonProperty("model")
    private String model;

    @JsonProperty("answers")
    private Map<String, SystemOneAnswer> answers;

    @JsonProperty("usage")
    private SystemOneUsage usage;

    private final Map<String, Object> metadata = new LinkedHashMap<>();

    @JsonAnySetter
    void metadata(String name, Object value) {
        metadata.put(name, value);
    }

    Map<String, SystemOneAnswer> answers() {
        return answers;
    }

    Map<String, Object> metadata() {
        return metadata;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record SystemOneUsage(int input_tokens, int output_tokens) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record SystemOneAnswer(
        String type,
        Double noul,
        String choice,
        Double score,
        Double confidence,
        Map<String, Double> probabilities,
        Map<String, Object> legend) {}
