package dev.langchain4j.community.model.minimax.client.chat.message;

import dev.langchain4j.community.model.minimax.client.chat.Role;

public interface Message {
    Role getRole();
}
