/*
 * Copyright (c) 2026. DENODO Technologies.
 * http://www.denodo.com
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of DENODO
 * Technologies ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the terms
 * of the license agreement you entered into with DENODO.
 */
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
    AUTO,
    ON,
    OFF;
}
