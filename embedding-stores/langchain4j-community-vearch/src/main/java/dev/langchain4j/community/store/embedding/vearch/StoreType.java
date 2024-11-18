package dev.langchain4j.community.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum StoreType {

    @JsonProperty("MemoryOnly")
    MEMORY_ONLY,
    @JsonProperty("RocksDB")
    ROCKS_DB
}
