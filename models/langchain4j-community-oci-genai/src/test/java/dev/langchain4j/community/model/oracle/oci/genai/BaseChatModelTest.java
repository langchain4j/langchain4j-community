package dev.langchain4j.community.model.oracle.oci.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BaseChatModelTest {

    @Test
    void setNotNull() {
        assertThat(setIfNotNull(null)).isNull();
        assertThat(setIfNotNull(Map.of())).isNull();
        assertThat(setIfNotNull(List.of())).isNull();
        assertThat(setIfNotNull(Set.of())).isNull();
        assertThat(setIfNotNull(new HashMap<>(0))).isNull();

        var map = new HashMap<>();
        map.put("key", "value");
        assertThat(setIfNotNull(map)).isEqualTo(map);
        assertThat(setIfNotNull(5)).isEqualTo(5);
        assertThat(setIfNotNull("5")).isEqualTo("5");
        assertThat(setIfNotNull(List.of("5"))).isEqualTo(List.of("5"));
    }

    Object setIfNotNull(Object value) {
        AtomicReference<Object> ref = new AtomicReference<>();
        BaseChatModel.setIfNotNull(value, ref::set);
        return ref.get();
    }
}
