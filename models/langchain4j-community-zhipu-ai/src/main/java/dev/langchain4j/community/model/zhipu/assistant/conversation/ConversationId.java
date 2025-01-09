package dev.langchain4j.community.model.zhipu.assistant.conversation;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * This class represents the ConversationId data for a specific conversation.
 */
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationId {

    /**
     * 唯一id 用来调用获取对话或创作结果接口
     */
    private String id;

    /**
     * 会话id The conversation ID.
     */
    private String conversationId;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(final String conversationId) {
        this.conversationId = conversationId;
    }
}
