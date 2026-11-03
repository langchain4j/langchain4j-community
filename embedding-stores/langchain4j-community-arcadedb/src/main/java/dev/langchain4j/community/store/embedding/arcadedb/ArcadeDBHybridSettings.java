package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hybrid-search and full-text index settings shared by the remote and embedded builders.
 *
 * <p>The defaults are the best measured on BEIR Natural Questions with bge-small-en-v1.5 and ArcadeDB 26.10.2: RRF
 * weighted 8:1 towards the dense ranking and an {@code EnglishAnalyzer} full-text index (stop words and stemming).
 * For text that is not English, choose another analyzer.
 */
final class ArcadeDBHybridSettings {

    static final ArcadeDBHybridFusion DEFAULT_FUSION = ArcadeDBHybridFusion.RRF;
    static final double DEFAULT_DENSE_WEIGHT = 8;
    static final double DEFAULT_FULL_TEXT_WEIGHT = 1;
    static final int DEFAULT_RRF_K = 60;
    static final String DEFAULT_ANALYZER = "org.apache.lucene.analysis.en.EnglishAnalyzer";

    ArcadeDBHybridFusion fusion = DEFAULT_FUSION;
    double denseWeight = DEFAULT_DENSE_WEIGHT;
    double fullTextWeight = DEFAULT_FULL_TEXT_WEIGHT;
    int rrfK = DEFAULT_RRF_K;
    String analyzer = DEFAULT_ANALYZER;
    Double bm25K1;
    Double bm25B;

    ArcadeDBHybridSettings copy() {
        ArcadeDBHybridSettings copy = new ArcadeDBHybridSettings();
        copy.fusion = fusion;
        copy.denseWeight = denseWeight;
        copy.fullTextWeight = fullTextWeight;
        copy.rrfK = rrfK;
        copy.analyzer = analyzer;
        copy.bm25K1 = bm25K1;
        copy.bm25B = bm25B;
        return copy;
    }

    void validate() {
        ensureNotNull(fusion, "hybridFusion");
        ensureTrue(denseWeight >= 0 && fullTextWeight >= 0, "hybrid weights must not be negative");
        ensureTrue(denseWeight + fullTextWeight > 0, "at least one hybrid weight must be positive");
        ensureTrue(rrfK > 0, "hybridRrfK must be positive");
        ensureTrue(bm25K1 == null || bm25K1 >= 0, "fullTextBm25K1 must not be negative");
        ensureTrue(bm25B == null || (bm25B >= 0 && bm25B <= 1), "fullTextBm25B must be between 0 and 1");
    }

    /** The {@code vector.fuse()} options. */
    String fuseOptions(int limit) {
        return String.format(
                Locale.ROOT,
                "{ fusion: '%s', k: %d, weights: [%s, %s], limit: %d }",
                fusion.name(),
                rrfK,
                format(denseWeight),
                format(fullTextWeight),
                limit);
    }

    /** {@code METADATA {...}} for creating the full-text index, or an empty string for ArcadeDB's defaults. */
    String fullTextIndexMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (analyzer != null && !analyzer.isBlank()) {
            metadata.put("analyzer", "\"" + analyzer.replace("\"", "") + "\"");
        }
        if (bm25K1 != null) {
            metadata.put("bm25_k1", format(bm25K1));
        }
        if (bm25B != null) {
            metadata.put("bm25_b", format(bm25B));
        }
        if (metadata.isEmpty()) {
            return "";
        }
        return " METADATA { "
                + metadata.entrySet().stream()
                        .map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                        .collect(Collectors.joining(", "))
                + " }";
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
