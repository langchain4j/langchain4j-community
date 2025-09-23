package dev.langchain4j.community.web.search.duckduckgo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

class Utils {

    private Utils() {}

    static String buildFormData(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        return params.entrySet().stream()
                .map(entry -> {
                    String key = urlEncode(entry.getKey());
                    String value = urlEncode(String.valueOf(entry.getValue()));
                    return key + "=" + value;
                })
                .collect(Collectors.joining("&"));
    }

    protected static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
