package dev.langchain4j.community.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.DecisionRequest;
import dev.langchain4j.model.decision.NoulQuestion;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeDecisionModelIT {

    @Test
    void should_decide_with_system_one() {
        TypeSafeDecisionModel model = TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .state(Map.of("message", "Please refund the duplicate charge"))
                .question(
                        "refund",
                        NoulQuestion.builder()
                                .instructions("Is the customer asking for a refund?")
                                .build())
                .build();

        assertThat((Double) model.decide(request).answers().get("refund").value())
                .isBetween(0.0, 1.0);
    }
}
