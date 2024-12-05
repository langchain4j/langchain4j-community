package dev.langchain4j.community.model.xinference.client.chat.message;

import dev.langchain4j.community.model.xinference.client.chat.Role;

public interface Message {
    Role getRole();
}
