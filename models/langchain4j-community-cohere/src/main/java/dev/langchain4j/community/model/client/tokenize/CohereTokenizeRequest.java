package dev.langchain4j.community.model.client.tokenize;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereTokenizeRequest {

    private final String text;
    private final String model;

    private CohereTokenizeRequest(String text, String model) {
        this.text = ensureNotEmpty(text, "text");
        this.model = ensureNotBlank(model, "model");
    }

    public static CohereTokenizeRequest from(String text, String model) {
        return new CohereTokenizeRequest(text, model);
    }

    public String getText() {
        return text;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String toString() {
        return "CohereTokenizeRequest{ " + "text=" + quoted(text) + ", model=" + quoted(model) + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, model);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereTokenizeRequest that
                && Objects.equals(text, that.text)
                && Objects.equals(model, that.model);
    }
}
