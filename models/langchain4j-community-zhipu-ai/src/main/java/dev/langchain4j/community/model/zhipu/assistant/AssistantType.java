package dev.langchain4j.community.model.zhipu.assistant;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum AssistantType {
    INPUT,
    SELECTION_LIST,
    UPLOAD_FILE,
    UPLOAD_IMAGE,
    UPLOAD_VIDEO,
    INPUT_TEMPLATE;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
