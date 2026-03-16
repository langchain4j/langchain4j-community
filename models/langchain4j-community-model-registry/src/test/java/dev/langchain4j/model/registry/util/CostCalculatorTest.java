package dev.langchain4j.model.registry.util;

import dev.langchain4j.model.registry.dto.Cost;
import dev.langchain4j.model.registry.dto.ModelInfo;
import java.util.stream.Stream;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CostCalculatorTest implements WithAssertions {

    private ModelInfo modelWithCost(double input, double output) {
        Cost cost = new Cost(input, output);

        ModelInfo model = new ModelInfo();
        model.setCost(cost);

        return model;
    }

    private ModelInfo modelWithCache(double input, double output, double cacheRead, double cacheWrite) {
        Cost cost = new Cost(input, output);
        cost.setCacheRead(cacheRead);
        cost.setCacheWrite(cacheWrite);

        ModelInfo model = new ModelInfo();
        model.setCost(cost);

        return model;
    }

    @Test
    void calculate_cost_basic() {

        ModelInfo model = modelWithCost(10.0, 20.0);

        double cost = CostCalculator.calculateCost(model, 1_000_000, 1_000_000);

        assertThat(cost).isEqualTo(30.0);
    }

    @Test
    void calculate_cost_returns_zero_when_cost_missing() {

        ModelInfo model = new ModelInfo();

        double cost = CostCalculator.calculateCost(model, 1_000_000, 1_000_000);

        assertThat(cost).isEqualTo(0.0);
    }

    @Test
    void calculate_cost_with_cache() {

        ModelInfo model = modelWithCache(10.0, 20.0, 1.0, 2.0);

        double totalCost = CostCalculator.calculateCostWithCache(model, 1_000_000, 1_000_000, 500_000, 200_000);

        double expected = 10.0
                + // input
                20.0
                + // output
                (0.5 * 1.0)
                + // cache read
                (0.2 * 2.0); // cache write

        assertThat(totalCost).isEqualTo(expected);
    }

    @Test
    void calculate_monthly_cost() {

        ModelInfo model = modelWithCost(10.0, 20.0);

        double monthly = CostCalculator.calculateMonthlyCost(model, 1_000_000, 1_000_000);

        assertThat(monthly).isEqualTo(30.0 * 30);
    }

    @Test
    void compare_cost_between_models() {

        ModelInfo cheaper = modelWithCost(10.0, 10.0);
        ModelInfo expensive = modelWithCost(20.0, 20.0);

        double diff = CostCalculator.compareCost(cheaper, expensive, 1_000_000, 1_000_000);

        assertThat(diff).isLessThan(0);
    }

    @Test
    void estimate_document_processing_cost() {

        ModelInfo model = modelWithCost(10.0, 20.0);

        double cost = CostCalculator.estimateDocumentProcessingCost(model, 1_000_000, 1_000_000, 10);

        assertThat(cost).isEqualTo(30.0 * 10);
    }

    @ParameterizedTest
    @MethodSource("costPerTokenCases")
    void cost_per_1000_tokens(Cost cost, double expected) {

        double result = CostCalculator.costPer1000Tokens(cost);

        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> costPerTokenCases() {

        Cost cost = new Cost(10.0, 20.0);
        double expected = ((10.0 + 20.0) / 2.0) / 1000.0;

        return Stream.of(Arguments.of(cost, expected), Arguments.of(null, 0.0));
    }

    @ParameterizedTest
    @MethodSource("cacheSavingsCases")
    void calculate_cache_savings(ModelInfo model, boolean shouldHaveSavings) {

        double savings = CostCalculator.calculateCacheSavings(model, 1_000_000, 1_000_000, 0.5);

        if (shouldHaveSavings) {
            assertThat(savings).isGreaterThan(0);
        } else {
            assertThat(savings).isEqualTo(0.0);
        }
    }

    static Stream<Arguments> cacheSavingsCases() {

        Cost cacheSupported = new Cost(10.0, 20.0);
        cacheSupported.setCacheRead(1.0);

        ModelInfo withCache = new ModelInfo();
        withCache.setCost(cacheSupported);

        ModelInfo withoutCache = new ModelInfo();
        withoutCache.setCost(new Cost(10.0, 20.0));

        return Stream.of(Arguments.of(withCache, true), Arguments.of(withoutCache, false));
    }
}
