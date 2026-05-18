package dev.langchain4j.community.store.embedding.cockroachdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CSpannIndexTest {

    @Test
    void emits_minimal_create_vector_index() {
        String sql = CSpannIndex.builder()
                .build()
                .getCreateIndexSql("public.embeddings", "embedding", Collections.emptyList());
        assertThat(sql)
                .isEqualTo("CREATE VECTOR INDEX IF NOT EXISTS embeddings_embedding_vector_idx "
                        + "ON public.embeddings (embedding)");
    }

    @Test
    void includes_partition_size_options() {
        String sql = CSpannIndex.builder()
                .minPartitionSize(16)
                .maxPartitionSize(128)
                .build()
                .getCreateIndexSql("public.embeddings", "embedding", Collections.emptyList());
        assertThat(sql).contains("WITH (min_partition_size = 16, max_partition_size = 128)");
    }

    @Test
    void prefix_columns_come_before_embedding_column() {
        String sql = CSpannIndex.builder()
                .build()
                .getCreateIndexSql("public.embeddings", "embedding", Arrays.asList("tenant_id"));
        assertThat(sql).contains("(tenant_id, embedding)");
    }

    @Test
    void honours_explicit_index_name() {
        String sql = CSpannIndex.builder()
                .name("my_idx")
                .build()
                .getCreateIndexSql("public.embeddings", "embedding", Collections.emptyList());
        assertThat(sql).startsWith("CREATE VECTOR INDEX IF NOT EXISTS my_idx ");
    }
}
