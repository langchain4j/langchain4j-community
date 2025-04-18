package dev.langchain4j.community.web.search.searxng;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class Utils {
    private Utils() {}

    public static String makeUrl(String baseUrl, String path, Map<String, Object> params) {
        StringBuilder builder = new StringBuilder();
        builder.append(baseUrl);
        if (!baseUrl.endsWith("/")) {
            builder.append("/");
        }
        if (path.startsWith("/")) {
            builder.append(path, 1, path.length());
        } else {
            builder.append(path);
        }
        boolean isFirstParam = true;

        // for process space.
        // " " -- URLEncoder.encode() --> "+" -- replace -->"%20"
        Pattern pattern = Pattern.compile("\\+");
        for (final Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            String encodeKey = URLEncoder.encode(key, Charset.defaultCharset());
            builder.append(isFirstParam ? "?" : "&").append(encodeKey);

            Object value = entry.getValue();
            if (value != null) {
                String valueStr = Objects.toString(value);
                String encodeValue = URLEncoder.encode(valueStr, Charset.defaultCharset());
                encodeValue = pattern.matcher(encodeValue).replaceAll("%20");
                builder.append("=").append(encodeValue);
            }
            isFirstParam = false;
        }
        return builder.toString();
    }
}
