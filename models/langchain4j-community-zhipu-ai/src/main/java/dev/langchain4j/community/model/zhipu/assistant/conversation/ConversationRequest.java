package dev.langchain4j.community.model.zhipu.assistant.conversation;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.zhipu.assistant.AssistantKeyValuePair;
import java.util.List;

/**
 * 3.5 创建对话或创作请求 (只支持增量模式)
 * @author cuiwei
 * @since  2024/11/25
 */
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ConversationRequest {

    /**
     * 智能体（应用）id
     */
    private String appId;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 智能体 ID（目前无用）
     */
    private String assistantId;

    /**
     * 输入模版中的变量列表
     */
    private List<AssistantKeyValuePair> keyValuePairs;

    /**
     * 适用问答类智能体（应用）:  用于知识筛选, 不传默认使用智能体（应用）配置
     */
    private List<String> documentIds;

    /**
     * 适用问答类智能体（应用）:  用于知识筛选, 不传默认使用智能体（应用）配置
     */
    private List<String> knowledgeIds;

    public ConversationRequest() {}

    public ConversationRequest(
            final String appId,
            final String conversationId,
            final String assistantId,
            final List<AssistantKeyValuePair> keyValuePairs,
            final List<String> documentIds,
            final List<String> knowledgeIds) {
        this.appId = appId;
        this.conversationId = conversationId;
        this.assistantId = assistantId;
        this.keyValuePairs = keyValuePairs;
        this.documentIds = documentIds;
        this.knowledgeIds = knowledgeIds;
    }

    public static ConversationRequestBuilder builder() {
        return new ConversationRequestBuilder();
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(final String appId) {
        this.appId = appId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(final String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(final String assistantId) {
        this.assistantId = assistantId;
    }

    public List<AssistantKeyValuePair> getKeyValuePairs() {
        return keyValuePairs;
    }

    public void setKeyValuePairs(final List<AssistantKeyValuePair> keyValuePairs) {
        this.keyValuePairs = keyValuePairs;
    }

    public List<String> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(final List<String> documentIds) {
        this.documentIds = documentIds;
    }

    public List<String> getKnowledgeIds() {
        return knowledgeIds;
    }

    public void setKnowledgeIds(final List<String> knowledgeIds) {
        this.knowledgeIds = knowledgeIds;
    }

    public static class ConversationRequestBuilder {
        /**
         * 智能体（应用）id
         */
        private String appId;

        /**
         * 会话 ID
         */
        private String conversationId;

        /**
         * 智能体 ID（目前无用）
         */
        private String assistantId;

        /**
         * 输入模版中的变量列表
         */
        private List<AssistantKeyValuePair> keyValuePairs;

        /**
         * 适用问答类智能体（应用）:  用于知识筛选, 不传默认使用智能体（应用）配置
         */
        private List<String> documentIds;

        /**
         * 适用问答类智能体（应用）:  用于知识筛选, 不传默认使用智能体（应用）配置
         */
        private List<String> knowledgeIds;

        public ConversationRequestBuilder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public ConversationRequestBuilder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public ConversationRequestBuilder assistantId(String assistantId) {
            this.assistantId = assistantId;
            return this;
        }

        public ConversationRequestBuilder keyValuePairs(List<AssistantKeyValuePair> keyValuePairs) {
            this.keyValuePairs = keyValuePairs;
            return this;
        }

        public ConversationRequestBuilder documentIds(List<String> documentIds) {
            this.documentIds = documentIds;
            return this;
        }

        public ConversationRequestBuilder knowledgeIds(List<String> knowledgeIds) {
            this.knowledgeIds = knowledgeIds;
            return this;
        }

        public ConversationRequest build() {
            return new ConversationRequest(
                    appId, conversationId, assistantId, keyValuePairs, documentIds, knowledgeIds);
        }
    }
}
