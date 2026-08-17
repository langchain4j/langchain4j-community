package dev.langchain4j.community.store.embedding.redis.vectorsets;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimilarityFilterMapper {

    private static final Logger log = LoggerFactory.getLogger(SimilarityFilterMapper.class);

    public SimilarityFilterMapper() {}

    Optional<String> from(Filter filter) {
        if (filter == null) return Optional.empty();

        if (filter instanceof final IsEqualTo e) {
            return format(".%s==%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsNotEqualTo e) {
            return format(".%s!=%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsGreaterThanOrEqualTo e) {
            return format(".%s>=%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsGreaterThan e) {
            return format(".%s>%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsLessThan e) {
            return format(".%s<%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsLessThanOrEqualTo e) {
            return format(".%s<=%s", e.key(), e.comparisonValue());
        } else if (filter instanceof final IsIn e) {
            return format(".%s in %s", e.key(), e.comparisonValues());
        } else if (filter instanceof final IsNotIn e) {
            return format(".%s not (in %s)", e.key(), e.comparisonValues());
        } else if (filter instanceof final And e) {
            return from(e.left())
                    .flatMap(left ->
                            from(e.right())
                                    .map(right -> String.format("(%s and %s)", left, right)));
        } else if (filter instanceof final Or e) {
            return from(e.left())
                    .flatMap(left ->
                            from(e.right())
                                    .map(right -> String.format("(%s or %s)", left, right)));
        } else if (filter instanceof final Not e) {
            return from(e.expression()).map(not -> String.format("not (%s)", not));
        } else {
            log.warn("Type {} not supported.", filter);
            return Optional.empty();
        }
    }

    private Optional<String> format(String format, String key, Object value) {
        return Optional.of(String.format(format, key, toString(value)));
    }

    private String toString(Object input) {
        if (input == null) return null;

        Function<Collection<?>, String> collectIntoArray = i -> {
            String joined = i
                    .stream()
                    .map(e -> {
                        if (e == null) {
                            return null;
                        } else if (e instanceof String s) {
                            return toString(s);
                        } else if (e instanceof  UUID s){
                            return toString(s);
                        } else {
                            return e.toString();
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));

            return String.format("[%s]", joined);
        };

        if (input instanceof  String s) {
            return String.format("\"%s\"", s);
        } else if (input instanceof  UUID s){
            return String.format("\"%s\"", s);
        }
        else if (input instanceof Collection<?> c) {
            return collectIntoArray.apply(c);
        } else {
            return input.toString();
        }
    }

}
