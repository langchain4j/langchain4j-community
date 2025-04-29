package dev.langchain4j.community.store.embedding.neo4j;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jFilterMapper.UNSUPPORTED_FILTER_TYPE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.neo4j.cypherdsl.core.Cypher.node;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.core.Condition;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.driver.internal.InternalPoint3D;
import org.neo4j.driver.internal.value.PointValue;

class Neo4jFilterMapperTest {

    public static final Node LABEL = node("Label").named("n");
    private static final Neo4jFilterMapper mapper = new Neo4jFilterMapper(LABEL);

    @Test
    void should_map_equal() {
        final String value = "value";
        final String expectedQuery = "MATCH (n:`Label`) WHERE n['key'] = 'value' WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_local_date() {
        final LocalDate value = LocalDate.parse("2024-03-30");
        final String expectedQuery = "MATCH (n:`Label`) WHERE n['key'] = date('2024-03-30') WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_local_time() {
        final LocalTime value = LocalTime.parse("12:50:35.556");
        final String expectedQuery = "MATCH (n:`Label`) WHERE n['key'] = localtime('12:50:35.556') WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_time() {
        final OffsetTime value = OffsetTime.parse("12:50:35.556+01:00");
        final String expectedQuery = "MATCH (n:`Label`) WHERE n['key'] = time('12:50:35.556+01:00') WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_local_date_time() {
        final LocalDateTime value = LocalDateTime.parse("2015-05-18T19:32:24.000");
        final String expectedQuery =
                "MATCH (n:`Label`) WHERE n['key'] = localdatetime('2015-05-18T19:32:24') WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_date_time() {
        final OffsetDateTime value = OffsetDateTime.parse("2007-12-03T10:15:30+01:00");
        final String expectedQuery =
                "MATCH (n:`Label`) WHERE n['key'] = datetime('2007-12-03T10:15:30+01:00') WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_point_value() {
        final PointValue value = new PointValue(new InternalPoint3D(1, 1, 1, 1));
        final String expectedQuery =
                "MATCH (n:`Label`) WHERE n['key'] = point({srid: 1, x: 1.0, y: 1.0, z: 1.0}) WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    @Test
    void should_map_equal_with_list_of_values() {
        final List<LocalDate> value = List.of(LocalDate.parse("2024-03-30"), LocalDate.parse("2025-03-30"));
        final String expectedQuery =
                "MATCH (n:`Label`) WHERE n['key'] = [date('2024-03-30'), date('2025-03-30')] WITH n RETURN 1";
        testIsEqualFilter(value, expectedQuery);
    }

    private <T> void testIsEqualFilter(T value, String expectedQuery) {
        IsEqualTo filter = new IsEqualTo("key", value);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo(expectedQuery);
    }

    @Test
    void should_map_not_equal() {
        IsNotEqualTo filter = new IsNotEqualTo("key", "value");
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['key'] <> 'value' WITH n RETURN 1");
    }

    @Test
    void should_map_is_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("key", 10);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['key'] > 10 WITH n RETURN 1");
    }

    @Test
    void should_map_is_greater_than_or_equal_to() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("key", 10);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['key'] >= 10 WITH n RETURN 1");
    }

    @Test
    void should_map_is_less_than() {
        IsLessThan filter = new IsLessThan("key", 10);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['key'] < 10 WITH n RETURN 1");
    }

    @Test
    void should_map_is_less_than_or_equal_to() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("key", 10);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['key'] <= 10 WITH n RETURN 1");
    }

    @Test
    void should_map_is_in() {
        final Set<Integer> value = Set.of(1, 2, 3);
        IsIn filter = new IsIn("key", value);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE any(x IN [1, 2, 3] WHERE x IN n['key']) WITH n RETURN 1");
    }

    @Test
    void should_map_is_not_in() {
        final Set<Integer> value = Set.of(1, 2, 3);
        IsNotIn filter = new IsNotIn("key", value);
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual)
                .isEqualTo("MATCH (n:`Label`) WHERE NOT (any(x IN [1, 2, 3] WHERE x IN n['key'])) WITH n RETURN 1");
    }

    @Test
    void should_map_and() {
        And filter = new And(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual)
                .isEqualTo("MATCH (n:`Label`) WHERE (n['key1'] = 'value1' AND n['key2'] = 'value2') WITH n RETURN 1");
    }

    @Test
    void should_map_or() {
        Or filter = new Or(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "value2"));
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual)
                .isEqualTo("MATCH (n:`Label`) WHERE (n['key1'] = 'value1' OR n['key2'] = 'value2') WITH n RETURN 1");
    }

    @Test
    void should_map_or_not_and() {
        final Set<String> valueKey3 = Set.of("1", "2");
        Or filter = new Or(
                new And(new IsEqualTo("key1", "value1"), new IsGreaterThan("key2", "value2")),
                new Not(new And(new IsIn("key3", valueKey3), new IsLessThan("key4", "value4"))));
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual)
                .isEqualTo(
                        "MATCH (n:`Label`) WHERE ((n['key1'] = 'value1' AND n['key2'] > 'value2') OR NOT ((any(x IN ['1', '2'] WHERE x IN n['key3']) AND n['key4'] < 'value4'))) WITH n RETURN 1");
    }

    @Test
    void should_correctly_sanitize_key() {
        IsEqualTo filter = new IsEqualTo("k\\ ` ey", "value");
        String actual = getCypherStatementFromFilterMapping(filter);
        assertThat(actual).isEqualTo("MATCH (n:`Label`) WHERE n['k\\\\ ` ey'] = 'value' WITH n RETURN 1");
    }

    @Test
    void should_throws_unsupported_filter_error() {
        MockFilter filter = new MockFilter();
        try {
            mapper.getCondition(filter);
            fail("Should fail due to unsupported filter");
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage()).contains(UNSUPPORTED_FILTER_TYPE_ERROR);
        }
    }

    private String getCypherStatementFromFilterMapping(Filter filter) {
        final Condition condition = mapper.getCondition(filter);
        Statement statement = Cypher.match(LABEL)
                .where(condition)
                .with(LABEL)
                .returning(Cypher.raw("1"))
                .build();
        return Renderer.getDefaultRenderer().render(statement);
    }

    private static class MockFilter implements Filter {

        @Override
        public boolean test(final Object object) {
            return false;
        }

        @Override
        public Filter and(final Filter filter) {
            return Filter.super.and(filter);
        }

        @Override
        public Filter or(final Filter filter) {
            return Filter.super.or(filter);
        }
    }
}
