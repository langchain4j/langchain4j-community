package dev.langchain4j.community.model.oracle.oci.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BaseChatModelTest {
    @Test
    void setNotNull() {
        assertNull(setIfNotNull(null));
        assertNull(setIfNotNull(Map.of()));
        assertNull(setIfNotNull(List.of()));
        assertNull(setIfNotNull(Set.of()));
        assertNull(setIfNotNull(new HashMap<>(0)));

        var map = new HashMap<>();
        map.put("key", "value");
        assertEquals(map, setIfNotNull(map));
        assertEquals(5, setIfNotNull(5));
        assertEquals("5", setIfNotNull("5"));
        assertEquals(List.of("5"), setIfNotNull(List.of("5")));
    }

    Object setIfNotNull(Object value) {
        AtomicReference<Object> ref = new AtomicReference<>();
        BaseChatModel.setIfNotNull(value, ref::set);
        return ref.get();
    }
}
