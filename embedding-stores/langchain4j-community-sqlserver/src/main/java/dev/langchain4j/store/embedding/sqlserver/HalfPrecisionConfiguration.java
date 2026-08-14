package dev.langchain4j.store.embedding.sqlserver;

/**
 * Represents the configuration options for using half-precision (16-bit) floating-point numbers.
 * This enumeration defines the following modes:
 *
 * <ul>
 *   <li>{@code AUTO}: Automatic determination of whether to use half-precision based on the dimensions.</li>
 *   <li>{@code ON}: Explicitly enables the use of half-precision.</li>
 *   <li>{@code OFF}: Explicitly disables the use of half-precision. This mode can make the embedding store fail when the embedding dimension is too large for full precision.</li>
 * </ul>
 *
 */
public enum HalfPrecisionConfiguration {
    /**
     * Automatic determination of whether to use half-precision based on the dimensions.
     */
    AUTO,
    /**
     * Explicitly enables the use of half-precision.
     */
    ON,
    /**
     * Explicitly disables the use of half-precision. This mode can make the embedding store fail when the embedding dimension is too large for full precision.
     */
    OFF;
}
