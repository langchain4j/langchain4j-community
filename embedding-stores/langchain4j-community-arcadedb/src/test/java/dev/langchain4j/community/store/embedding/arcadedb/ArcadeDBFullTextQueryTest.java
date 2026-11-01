package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArcadeDBFullTextQueryTest {

    @Test
    void should_escape_lucene_special_characters() {
        assertThat(ArcadeDBEmbeddingStore.escapeFullTextQuery("where was the 3/5 compromise (US)?"))
                .isEqualTo("where was the 3\\/5 compromise \\(US\\)\\?");
        assertThat(ArcadeDBEmbeddingStore.escapeFullTextQuery("a+b-c!d:e^f[g]h\"i{j}k~l*m|n&o\\p"))
                .isEqualTo("a\\+b\\-c\\!d\\:e\\^f\\[g\\]h\\\"i\\{j\\}k\\~l\\*m\\|n\\&o\\\\p");
    }

    @Test
    void should_lower_case_operators_but_not_words_containing_them() {
        assertThat(ArcadeDBEmbeddingStore.escapeFullTextQuery("cats AND dogs OR birds NOT fish"))
                .isEqualTo("cats and dogs or birds not fish");
        assertThat(ArcadeDBEmbeddingStore.escapeFullTextQuery("ANDROID ORACLE NOTHING Andes"))
                .isEqualTo("ANDROID ORACLE NOTHING Andes");
    }

    @Test
    void should_leave_plain_text_unchanged() {
        assertThat(ArcadeDBEmbeddingStore.escapeFullTextQuery("what does sip stand for in ims"))
                .isEqualTo("what does sip stand for in ims");
    }
}
