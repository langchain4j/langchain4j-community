package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArcadeDBMetadataFilterMapperTest {

    @Test
    void should_quote_identifiers_and_escape_backslashes_and_backticks() {
        assertThat(ArcadeDBMetadataFilterMapper.quoteIdentifier("tenant")).isEqualTo("`tenant`");
        assertThat(ArcadeDBMetadataFilterMapper.quoteIdentifier("a`b")).isEqualTo("`a\\`b`");
        assertThat(ArcadeDBMetadataFilterMapper.quoteIdentifier("a\\b")).isEqualTo("`a\\\\b`");
        assertThat(ArcadeDBMetadataFilterMapper.quoteIdentifier("trailing\\")).isEqualTo("`trailing\\\\`");
    }

    @Test
    void should_keep_a_key_that_closes_the_quoted_name_inside_it() {
        List<Object> parameters = new ArrayList<>();
        String where = new ArcadeDBMetadataFilterMapper("meta_", true)
                .map(metadataKey("tenant` = 'a' OR 1 = 1 OR `tenant").isEqualTo("b"), parameters);

        assertThat(where).isEqualTo("`meta_tenant\\` = 'a' OR 1 = 1 OR \\`tenant` = ?");
        assertThat(parameters).containsExactly("b");
    }
}
