package dev.langchain4j.community.store.embedding.yugabytedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for YugabyteDBMetadataFilterMapper
 */
class YugabyteDBMetadataFilterMapperTest {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBMetadataFilterMapperTest.class);

    private YugabyteDBMetadataFilterMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new YugabyteDBMetadataFilterMapper();
    }

    @Test
    void should_map_is_equal_to_filter() {
        logger.info("Testing IsEqualTo filter mapping for genre='comedy'");

        // given
        IsEqualTo filter = new IsEqualTo("genre", "comedy");
        logger.debug("Created filter: {}", filter);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);
        logger.info("Generated SQL: {}", result.getSqlClause());
        logger.info("Parameters: {}", result.getParameters());

        // then
        assertThat(result.getSqlClause()).isEqualTo("metadata->>'genre' IS NOT NULL AND metadata->>'genre' = ?");
        assertThat(result.getParameters()).containsExactly("comedy");
        logger.info("Test passed successfully!");
    }

    @Test
    void should_map_is_not_equal_to_filter() {
        // given
        IsNotEqualTo filter = new IsNotEqualTo("genre", "drama");

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("metadata->>'genre' IS NULL OR metadata->>'genre' != ?");
        assertThat(result.getParameters()).containsExactly("drama");
    }

    @Test
    void should_map_is_greater_than_filter_with_number() {
        // given
        IsGreaterThan filter = new IsGreaterThan("year", 2000);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("(metadata->>'year')::numeric > ?");
        assertThat(result.getParameters()).containsExactly(2000);
    }

    @Test
    void should_map_is_greater_than_filter_with_string() {
        // given
        IsGreaterThan filter = new IsGreaterThan("title", "A");

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("metadata->>'title' > ?");
        assertThat(result.getParameters()).containsExactly("A");
    }

    @Test
    void should_map_is_less_than_filter_with_number() {
        // given
        IsLessThan filter = new IsLessThan("rating", 5.0);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("(metadata->>'rating')::numeric < ?");
        assertThat(result.getParameters()).containsExactly(5.0);
    }

    @Test
    void should_map_is_in_filter() {
        // given
        IsIn filter = new IsIn("genre", Arrays.asList("comedy", "action", "drama"));

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("metadata->>'genre' IN (?,?,?)");
        // The order should match the order in the ArrayList constructor
        assertThat(result.getParameters()).hasSize(3);
        assertThat(result.getParameters()).contains("comedy", "action", "drama");
    }

    @Test
    void should_map_and_filter() {
        // given
        IsEqualTo left = new IsEqualTo("genre", "comedy");
        IsGreaterThan right = new IsGreaterThan("year", 2000);
        And filter = new And(left, right);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause())
                .isEqualTo(
                        "(metadata->>'genre' IS NOT NULL AND metadata->>'genre' = ? AND (metadata->>'year')::numeric > ?)");
        assertThat(result.getParameters()).containsExactly("comedy", 2000);
    }

    @Test
    void should_map_or_filter() {
        // given
        IsEqualTo left = new IsEqualTo("genre", "comedy");
        IsEqualTo right = new IsEqualTo("genre", "action");
        Or filter = new Or(left, right);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause())
                .isEqualTo(
                        "(metadata->>'genre' IS NOT NULL AND metadata->>'genre' = ? OR metadata->>'genre' IS NOT NULL AND metadata->>'genre' = ?)");
        assertThat(result.getParameters()).containsExactly("comedy", "action");
    }

    @Test
    void should_map_not_filter() {
        // given
        IsEqualTo equalTo = new IsEqualTo("genre", "horror");
        Not filter = new Not(equalTo);

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("NOT (metadata->>'genre' IS NOT NULL AND metadata->>'genre' = ?)");
        assertThat(result.getParameters()).containsExactly("horror");
    }

    @Test
    void should_handle_null_filter() {
        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(null);

        // then
        assertThat(result.getSqlClause()).isEmpty();
        assertThat(result.getParameters()).isEmpty();
    }

    @Test
    void should_escape_single_quotes() {
        // given
        IsEqualTo filter = new IsEqualTo("title", "It's a Wonderful Life");

        // when
        YugabyteDBMetadataFilterMapper.FilterResult result = mapper.map(filter);

        // then
        assertThat(result.getSqlClause()).isEqualTo("metadata->>'title' IS NOT NULL AND metadata->>'title' = ?");
        assertThat(result.getParameters()).containsExactly("It's a Wonderful Life");
    }
}
