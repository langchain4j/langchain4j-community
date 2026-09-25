package dev.langchain4j.community.model.systemone;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.structureddecision.ConfidenceProvenance;
import dev.langchain4j.model.structureddecision.StructuredDecisionAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** djev's list of grounded spans and optional diagnostics. */
@Experimental
public record SpansAnswer(SpansValue value, Double confidence, ConfidenceProvenance confidenceProvenance,
                          Map<String, Object> metadata) implements StructuredDecisionAnswer {
    public SpansAnswer {
        SpanAnswer.validateConfidence(confidence, confidenceProvenance);
        metadata = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public record SpansValue(boolean found, List<SpanAnswer.SpanValue> items) {
        public SpansValue {
            items = List.copyOf(items);
        }
    }
}
