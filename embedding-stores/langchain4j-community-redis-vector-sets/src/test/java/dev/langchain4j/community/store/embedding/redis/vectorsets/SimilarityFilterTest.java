package dev.langchain4j.community.store.embedding.redis.vectorsets;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimilarityFilterTest {
    private final SimilarityFilterMapper instance;

    private final IsEqualTo age30;
    private final IsEqualTo isJacopo;

    SimilarityFilterTest() {
        instance = new SimilarityFilterMapper();

        age30 = new IsEqualTo("age", 30);
        isJacopo = new IsEqualTo("name", "jacopo");
    }


    @Test
    void basicScenario() {
        assertEquals(Optional.empty(), instance.from(null));

        Filter customFilter = o -> false;
        assertEquals(Optional.empty(), instance.from(customFilter));

        assertEquals(Optional.of(".age==30"), instance.from(age30));
        assertEquals(Optional.of(".name==\"jacopo\""), instance.from(isJacopo));
        assertEquals(Optional.of(".height==187.5"), instance.from(new IsEqualTo("height", 187.5)));

        assertEquals(Optional.of(".name!=\"jacopo\""), instance.from(new IsNotEqualTo("name", "jacopo")));


        assertEquals(Optional.of(".age<30"), instance.from(new IsLessThan("age", 30)));
        assertEquals(Optional.of(".age<=30"), instance.from(new IsLessThanOrEqualTo("age", 30)));

        assertEquals(Optional.of(".age>30"), instance.from(new IsGreaterThan("age", 30)));
        assertEquals(Optional.of(".age>=30"), instance.from(new IsGreaterThanOrEqualTo("age", 30)));

        assertEquals(Optional.of(".age in [0]"), instance.from(new IsIn("age", List.of(0, 0, 0))));
        assertEquals(Optional.of(".name not (in [\"jacopo\"])"), instance.from(new IsNotIn("name", List.of("jacopo", "jacopo", "jacopo"))));
    }


    @Test
    void basicLogicalOperators() {
        assertEquals(Optional.of("(.age==30 and .name==\"jacopo\")"), instance.from(Filter.and(age30, isJacopo)));
        assertEquals(Optional.of("(.age==30 or .name==\"jacopo\")"), instance.from(Filter.or(age30, isJacopo)));

        assertEquals(Optional.of("not ((.age==30 or .name==\"jacopo\"))"), instance.from(Filter.not(Filter.or(age30, isJacopo))));
    }

    @Test
    void complexLogicalOperators() {
        var jacopo = Filter.and(isJacopo, age30);
        var bob = Filter.and(new IsEqualTo("name", "bob"), new IsNotEqualTo("age", 30));

        var jacopoOrBob = Filter.or(jacopo, bob);
        var notAge30 = Filter.not(age30);

        assertEquals(
                Optional.of("(((.name==\"jacopo\" and .age==30) or (.name==\"bob\" and .age!=30)) and not (.age==30))"),
                instance.from(Filter.and(jacopoOrBob, notAge30)));
    }
}
