package dev.langchain4j.community.data.document.transformer.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Internal
class LLMGraphTransformerUtils {

    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);

    private LLMGraphTransformerUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate utility class.");
    }

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

    static <T> T parseJson(String jsonString) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(jsonString, new TypeReference<>() {});
    }
}
