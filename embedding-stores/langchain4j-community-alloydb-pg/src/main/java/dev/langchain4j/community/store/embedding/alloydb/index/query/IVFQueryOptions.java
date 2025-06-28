package dev.langchain4j.community.store.embedding.alloydb.index.query;

import java.util.List;

/**
 * ScaNN index query options
 */
public class IVFQueryOptions implements QueryOptions {

    private final Integer probes;

    /**
     * Constructor for IVFQueryOptions
     *
     * @param builder builder
     */
    public IVFQueryOptions(Builder builder) {
        this.probes = builder.probes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameterSettings() {
        return List.of("ivf.probes = " + probes);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder which configures and creates instances of {@link IVFQueryOptions}.
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
         * Builds an {@link IVFQueryOptions} store with the configuration applied to this builder.
         *
         * @return A new {@link IVFQueryOptions} instance
         */
        public IVFQueryOptions build() {
            return new IVFQueryOptions(this);
        }
    }
}
