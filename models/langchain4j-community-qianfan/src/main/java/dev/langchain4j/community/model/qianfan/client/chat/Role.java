package dev.langchain4j.community.model.qianfan.client.chat;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function");

    @JsonValue
    private final String stringValue;

    Role(String stringValue) {
        this.stringValue = stringValue;
    }

    static Role from(String stringValue) {
        for (Role role : values()) {
            if (role.stringValue.equals(stringValue)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Unknown role: '" + stringValue + "'");
    }

    @Override
    public String toString() {
        return this.stringValue;
    }
}
