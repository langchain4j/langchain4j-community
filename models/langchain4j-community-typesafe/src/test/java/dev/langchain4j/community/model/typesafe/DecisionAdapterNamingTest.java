package dev.langchain4j.community.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.systemone.SystemOneDecisionModel;
import dev.langchain4j.model.decision.DecisionModel;
import org.junit.jupiter.api.Test;

class DecisionAdapterNamingTest {

    @Test
    void consumer_can_use_decision_adapters() {
        DecisionModel systemOne =
                SystemOneDecisionModel.builder().baseUrl("http://localhost").build();
        DecisionModel typeSafe = TypeSafeDecisionModel.builder()
                .baseUrl("http://localhost")
                .apiKey("test")
                .build();

        assertThat(systemOne).isInstanceOf(SystemOneDecisionModel.class);
        assertThat(typeSafe).isInstanceOf(TypeSafeDecisionModel.class);
    }
}
