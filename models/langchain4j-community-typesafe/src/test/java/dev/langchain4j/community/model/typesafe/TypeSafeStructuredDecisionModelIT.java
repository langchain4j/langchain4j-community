package dev.langchain4j.community.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeStructuredDecisionModelIT {

    @Test
    void should_decide_with_system_one() {
        TypeSafeStructuredDecisionModel model = TypeSafeStructuredDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .build();
        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state(Map.of("message", "Please refund the duplicate charge"))
                .question(
                        "refund",
                        NoulQuestion.builder()
                                .instructions("Is the customer asking for a refund?")
                                .build())
                .build();

        assertThat((Double) model.decide(request).answers().get("refund").value()).isBetween(0.0, 1.0);
    }
}
