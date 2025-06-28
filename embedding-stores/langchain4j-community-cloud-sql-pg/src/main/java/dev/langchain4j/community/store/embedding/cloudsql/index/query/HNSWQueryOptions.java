package dev.langchain4j.community.store.embedding.cloudsql.index.query;

import java.util.List;

/**
 * HNSW index query options
 */
public class HNSWQueryOptions implements QueryOptions {

    private final Integer efSearch;

    /**
     * Constructor for HNSWQueryOptions
     *
     * @param builder builder
     */
    public HNSWQueryOptions(Builder builder) {
        this.efSearch = builder.efSearch;
    }

    @Override
    public List<String> getParameterSettings() {
        return List.of("nsw.efS_search = " + efSearch);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder which configures and creates instances of {@link HNSWQueryOptions}.
     */
    public static class Builder {

        private Integer efSearch = 40;

        /**
         * @param efSearch size of the dynamic candidate list for search
         * @return this builder
         */
        public Builder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        /**
         * Builds an {@link HNSWQueryOptions} store with the configuration applied to this builder.
         *
         * @return A new {@link HNSWQueryOptions} instance
         */
        public HNSWQueryOptions build() {
            return new HNSWQueryOptions(this);
        }
    }
}
