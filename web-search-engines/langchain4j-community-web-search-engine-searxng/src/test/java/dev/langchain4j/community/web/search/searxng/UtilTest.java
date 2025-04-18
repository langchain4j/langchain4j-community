package dev.langchain4j.community.web.search.searxng;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UtilTest {
    @Test
    void test_make_url() {
        String baseUrl = "http://localhost:57504";
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "AI");
        params.put("safesearch", 2);
        String actualUrl = Utils.makeUrl(baseUrl, path, params);
        String expectUrl = baseUrl + "/" + path + "?" + "q=AI&safesearch=2";
        Assertions.assertEquals(expectUrl, actualUrl);
    }

    @Test
    void test_make_url_with_space() {
        String baseUrl = "http://localhost:57504";
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "qqq stock quote");
        params.put("safesearch", 2);
        String actualUrl = Utils.makeUrl(baseUrl, path, params);
        String expectUrl = baseUrl + "/" + path + "?" + "q=qqq%20stock%20quote&safesearch=2";
        Assertions.assertEquals(expectUrl, actualUrl);
    }

    @Test
    void test_make_url_with_question_mark() {
        String baseUrl = "http://localhost:57504";
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "LangChain4j?");
        params.put("safesearch", 2);
        String actualUrl = Utils.makeUrl(baseUrl, path, params);
        String expectUrl = baseUrl + "/" + path + "?" + "q=LangChain4j%3F&safesearch=2";
        Assertions.assertEquals(expectUrl, actualUrl);
    }

    @Test
    void test_makeUrl_with_space_and_question_mark() {
        String baseUrl = "http://localhost:57504";
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "What is LangChain4j?");
        params.put("safesearch", 2);
        String actualUrl = Utils.makeUrl(baseUrl, path, params);
        String expectUrl = baseUrl + "/" + path + "?" + "q=What%20is%20LangChain4j%3F&safesearch=2";
        Assertions.assertEquals(expectUrl, actualUrl);
    }
}
