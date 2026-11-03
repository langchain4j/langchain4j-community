package dev.langchain4j.community.store.embedding.arcadedb;

/**
 * Quantization of the vectors in ArcadeDB's {@code LSM_VECTOR} index (the stored embedding property is not
 * quantized). Applies when the index is created.
 */
public enum ArcadeDBVectorQuantization {
    /** Full-precision float vectors. */
    NONE,
    /** 8-bit scalar quantization: about 4x less index memory. */
    INT8,
    /** 1 bit per dimension: about 32x less index memory, lower recall. */
    BINARY,
    /** Product quantization with ArcadeDB's default subspaces and clusters. */
    PRODUCT
}
