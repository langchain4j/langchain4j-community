package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import java.util.LinkedHashMap;
import java.util.Map;

/** CLM-specific per-call parameters for System One. */
@Experimental
public record ClmRequestParameters(String modelName, Double temperature, Map<String, Object> additionalProperties)
        implements StructuredDecisionRequestParameters {

    public ClmRequestParameters {
        ensureTrue(temperature == null || (Double.isFinite(temperature) && temperature > 0 && temperature <= 100),
                "CLM temperature must be in (0, 100]");
        additionalProperties = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(additionalProperties));
    }

    public ClmRequestParameters(String modelName, Double temperature) {
        this(modelName, temperature, Map.of());
    }

    @Override
    public ClmRequestParameters overrideWith(StructuredDecisionRequestParameters that) {
        if (that == null) return this;
        Map<String, Object> properties = new LinkedHashMap<>(additionalProperties);
        properties.putAll(that.additionalProperties());
        return new ClmRequestParameters(that.modelName() == null ? modelName : that.modelName(),
                that instanceof ClmRequestParameters clm && clm.temperature != null ? clm.temperature : temperature,
                properties);
    }
}
