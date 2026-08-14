package dev.langchain4j.community.model.zhipu.common;

import static org.mockito.ArgumentMatchers.argThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatRequestParameters;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String ZHIPU_API_KEY = System.getenv("ZHIPU_API_KEY");

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_5)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build(),
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_6)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build(),
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_7)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build(),
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_5)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build(),
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_5_1)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build());
    }

    @Override
    protected boolean supportsPartialToolStreaming(StreamingChatModel model) {
        // Zhipu AI does not support true partial tool streaming -
        // tool arguments are not streamed incrementally, they come all at once
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        // Zhipu does not support response format with schema:
        // https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%AF%B9%E8%AF%9D%E8%A1%A5%E5%85%A8#body-one-of-0-response-format
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        // Zhipu does not support response format with schema:
        // https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%AF%B9%E8%AF%9D%E8%A1%A5%E5%85%A8#body-one-of-0-response-format
        return false;
    }

    @Override
    protected String customModelName() {
        return ChatCompletionModel.GLM_5_1.toString();
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        ZhipuAiStreamingChatModel.ZhipuAiStreamingChatModelBuilder zhipuAiStreamingChatModelBuilder =
                ZhipuAiStreamingChatModel.builder()
                        .apiKey(ZHIPU_API_KEY)
                        .model(parameters.modelName())
                        .maxToken(parameters.maxOutputTokens())
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .logRequests(true)
                        .logResponses(true);
        if (parameters.modelName() == null) {
            zhipuAiStreamingChatModelBuilder.model(ChatCompletionModel.GLM_5);
        }
        return zhipuAiStreamingChatModelBuilder.build();
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingImageInputs() {
        return List.of(
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_6V)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build(),
                ZhipuAiStreamingChatModel.builder()
                        .model(ChatCompletionModel.GLM_5V_TURBO)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .toolStream(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ZhipuAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return ZhipuAiStreamingChatModel.builder()
                .apiKey(ZHIPU_API_KEY)
                .model(ChatCompletionModel.GLM_4_7)
                .toolStream(true)
                .thinking(Thinking.builder().type("disabled").build())
                .listeners(List.of(listener))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected boolean supportsStreamingCancellation() {
        return false;
    }

    @Override
    protected String catImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    }

    @Override
    protected String diceImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/PNG_transparency_demonstration_1.png";
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        // FIXME: verify partial tool call
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        // FIXME: verify partial tool call
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id1)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));

        // FIXME: verify partial tool call
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 1
                    && request.id().equals(id2)
                    && request.name().equals("getTime")
                    && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
        }));
    }

    @Override
    @Disabled("GLM will return text content and tool call at the same time")
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {}

    @Override
    @Disabled("GLM will return text content and tool call at the same time")
    protected void should_execute_a_tool_without_arguments_then_answer(StreamingChatModel model) {}

    @Override
    @Disabled("GLM will return text content and tool call at the same time")
    protected void should_execute_a_tool_then_answer(StreamingChatModel model) {}
}
