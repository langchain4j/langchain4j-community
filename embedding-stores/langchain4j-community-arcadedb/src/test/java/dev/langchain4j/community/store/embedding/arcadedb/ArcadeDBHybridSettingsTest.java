package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArcadeDBHybridSettingsTest {

    @Test
    void should_default_to_dense_weighted_rrf_and_an_english_analyzer() {
        ArcadeDBHybridSettings settings = new ArcadeDBHybridSettings();

        assertThat(settings.fuseOptions(400)).isEqualTo("{ fusion: 'RRF', k: 60, weights: [8, 1], limit: 400 }");
        assertThat(settings.fullTextIndexMetadata())
                .isEqualTo(" METADATA { \"analyzer\": \"org.apache.lucene.analysis.en.EnglishAnalyzer\" }");
    }

    @Test
    void should_render_custom_fusion_and_full_text_settings() {
        ArcadeDBHybridSettings settings = new ArcadeDBHybridSettings();
        settings.fusion = ArcadeDBHybridFusion.LINEAR;
        settings.denseWeight = 0.7;
        settings.fullTextWeight = 0.3;
        settings.rrfK = 10;
        settings.analyzer = "org.apache.lucene.analysis.standard.StandardAnalyzer";
        settings.bm25K1 = 1.5;
        settings.bm25B = 0.5;

        assertThat(settings.fuseOptions(200)).isEqualTo("{ fusion: 'LINEAR', k: 10, weights: [0.7, 0.3], limit: 200 }");
        assertThat(settings.fullTextIndexMetadata())
                .isEqualTo(" METADATA { \"analyzer\": \"org.apache.lucene.analysis.standard.StandardAnalyzer\","
                        + " \"bm25_k1\": 1.5, \"bm25_b\": 0.5 }");
    }

    @Test
    void should_use_arcadedb_defaults_without_analyzer_and_bm25_settings() {
        ArcadeDBHybridSettings settings = new ArcadeDBHybridSettings();
        settings.analyzer = null;

        assertThat(settings.fullTextIndexMetadata()).isEmpty();
    }

    @Test
    void should_reject_invalid_settings() {
        ArcadeDBHybridSettings negative = new ArcadeDBHybridSettings();
        negative.denseWeight = -1;
        ArcadeDBHybridSettings zero = new ArcadeDBHybridSettings();
        zero.denseWeight = 0;
        zero.fullTextWeight = 0;
        ArcadeDBHybridSettings b = new ArcadeDBHybridSettings();
        b.bm25B = 1.5;

        assertThatThrownBy(negative::validate).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(zero::validate).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(b::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
