package dev.langchain4j.community.outputgate;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.SchemaGate;

/** Contracts shared by the tests: a survey summary whose option counts must add up. */
final class Contracts {

    static final String SCHEMA = """
            {
              "type": "object",
              "required": ["question", "total_responses", "options"],
              "properties": {
                "question": {"type": "string"},
                "total_responses": {"type": "integer", "minimum": 0},
                "options": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "required": ["label", "count"],
                    "properties": {
                      "label": {"type": "string"},
                      "count": {"type": "integer", "minimum": 0}
                    }
                  }
                }
              }
            }
            """;

    static final String VALID = """
            {"question": "Would you recommend us?", "total_responses": 120,
             "options": [{"label": "Yes", "count": 70}, {"label": "No", "count": 50}]}""";

    static final String WRONG_TOTAL = """
            {"question": "Would you recommend us?", "total_responses": 130,
             "options": [{"label": "Yes", "count": 70}, {"label": "No", "count": 50}]}""";

    private Contracts() {}

    static Contract survey() {
        return Contract.builder()
                .gate(SchemaGate.fromString(SCHEMA))
                .gate(MathGate.builder()
                        .sumEquals("options[*].count", "total_responses")
                        .build())
                .build();
    }
}
