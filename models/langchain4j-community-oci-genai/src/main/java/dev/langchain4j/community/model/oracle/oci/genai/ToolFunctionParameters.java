package dev.langchain4j.community.model.oracle.oci.genai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.oracle.bmc.http.client.Serializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <pre>{@code
 * {
 *     "type": "function",
 *     "function": {
 *         "name": "currentTime",
 *         "description": "Returns current local time now at provided location.",
 *         "parameters": {
 *             "type": "object",
 *             "properties": {
 *                 "location": {
 *                     "type": "string",
 *                     "description": "The location where the time will be determined."
 *                 }
 *             },
 *             "required": [
 *                 "location"
 *             ]
 *         }
 *     }
 * }
 * }</pre>
 */
@JsonPropertyOrder({"type", "properties", "required"})
public class ToolFunctionParameters {

    @JsonProperty("type")
    private String type = "object";

    @JsonProperty("properties")
    private Map<String, Object> properties = new HashMap<>();

    @JsonProperty("required")
    private List<String> required = new ArrayList<>();

    void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }

    void addRequired(String required) {
        this.required.add(required);
    }

    @Override
    public String toString() {
        try {
            return Serializer.getDefault().writeValueAsString(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
