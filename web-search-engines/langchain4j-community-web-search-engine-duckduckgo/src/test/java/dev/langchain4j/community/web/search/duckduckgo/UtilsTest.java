package dev.langchain4j.community.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void test_buildFormData_basic() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "AI");
        params.put("safe", "strict");
        String actualFormData = Utils.buildFormData(params);
        String expectedFormData = "q=AI&safe=strict";
        assertThat(actualFormData).isEqualTo(expectedFormData);
    }

    @Test
    void test_buildFormData_with_space_and_question_mark() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "What is LangChain4j?");
        params.put("safe", "strict");
        String actualFormData = Utils.buildFormData(params);
        String expectedFormData = "q=What%20is%20LangChain4j%3F&safe=strict";
        assertThat(actualFormData).isEqualTo(expectedFormData);
    }

    @Test
    void test_buildFormData_with_special_characters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "test@email.com & more");
        params.put("kl", "us-en");
        String actualFormData = Utils.buildFormData(params);
        String expectedFormData = "q=test%40email.com%20%26%20more&kl=us-en";
        assertThat(actualFormData).isEqualTo(expectedFormData);
    }

    @Test
    void test_buildFormData_null_params() {
        String actualFormData = Utils.buildFormData(null);
        assertThat(actualFormData).isEmpty();
    }

    @Test
    void test_urlEncode_special_characters() {
        String input = "test@email.com?param=value&other=data";
        String encoded = Utils.urlEncode(input);
        assertThat(encoded).isEqualTo("test%40email.com%3Fparam%3Dvalue%26other%3Ddata");
    }
}
