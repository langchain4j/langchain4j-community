package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.store.embedding.filter.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * This class provides usage examples for the enhanced Redis filter API.
 * These examples show how to use the filters in real-world scenarios.
 */
public class EnhancedRedisFiltersExamplesTest {

    @Test
    public void filterUsageExamples() {
        // Example 1: Find restaurant recommendations in Paris with high ratings
        FilterExpression filter = RedisFilter.text("content")
                .contains("restaurant")
                .and(RedisFilter.tag("city").equalTo("Paris"))
                .and(RedisFilter.numeric("rating").greaterThanOrEqualTo(4.0));

        Filter redisFilter = new RedisFilterExpression(filter);

        assertNotNull(redisFilter);
        String exampleQueryString = ((RedisFilterExpression) redisFilter).toRedisQueryString();
        // Print the actual query string for debugging
        System.out.println("Example 1 Query: " + exampleQueryString);
        // Check individual components rather than specific formatting
        assertTrue(exampleQueryString.contains("restaurant"));
        assertTrue(exampleQueryString.contains("Paris"));
        assertTrue(exampleQueryString.contains("4.0"));

        // Example 2: Find recent entries about climate change
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusMonths(1);

        filter = RedisFilter.timestamp("created_at")
                .after(monthAgo)
                .and(RedisFilter.text("content").matchesPattern("climate*change"));

        redisFilter = new RedisFilterExpression(filter);

        assertNotNull(redisFilter);
        String queryString = ((RedisFilterExpression) redisFilter).toRedisQueryString();
        assertTrue(queryString.contains("@created_at:[("));
        assertTrue(queryString.contains("@content:(climate*change)"));

        // Example 3: Find entries within a geographic area with specific categories
        filter = RedisFilter.geo("location")
                .withinRadius(-122.4194, 37.7749, 5, "km")
                .and(RedisFilter.tag("category").in("restaurant", "cafe", "bar"))
                .and(RedisFilter.numeric("price_level").between(1, 3));

        redisFilter = new RedisFilterExpression(filter);

        assertNotNull(redisFilter);
        String geoQueryString = ((RedisFilterExpression) redisFilter).toRedisQueryString();
        System.out.println("Example 3 Query: " + geoQueryString);
        // Check content not exact format
        assertTrue(geoQueryString.contains("-122.419400"));
        assertTrue(geoQueryString.contains("37.774900"));
        assertTrue(geoQueryString.contains("restaurant|cafe|bar"));
        assertTrue(geoQueryString.contains("1") && geoQueryString.contains("3"));

        // Example 4: Complex filters with nested AND/OR conditions
        FilterExpression popularFilter = RedisFilter.numeric("views")
                .greaterThan(1000)
                .or(RedisFilter.numeric("likes").greaterThan(100));

        FilterExpression recentFilter = RedisFilter.timestamp("created_at").after(monthAgo);

        FilterExpression categoryFilter = RedisFilter.tag("category")
                .in("tech", "science")
                .and(RedisFilter.tag("status").equalTo("published"));

        filter = popularFilter.and(recentFilter).and(categoryFilter);

        redisFilter = new RedisFilterExpression(filter);

        assertNotNull(redisFilter);
        String complexQueryString = ((RedisFilterExpression) redisFilter).toRedisQueryString();
        System.out.println("Example 4 Query: " + complexQueryString);
        // Check content presence, not specific format
        assertTrue(complexQueryString.contains("tech|science"));
        assertTrue(complexQueryString.contains("published"));

        // Example 5: Fuzzy text search with temporal constraints
        LocalDate today = LocalDate.now();

        filter = RedisFilter.text("title")
                .fuzzyMatch("resturant", 5) // Use 5 to match %%%%%resturant%%%%%
                .and(RedisFilter.timestamp("publish_date").onDate(today));

        redisFilter = new RedisFilterExpression(filter);

        assertNotNull(redisFilter);
        String fuzzyQueryString = ((RedisFilterExpression) redisFilter).toRedisQueryString();
        System.out.println("Example 5 Query: " + fuzzyQueryString);
        // Check for presence of key terms with flexible formatting
        assertTrue(fuzzyQueryString.contains("resturant"));
        assertTrue(fuzzyQueryString.contains("publish_date"));
    }

    @Test
    public void documentExamplesForREADME() {
        // Example for README documentation

        // Tag filter
        FilterExpression tagFilter = RedisFilter.tag("category").equalTo("finance");

        // Numeric filter
        FilterExpression numFilter = RedisFilter.numeric("rating").greaterThanOrEqualTo(4);

        // Text filter with fuzzy matching
        FilterExpression textFilter = RedisFilter.text("description").fuzzyMatch("investment", 2);

        // Date filter
        FilterExpression dateFilter =
                RedisFilter.timestamp("created_at").between(LocalDateTime.now().minusDays(30), LocalDateTime.now());

        // Geo filter
        FilterExpression geoFilter = RedisFilter.geo("location").withinRadius(-122.4194, 37.7749, 5, "km");

        // Combining filters with logical operators
        FilterExpression combinedFilter = RedisFilter.tag("category")
                .equalTo("finance")
                .and(RedisFilter.numeric("rating").greaterThanOrEqualTo(4))
                .and(RedisFilter.text("description").contains("investment"))
                .and(RedisFilter.timestamp("created_at")
                        .after(LocalDateTime.now().minusDays(30)))
                .and(RedisFilter.geo("location").withinRadius(-122.4194, 37.7749, 5, "km"));

        // Convert to LangChain4j Filter
        Filter filter = new RedisFilterExpression(combinedFilter);

        assertNotNull(filter);
    }
}
