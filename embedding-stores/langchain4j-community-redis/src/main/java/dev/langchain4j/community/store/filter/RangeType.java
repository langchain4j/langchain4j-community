package dev.langchain4j.community.store.filter;

/**
 * Controls inclusivity of range boundaries for numeric and timestamp ranges.
 */
public enum RangeType {
    /**
     * Both range boundaries are inclusive
     */
    INCLUSIVE,

    /**
     * Both range boundaries are exclusive
     */
    EXCLUSIVE,

    /**
     * Only left boundary is inclusive, right boundary is exclusive
     */
    LEFT_INCLUSIVE,

    /**
     * Only right boundary is inclusive, left boundary is exclusive
     */
    RIGHT_INCLUSIVE
}
