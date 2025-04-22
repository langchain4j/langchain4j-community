package dev.langchain4j.community.web.search.searxng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UtilTest {
    @Test
    void test_make_url() {
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "AI");
        params.put("safesearch", 2);
        String actualUrl = Utils.pathWithQuery(path, params);
        String expectUrl = path + "?" + "q=AI&safesearch=2";
        assertThat(actualUrl).isEqualTo(expectUrl);
    }

    @Test
    void test_make_url_with_space() {
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "qqq stock quote");
        params.put("safesearch", 2);
        String actualUrl = Utils.pathWithQuery(path, params);
        String expectUrl = path + "?" + "q=qqq%20stock%20quote&safesearch=2";
        assertThat(actualUrl).isEqualTo(expectUrl);
    }

    @Test
    void test_make_url_with_question_mark() {
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "LangChain4j?");
        params.put("safesearch", 2);
        String actualUrl = Utils.pathWithQuery(path, params);
        String expectUrl = path + "?" + "q=LangChain4j%3F&safesearch=2";
        assertThat(actualUrl).isEqualTo(expectUrl);
    }

    @Test
    void test_makeUrl_with_space_and_question_mark() {
        String path = "search";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "What is LangChain4j?");
        params.put("safesearch", 2);
        String actualUrl = Utils.pathWithQuery(path, params);
        String expectUrl = path + "?" + "q=What%20is%20LangChain4j%3F&safesearch=2";
        assertThat(actualUrl).isEqualTo(expectUrl);
    }
}
