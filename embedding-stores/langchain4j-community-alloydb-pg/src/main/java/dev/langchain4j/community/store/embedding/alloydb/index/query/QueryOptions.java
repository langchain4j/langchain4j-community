package dev.langchain4j.community.store.embedding.alloydb.index.query;

import java.util.List;

/**
 * Query options interface
 */
public interface QueryOptions {

    /**
     * Convert index attributes to list of configuration
     *
     * @return List of parameter setting strings
     */
    List<String> getParameterSettings();
}
