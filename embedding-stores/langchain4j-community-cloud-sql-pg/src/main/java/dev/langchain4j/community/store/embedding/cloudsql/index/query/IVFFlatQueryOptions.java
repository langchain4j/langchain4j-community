package dev.langchain4j.community.store.embedding.cloudsql.index.query;

import java.util.List;

/**
 * IVFFlat index query options
 */
public class IVFFlatQueryOptions implements QueryOptions {

    private final Integer probes;

    /**
     * Constructor for IVFFlatQueryOptions
     *
     * @param builder builder
     */
    public IVFFlatQueryOptions(Builder builder) {
        this.probes = builder.probes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameterSettings() {
        return List.of("ivfflat.probes = " + probes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder which configures and creates instances of {@link IVFFlatQueryOptions}.
     */
    public static class Builder {

        private Integer probes = 1;

        /**
         * @param probes number of probes
         * @return this builder
         */
        public Builder probes(Integer probes) {
            this.probes = probes;
            return this;
        }

        /**
         * Builds an {@link IVFFlatQueryOptions} store with the configuration applied to this builder.
         *
         * @return A new {@link IVFFlatQueryOptions} instance
         */
        public IVFFlatQueryOptions build() {
            return new IVFFlatQueryOptions(this);
        }
    }
}
