package dev.langchain4j.community.store.routing.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a route for semantic routing with reference texts.
 *
 * <p>A route is a semantic destination to which queries can be directed.
 * It is defined by a name, a list of reference texts that represent the semantic scope of the route,
 * and a distance threshold to determine if a query matches the route.</p>
 *
 * <p>Routes can also have optional metadata for additional context.</p>
 */
public class Route {

    private final String name;
    private final List<String> references;
    private final double distanceThreshold;
    private final Map<String, Object> metadata;

    /**
     * Creates a new Route.
     *
     * @param name The unique name of the route
     * @param references The reference texts that define the semantic scope of the route
     * @param distanceThreshold The maximum cosine distance for a match (0.0 to 2.0)
     * @param metadata Optional metadata associated with the route
     */
    public Route(String name, List<String> references, double distanceThreshold, Map<String, Object> metadata) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Route name cannot be null or empty");
        }
        if (references == null || references.isEmpty()) {
            throw new IllegalArgumentException("Route references cannot be null or empty");
        }
        if (distanceThreshold < 0.0 || distanceThreshold > 2.0) {
            throw new IllegalArgumentException("Distance threshold must be between 0.0 and 2.0");
        }

        this.name = name;
        this.references = new ArrayList<>(references);
        this.distanceThreshold = distanceThreshold;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Gets the name of the route.
     *
     * @return The route name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the reference texts for the route.
     *
     * @return The reference texts
     */
    public List<String> getReferences() {
        return Collections.unmodifiableList(references);
    }

    /**
     * Gets the distance threshold for the route.
     *
     * @return The distance threshold
     */
    public double getDistanceThreshold() {
        return distanceThreshold;
    }

    /**
     * Gets the metadata for the route.
     *
     * @return The metadata
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Creates a new builder for Route.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Route.
     */
    public static class Builder {
        private String name;
        private List<String> references = new ArrayList<>();
        private double distanceThreshold = 0.2;
        private Map<String, Object> metadata = new HashMap<>();

        /**
         * Sets the name of the route.
         *
         * @param name The route name
         * @return This builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the reference texts for the route.
         *
         * @param references The reference texts
         * @return This builder
         */
        public Builder references(List<String> references) {
            this.references = new ArrayList<>(references);
            return this;
        }

        /**
         * Adds a reference text to the route.
         *
         * @param reference The reference text
         * @return This builder
         */
        public Builder addReference(String reference) {
            this.references.add(reference);
            return this;
        }

        /**
         * Sets the distance threshold for the route.
         *
         * @param distanceThreshold The distance threshold
         * @return This builder
         */
        public Builder distanceThreshold(double distanceThreshold) {
            this.distanceThreshold = distanceThreshold;
            return this;
        }

        /**
         * Sets the metadata for the route.
         *
         * @param metadata The metadata
         * @return This builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        /**
         * Adds a metadata entry to the route.
         *
         * @param key The metadata key
         * @param value The metadata value
         * @return This builder
         */
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Builds a new Route instance.
         *
         * @return A new Route
         */
        public Route build() {
            return new Route(name, references, distanceThreshold, metadata);
        }
    }
}
