package dev.langchain4j.community.store.memory.chat.redis;

/**
 * Used to decide which data structure to use to store message content
 */
public enum StoreType {

    /**
     * Redis Json
     */
    JSON,

    /**
     * Redis String
     */
    STRING
}
