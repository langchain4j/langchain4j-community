package dev.langchain4j.community.store.filter;

/**
 * Utility class for escaping special characters in Redis queries.
 * Based on the Python implementation in redis-vl.
 */
public class TokenEscaper {

    /**
     * Creates a new instance of TokenEscaper.
     * This class provides utility methods for properly escaping special characters
     * in Redis query strings.
     */
    public TokenEscaper() {
        // Default constructor
    }

    // Characters that need to be escaped in Redis queries
    private static final char[] SPECIAL_CHARS = {
        ',', '.', '<', '>', '{', '}', '[', ']', '"', '\'', ':', ';', '!', '@',
        '#', '$', '%', '^', '&', '*', '(', ')', '-', '+', '=', '~', '|', '\\',
        '/', '?', ' '
    };

    /**
     * Escapes special characters in a string for use in Redis queries.
     *
     * @param text The text to escape
     * @return The escaped text
     */
    public String escape(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length() * 2);

        for (char c : text.toCharArray()) {
            if (needsEscaping(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * Checks if a character needs to be escaped.
     *
     * @param c The character to check
     * @return true if the character needs escaping, false otherwise
     */
    private boolean needsEscaping(char c) {
        for (char special : SPECIAL_CHARS) {
            if (c == special) {
                return true;
            }
        }
        return false;
    }
}
