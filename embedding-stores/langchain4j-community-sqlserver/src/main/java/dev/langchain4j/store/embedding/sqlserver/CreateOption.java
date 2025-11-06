package dev.langchain4j.store.embedding.sqlserver;

/**
 * Options which configure the creation of database schema objects, such as tables and indexes.
 */
public enum CreateOption {

    /** No attempt is made to create the schema object. */
    CREATE_NONE,
    /** A new schema object is created. If the object already exists, an error is thrown.*/
    CREATE,
    /** An existing schema object is created only if it does not already exist. */
    CREATE_IF_NOT_EXISTS,
    /** An existing schema object is dropped and replaced with a new one. */
    CREATE_OR_REPLACE
}
