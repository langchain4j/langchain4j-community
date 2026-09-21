package dev.langchain4j.community.model.typesafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

record SystemOneRequest(Map<String, Object> state, String model, Map<String, SystemOneQuestion> questions) {}

record SystemOneQuestion(String type, String instructions, Object criteria) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record SystemOneResponse(String model, Map<String, SystemOneAnswer> answers, SystemOneUsage usage) {}

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
