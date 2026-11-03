package dev.langchain4j.community.store.embedding.arcadedb;

/**
 * How a search with a metadata {@code Filter} is executed. Filters that cannot be expressed in SQL
 * ({@code ContainsString}) are always applied after the search.
 */
public enum ArcadeDBFilterStrategy {
    /**
     * Remote mode: as {@link #PRE_FILTER}. Embedded mode: as {@link #POST_FILTER} first; when fewer than
     * {@code maxResults} candidates pass the filter, the search is repeated as {@link #PRE_FILTER}. Selective filters
     * thus return complete results, and broad ones keep the cost of an unfiltered search.
     */
    ADAPTIVE,
    /**
     * The filter selects the allowed records in SQL ({@code SELECT @rid ... WHERE <filter>}, which uses an index on
     * the metadata property if there is one), and the vector search returns the nearest allowed ones; hybrid search
     * applies the filter to the full-text side too.
     */
    PRE_FILTER,
    /**
     * A fixed number of nearest candidates is fetched and the filter is applied to them (the behaviour before this
     * option): fast, but selective filters return fewer than {@code maxResults}, or no, results.
     */
    POST_FILTER
}
