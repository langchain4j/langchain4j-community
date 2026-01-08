package dev.langchain4j.community.store.embedding.s3;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class S3VectorsMetadataFilterMapperTest {

    @Test
    void should_return_null_for_null_filter() {
        Document result = S3VectorsMetadataFilterMapper.map(null);
        assertThat(result).isNull();
    }

    @Test
    void should_map_equal_with_string() {
        IsEqualTo filter = new IsEqualTo("genre", "scifi");
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$eq");
        assertThat(genreDoc.get("$eq").asString()).isEqualTo("scifi");
    }

    @Test
    void should_map_equal_with_number() {
        IsEqualTo filter = new IsEqualTo("rating", 5);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$eq");
        assertThat(ratingDoc.get("$eq").asNumber().intValue()).isEqualTo(5);
    }

    @Test
    void should_map_equal_with_boolean() {
        IsEqualTo filter = new IsEqualTo("active", true);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("active");
        Map<String, Document> activeDoc = result.asMap().get("active").asMap();
        assertThat(activeDoc).containsKey("$eq");
        assertThat(activeDoc.get("$eq").asBoolean()).isTrue();
    }

    @Test
    void should_map_equal_with_uuid() {
        UUID uuid = UUID.randomUUID();
        IsEqualTo filter = new IsEqualTo("id", uuid);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("id");
        Map<String, Document> idDoc = result.asMap().get("id").asMap();
        assertThat(idDoc).containsKey("$eq");
        assertThat(idDoc.get("$eq").asString()).isEqualTo(uuid.toString());
    }

    @Test
    void should_map_not_equal() {
        IsNotEqualTo filter = new IsNotEqualTo("genre", "horror");
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$ne");
        assertThat(genreDoc.get("$ne").asString()).isEqualTo("horror");
    }

    @Test
    void should_map_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("rating", 4.0);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$gt");
        assertThat(ratingDoc.get("$gt").asNumber().doubleValue()).isEqualTo(4.0);
    }

    @Test
    void should_map_greater_than_or_equal() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("year", 2020);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("year");
        Map<String, Document> yearDoc = result.asMap().get("year").asMap();
        assertThat(yearDoc).containsKey("$gte");
        assertThat(yearDoc.get("$gte").asNumber().intValue()).isEqualTo(2020);
    }

    @Test
    void should_map_less_than() {
        IsLessThan filter = new IsLessThan("price", 100);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("price");
        Map<String, Document> priceDoc = result.asMap().get("price").asMap();
        assertThat(priceDoc).containsKey("$lt");
        assertThat(priceDoc.get("$lt").asNumber().intValue()).isEqualTo(100);
    }

    @Test
    void should_map_less_than_or_equal() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("quantity", 10);
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("quantity");
        Map<String, Document> quantityDoc = result.asMap().get("quantity").asMap();
        assertThat(quantityDoc).containsKey("$lte");
        assertThat(quantityDoc.get("$lte").asNumber().intValue()).isEqualTo(10);
    }

    @Test
    void should_map_in() {
        IsIn filter = new IsIn("category", asList("books", "movies", "games"));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("category");
        Map<String, Document> categoryDoc = result.asMap().get("category").asMap();
        assertThat(categoryDoc).containsKey("$in");
        List<Document> values = categoryDoc.get("$in").asList();
        assertThat(values).hasSize(3);
        List<String> stringValues = values.stream().map(Document::asString).toList();
        assertThat(stringValues).containsExactlyInAnyOrder("books", "movies", "games");
    }

    @Test
    void should_map_not_in() {
        IsNotIn filter = new IsNotIn("status", asList("deleted", "archived"));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("status");
        Map<String, Document> statusDoc = result.asMap().get("status").asMap();
        assertThat(statusDoc).containsKey("$nin");
        List<Document> values = statusDoc.get("$nin").asList();
        assertThat(values).hasSize(2);
        List<String> stringValues = values.stream().map(Document::asString).toList();
        assertThat(stringValues).containsExactlyInAnyOrder("deleted", "archived");
    }

    @Test
    void should_map_and() {
        // given
        And filter = new And(
                new IsEqualTo("genre", "scifi"),
                new IsGreaterThanOrEqualTo("year", 2020)
        );

        // when
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        // then
        assertThat(result.asMap()).containsKey("$and");
        List<Document> operands = result.asMap().get("$and").asList();
        assertThat(operands).hasSize(2);
        assertThat(operands.get(0).asMap()).containsKey("genre");
        assertThat(operands.get(1).asMap()).containsKey("year");
    }

    @Test
    void should_map_or() {
        Or filter = new Or(
                new IsEqualTo("genre", "scifi"),
                new IsEqualTo("genre", "fantasy")
        );
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("$or");
        List<Document> operands = result.asMap().get("$or").asList();
        assertThat(operands).hasSize(2);
    }

    @Test
    void should_map_not_equal_to_not_equal() {
        Not filter = new Not(new IsEqualTo("genre", "horror"));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$ne");
        assertThat(genreDoc.get("$ne").asString()).isEqualTo("horror");
    }

    @Test
    void should_map_not_not_equal_to_equal() {
        Not filter = new Not(new IsNotEqualTo("genre", "scifi"));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$eq");
        assertThat(genreDoc.get("$eq").asString()).isEqualTo("scifi");
    }

    @Test
    void should_map_not_greater_than_to_less_than_or_equal() {
        Not filter = new Not(new IsGreaterThan("rating", 4));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$lte");
    }

    @Test
    void should_map_not_greater_than_or_equal_to_less_than() {
        Not filter = new Not(new IsGreaterThanOrEqualTo("rating", 4));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$lt");
    }

    @Test
    void should_map_not_less_than_to_greater_than_or_equal() {
        Not filter = new Not(new IsLessThan("rating", 4));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$gte");
    }

    @Test
    void should_map_not_less_than_or_equal_to_greater_than() {
        Not filter = new Not(new IsLessThanOrEqualTo("rating", 4));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("rating");
        Map<String, Document> ratingDoc = result.asMap().get("rating").asMap();
        assertThat(ratingDoc).containsKey("$gt");
    }

    @Test
    void should_map_not_in_to_not_in() {
        Not filter = new Not(new IsIn("genre", asList("horror", "thriller")));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$nin");
    }

    @Test
    void should_map_not_not_in_to_in() {
        Not filter = new Not(new IsNotIn("genre", asList("horror", "thriller")));
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        assertThat(result.asMap()).containsKey("genre");
        Map<String, Document> genreDoc = result.asMap().get("genre").asMap();
        assertThat(genreDoc).containsKey("$in");
    }

    @Test
    void should_map_not_and_to_or_of_nots() {
        // given
        Not filter = new Not(new And(
                new IsEqualTo("genre", "horror"),
                new IsEqualTo("year", 2020)
        ));

        // when
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        // then
        assertThat(result.asMap()).containsKey("$or");
    }

    @Test
    void should_map_not_or_to_and_of_nots() {
        // given
        Not filter = new Not(new Or(
                new IsEqualTo("genre", "horror"),
                new IsEqualTo("year", 2020)
        ));

        // when
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        // then
        assertThat(result.asMap()).containsKey("$and");
    }

    @Test
    void should_map_nested_filters() {
        // given
        And filter = new And(
                new IsEqualTo("genre", "scifi"),
                new Or(
                        new IsGreaterThanOrEqualTo("year", 2020),
                        new IsGreaterThan("rating", 4)
                )
        );

        // when
        Document result = S3VectorsMetadataFilterMapper.map(filter);

        // then
        assertThat(result.asMap()).containsKey("$and");
        List<Document> operands = result.asMap().get("$and").asList();
        assertThat(operands).hasSize(2);
        assertThat(operands.get(1).asMap()).containsKey("$or");
    }

    @Test
    void should_throw_for_unsupported_filter_type() {
        Filter customFilter = new Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        };
        assertThatThrownBy(() -> S3VectorsMetadataFilterMapper.map(customFilter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported filter type");
    }
}

