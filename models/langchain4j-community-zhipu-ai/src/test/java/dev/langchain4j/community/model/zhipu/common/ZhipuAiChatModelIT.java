package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatRequestParameters;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
public class ZhipuAiChatModelIT extends AbstractChatModelIT {

    private static final String ZHIPU_API_KEY = System.getenv("ZHIPU_API_KEY");

    @Override
    protected List<ChatModel> models() {
        return List.of(
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_5)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build(),
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_6)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build(),
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_7)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build(),
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_5)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build(),
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_5_1)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build());
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
        return ChatCompletionModel.GLM_5.toString();
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        ZhipuAiChatModel.ZhipuAiChatModelBuilder zhipuAiChatModelBuilder = ZhipuAiChatModel.builder()
                .apiKey(ZHIPU_API_KEY)
                .thinking(Thinking.builder().type("disabled").build())
                .model(parameters.modelName())
                .maxToken(parameters.maxOutputTokens())
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            zhipuAiChatModelBuilder.model(ChatCompletionModel.GLM_5);
        }
        return zhipuAiChatModelBuilder.build();
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_4_6V)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build(),
                ZhipuAiChatModel.builder()
                        .model(ChatCompletionModel.GLM_5V_TURBO)
                        .apiKey(ZHIPU_API_KEY)
                        .logRequests(true)
                        .logResponses(true)
                        .thinking(Thinking.builder().type("disabled").build())
                        .maxRetries(1)
                        .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ZhipuAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected String catImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    }

    @Override
    protected String diceImageUrl() {
        return "https://cdn.wanx.aliyuncs.com/upload/commons/PNG_transparency_demonstration_1.png";
    }
}
