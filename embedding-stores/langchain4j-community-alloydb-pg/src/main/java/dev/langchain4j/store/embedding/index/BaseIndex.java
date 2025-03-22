package dev.langchain4j.store.embedding.index;

import java.util.List;

/**
 * Interface for indexes
 */
public interface BaseIndex {

    /** base index name suffix */
    final String DEFAULT_INDEX_NAME_SUFFIX = "langchainvectorindex";

    /**
     * get  index query options
     * @return  index query options string
     */
    public String getIndexOptions();

    /**
     * the distance strategy for the index
     * @return DistanceStrategy
     */
    public DistanceStrategy getDistanceStrategy();

    /**
     * retrieve partial indexes
     * @return list of partial indexes
     */
    public List<String> getPartialIndexes();

    /**
     * retrieve name
     * @return name
     */
    public String getName();

    /**
     * retrieve index type
     * @return index type String
     */
    public String getIndexType();
}
