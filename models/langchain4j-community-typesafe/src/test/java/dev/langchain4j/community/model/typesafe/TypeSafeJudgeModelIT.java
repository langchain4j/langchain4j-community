package dev.langchain4j.community.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.judge.JudgeRequest;
import dev.langchain4j.model.judge.NoulQuestion;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeJudgeModelIT {

    @Test
    void should_judge_with_system_one() {
        TypeSafeJudgeModel model = TypeSafeJudgeModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .build();
        JudgeRequest request = JudgeRequest.builder()
                .state(Map.of("message", "Please refund the duplicate charge"))
                .question(
                        "refund",
                        NoulQuestion.builder()
                                .instructions("Is the customer asking for a refund?")
                                .build())
                .build();

        assertThat(model.judge(request).answers().get("refund").noul()).isBetween(0.0, 1.0);
    }
}
