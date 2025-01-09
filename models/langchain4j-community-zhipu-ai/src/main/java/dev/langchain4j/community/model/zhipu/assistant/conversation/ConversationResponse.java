package dev.langchain4j.community.model.zhipu.assistant.conversation;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.zhipu.assistant.ClientResponse;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationResponse implements ClientResponse<ConversationId> {
    private int code;
    private String message;
    private ConversationId data;

    public int getCode() {
        return code;
    }

    @Override
    public void setCode(final int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void setMessage(final String message) {
        this.message = message;
    }

    @Override
    public ConversationId getData() {
        return data;
    }

    public void setData(final ConversationId data) {
        this.data = data;
    }
}
