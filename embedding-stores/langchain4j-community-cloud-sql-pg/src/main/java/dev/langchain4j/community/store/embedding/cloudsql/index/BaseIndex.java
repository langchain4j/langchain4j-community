package dev.langchain4j.community.store.embedding.cloudsql.index;

import java.util.List;

/**
 * Interface for indexes
 */
public interface BaseIndex {

    /**
     * base index name suffix
     */
    String DEFAULT_INDEX_NAME_SUFFIX = "langchainvectorindex";

    /**
     * get  index query options
     *
     * @return index query options string
     */
    String getIndexOptions();

    /**
     * the distance strategy for the index
     *
     * @return DistanceStrategy
     */
    DistanceStrategy getDistanceStrategy();

    /**
     * retrieve partial indexes
     *
     * @return list of partial indexes
     */
    List<String> getPartialIndexes();

    /**
     * retrieve name
     *
     * @return name
     */
    String getName();

    /**
     * retrieve index type
     *
     * @return index type String
     */
    String getIndexType();
}
