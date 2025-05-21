package dev.langchain4j.community.store.routing.redis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a match between a query and a route.
 *
 * <p>A route match contains the name of the matched route, the distance (similarity score),
 * and any metadata associated with the route.</p>
 */
public class RouteMatch {

    private final String routeName;
    private final double distance;
    private final Map<String, Object> metadata;

    /**
     * Creates a new RouteMatch.
     *
     * @param routeName The name of the matched route
     * @param distance The distance (similarity score) between the query and the route
     * @param metadata The metadata associated with the route
     */
    public RouteMatch(String routeName, double distance, Map<String, Object> metadata) {
        this.routeName = routeName;
        this.distance = distance;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Gets the name of the matched route.
     *
     * @return The route name
     */
    public String getRouteName() {
        return routeName;
    }

    /**
     * Gets the distance (similarity score) between the query and the route.
     *
     * @return The distance
     */
    public double getDistance() {
        return distance;
    }

    /**
     * Gets the metadata associated with the matched route.
     *
     * @return The metadata
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public String toString() {
        return "RouteMatch{" + "routeName='"
                + routeName + '\'' + ", distance="
                + distance + ", metadata="
                + metadata + '}';
    }
}
