package dev.langchain4j.community.store.embedding.oceanbase;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.List;

/**
 * Utility class for validating method arguments.
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Returns a null-safe item from a List.
     * @param list List to get an item from.
     * @param index Index of the item to get.
     * @param name Name of the parameter, for error messages.
     * @param <T> Type of the list items.
     * @return The item at the given index.
     * @throws IllegalArgumentException If the item is null.
     */
    public static <T> T ensureIndexNotNull(List<T> list, int index, String name) {
        if (index < 0 || index >= list.size()) {
            throw new IllegalArgumentException(
                    String.format("Index %d is out of bounds for %s list of size %d", index, name, list.size()));
        }
        T item = list.get(index);
        if (item == null) {
            throw new IllegalArgumentException(String.format("%s[%d] is null", name, index));
        }
        return item;
    }

    /**
     * Ensures that the given string is not null and not empty.
     *
     * @param value The string to check.
     * @param name The name of the string to be used in the exception message.
     * @return The string if it is not null and not empty.
     * @throws IllegalArgumentException if the string is null or empty.
     */
    public static String ensureNotEmpty(String value, String name) {
        ensureNotNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }
}
