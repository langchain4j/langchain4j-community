package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.structureddecision.ConfidenceProvenance;
import dev.langchain4j.model.structureddecision.StructuredDecisionAnswer;
import java.util.LinkedHashMap;
import java.util.Map;

/** One djev grounded span and optional diagnostics. */
@Experimental
public record SpanAnswer(
        SpanValue value, Double confidence, ConfidenceProvenance confidenceProvenance, Map<String, Object> metadata)
        implements StructuredDecisionAnswer {
    public SpanAnswer {
        validateConfidence(confidence, confidenceProvenance);
        metadata = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    static void validateConfidence(Double confidence, ConfidenceProvenance provenance) {
        ensureTrue(
                (confidence == null) == (provenance == null),
                "confidence and confidenceProvenance must both be present or absent");
        if (confidence != null) {
            ensureTrue(Double.isFinite(confidence), "confidence must be finite");
            ensureBetween(confidence, 0, 1, "confidence");
        }
    }

    public record SpanValue(
            boolean found, String text, Integer start, Integer end, Double confidence, Map<String, Object> metadata) {
        public SpanValue {
            if (confidence != null) {
                ensureTrue(Double.isFinite(confidence), "span confidence must be finite");
                ensureBetween(confidence, 0, 1, "span confidence");
            }
            metadata = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }
}
