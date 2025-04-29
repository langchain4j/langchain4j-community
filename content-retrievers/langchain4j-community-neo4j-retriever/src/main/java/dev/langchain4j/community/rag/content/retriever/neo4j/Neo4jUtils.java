package dev.langchain4j.community.rag.content.retriever.neo4j;

import static org.neo4j.cypherdsl.support.schema_name.SchemaNames.sanitize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
class Neo4jUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);

    /**
     * Get the text block wrapped by triple backticks
     */
    static String getBacktickText(String cypherQuery) {
        Matcher matcher = BACKTICKS_PATTERN.matcher(cypherQuery);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return cypherQuery;
    }

    static String sanitizeOrThrows(String value, String config) {
        return sanitize(value).orElseThrow(() -> {
            String invalidSanitizeValue = String.format(
                    "The value %s, to assign to configuration %s, cannot be safely quoted", value, config);
            return new RuntimeException(invalidSanitizeValue);
        });
    }

    static Map<String, Object> toMap(Object object) {
        return OBJECT_MAPPER.convertValue(object, new TypeReference<>() {});
    }

    static String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
