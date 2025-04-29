package dev.langchain4j.community.model.zhipu.common;

import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.Collections;
import java.util.List;

public class ZhipuAiStreamingAiServicesIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return Collections.singletonList(ZhipuAiStreamingChatModel.builder()
                .apiKey(System.getenv("ZHIPU_API_KEY"))
                .model(ChatCompletionModel.GLM_4_FLASH)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
