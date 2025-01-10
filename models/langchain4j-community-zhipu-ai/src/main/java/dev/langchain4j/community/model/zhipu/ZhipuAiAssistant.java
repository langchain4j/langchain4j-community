package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.community.model.zhipu.assistant.AssistantKeyValuePair;
import dev.langchain4j.community.model.zhipu.assistant.AssistantType;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationId;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationRequest;
import dev.langchain4j.community.model.zhipu.assistant.problem.Problems;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAssistantBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import java.time.Duration;
import java.util.List;

public class ZhipuAiAssistant {

    private final String appId;
    private final ZhipuAssistantClient client;

    public ZhipuAiAssistant(
            String baseUrl,
            String apiKey,
            String appId,
            Boolean logRequests,
            Boolean logResponses,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
        this.appId = appId;
        this.client = ZhipuAssistantClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(getOrDefault(callTimeout, ofSeconds(60)))
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAssistantChatModelBuilder builder() {
        for (ZhipuAssistantBuilderFactory factories : loadFactories(ZhipuAssistantBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAssistantChatModelBuilder();
    }

    public List<AssistantKeyValuePair> variables() {
        return client.variables(appId);
    }

    public ConversationId conversation() {
        return client.conversation(appId);
    }

    public void generate(
            String conversationId,
            List<AssistantKeyValuePair> keyValuePairs,
            StreamingResponseHandler<AiMessage> handler) {
        final ConversationRequest request = ConversationRequest.builder()
                .appId(appId)
                .conversationId(conversationId)
                .keyValuePairs(keyValuePairs)
                .build();
        final ConversationId reqId = client.generate(request);
        this.generate(reqId, handler);
    }

    public void generate(ConversationId request, StreamingResponseHandler<AiMessage> handler) {
        client.sseInvoke(request, handler);
    }

    /**
     * 推荐问题
     *
     * @param conversationId 会话ID
     * @return Problems
     */
    public Problems sessionRecord(String conversationId) {
        return client.sessionRecord(appId, conversationId);
    }

    public AssistantKeyValuePair initMessage(String content) {
        AssistantKeyValuePair keyValuePair = new AssistantKeyValuePair();
        keyValuePair.setId("user");
        keyValuePair.setName("用户提问");
        keyValuePair.setType(AssistantType.INPUT.serialize());
        keyValuePair.setValue(content);
        return keyValuePair;
    }

    public static class ZhipuAssistantChatModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String appId;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAssistantChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAssistantChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAssistantChatModelBuilder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public ZhipuAssistantChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAssistantChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAssistantChatModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAssistantChatModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAssistantChatModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public ZhipuAssistantChatModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiAssistant build() {
            return new ZhipuAiAssistant(
                    this.baseUrl,
                    this.apiKey,
                    this.appId,
                    this.logRequests,
                    this.logResponses,
                    this.callTimeout,
                    this.connectTimeout,
                    this.readTimeout,
                    this.writeTimeout);
        }
    }
}
