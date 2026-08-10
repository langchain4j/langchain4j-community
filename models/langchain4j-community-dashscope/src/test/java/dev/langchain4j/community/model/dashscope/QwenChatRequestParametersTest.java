package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.QWEN_MAX;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QwenChatRequestParametersTest {

    @Test
    void should_override_when_using_qwen_chat_request_parameters() {
        // given
        QwenChatRequestParameters original = QwenChatRequestParameters.builder()
                .modelName(QWEN_MAX)
                .temperature(0.7)
                .maxOutputTokens(500)
                .seed(42)
                .enableSearch(false)
                .topK(30)
                .frequencyPenalty(0.1)
                .presencePenalty(0.1)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                .isMultimodalModel(false)
                .supportIncrementalOutput(false)
                .vlHighResolutionImages(true)
                .enableThinking(false)
                .thinkingBudget(500)
                .enableSanitizeMessages(false)
                .n(2)
                .size("512*512")
                .promptExtend(false)
                .negativePrompt("blurry")
                .parallelToolCalls(false)
                .enableCodeInterpreter(false)
                .strictJsonSchema(false)
                .custom(Map.of("key1", "value1"))
                .build();

        QwenChatRequestParameters override = QwenChatRequestParameters.builder()
                .seed(50)
                .enableSearch(true)
                .topK(50)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .toolChoice(ToolChoice.REQUIRED)
                .responseFormat(ResponseFormat.JSON)
                .isMultimodalModel(true)
                .supportIncrementalOutput(true)
                .vlHighResolutionImages(false)
                .enableThinking(true)
                .thinkingBudget(1000)
                .enableSanitizeMessages(true)
                .n(1)
                .size("1024*1024")
                .promptExtend(true)
                .negativePrompt("disfigured")
                .parallelToolCalls(true)
                .enableCodeInterpreter(true)
                .strictJsonSchema(true)
                .custom(Map.of("key2", "value2"))
                .searchOptions(QwenChatRequestParameters.SearchOptions.builder()
                        .citationFormat("[<number>]")
                        .enableCitation(true)
                        .enableSource(true)
                        .forcedSearch(true)
                        .searchStrategy("standard")
                        .build())
                .asrOptions(QwenChatRequestParameters.AsrOptions.builder()
                        .language("zh")
                        .enableItn(true)
                        .build())
                .ttsOptions(QwenChatRequestParameters.TtsOptions.builder()
                        .voice("Cherry")
                        .languageType("Chinese")
                        .instructions("Speak slowly")
                        .optimizeInstructions(true)
                        .build())
                .translationOptions(QwenChatRequestParameters.TranslationOptions.builder()
                        .sourceLang("English")
                        .targetLang("Chinese")
                        .domains("The sentence is from Ali Cloud IT domain.")
                        .terms(List.of(QwenChatRequestParameters.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .tmLists(List.of(QwenChatRequestParameters.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .build())
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(QwenChatRequestParameters.class);
        QwenChatRequestParameters qwenResult = (QwenChatRequestParameters) result;

        // original fields preserved
        assertThat(qwenResult.modelName()).isEqualTo(QWEN_MAX);
        assertThat(qwenResult.temperature()).isEqualTo(0.7);
        assertThat(qwenResult.maxOutputTokens()).isEqualTo(500);

        // overridden fields
        assertThat(qwenResult.seed()).isEqualTo(50);
        assertThat(qwenResult.enableSearch()).isTrue();
        assertThat(qwenResult.topK()).isEqualTo(50);
        assertThat(qwenResult.frequencyPenalty()).isEqualTo(0.5);
        assertThat(qwenResult.presencePenalty()).isEqualTo(0.3);
        assertThat(qwenResult.toolChoice()).isEqualTo(ToolChoice.REQUIRED);
        assertThat(qwenResult.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(qwenResult.isMultimodalModel()).isTrue();
        assertThat(qwenResult.supportIncrementalOutput()).isTrue();
        assertThat(qwenResult.vlHighResolutionImages()).isFalse();
        assertThat(qwenResult.enableThinking()).isTrue();
        assertThat(qwenResult.thinkingBudget()).isEqualTo(1000);
        assertThat(qwenResult.enableSanitizeMessages()).isTrue();
        assertThat(qwenResult.n()).isEqualTo(1);
        assertThat(qwenResult.size()).isEqualTo("1024*1024");
        assertThat(qwenResult.promptExtend()).isTrue();
        assertThat(qwenResult.negativePrompt()).isEqualTo("disfigured");
        assertThat(qwenResult.parallelToolCalls()).isTrue();
        assertThat(qwenResult.enableCodeInterpreter()).isTrue();
        assertThat(qwenResult.strictJsonSchema()).isTrue();
        assertThat(qwenResult.custom()).containsEntry("key2", "value2");

        // searchOptions
        assertThat(qwenResult.searchOptions()).isNotNull();
        assertThat(qwenResult.searchOptions().citationFormat()).isEqualTo("[<number>]");
        assertThat(qwenResult.searchOptions().enableCitation()).isTrue();
        assertThat(qwenResult.searchOptions().enableSource()).isTrue();
        assertThat(qwenResult.searchOptions().forcedSearch()).isTrue();
        assertThat(qwenResult.searchOptions().searchStrategy()).isEqualTo("standard");

        // asrOptions
        assertThat(qwenResult.asrOptions()).isNotNull();
        assertThat(qwenResult.asrOptions().language()).isEqualTo("zh");
        assertThat(qwenResult.asrOptions().enableItn()).isTrue();

        // ttsOptions
        assertThat(qwenResult.ttsOptions()).isNotNull();
        assertThat(qwenResult.ttsOptions().voice()).isEqualTo("Cherry");
        assertThat(qwenResult.ttsOptions().languageType()).isEqualTo("Chinese");
        assertThat(qwenResult.ttsOptions().instructions()).isEqualTo("Speak slowly");
        assertThat(qwenResult.ttsOptions().optimizeInstructions()).isTrue();

        // translationOptions
        assertThat(qwenResult.translationOptions()).isNotNull();
        assertThat(qwenResult.translationOptions().sourceLang()).isEqualTo("English");
        assertThat(qwenResult.translationOptions().targetLang()).isEqualTo("Chinese");
        assertThat(qwenResult.translationOptions().domains()).isEqualTo("The sentence is from Ali Cloud IT domain.");
        assertThat(qwenResult.translationOptions().terms()).hasSize(1);
        assertThat(qwenResult.translationOptions().terms().get(0).source()).isEqualTo("memory");
        assertThat(qwenResult.translationOptions().terms().get(0).target()).isEqualTo("内存");
        assertThat(qwenResult.translationOptions().tmList()).hasSize(1);
        assertThat(qwenResult.translationOptions().tmList().get(0).source()).isEqualTo("memory");
        assertThat(qwenResult.translationOptions().tmList().get(0).target()).isEqualTo("内存");
    }

    @Test
    void shouldPreserveQwenSpecificFieldsWhenDefaultedBy() {
        QwenChatRequestParameters current = QwenChatRequestParameters.builder()
                .modelName("qwen-plus")
                .temperature(0.8)
                .topP(0.9)
                .maxOutputTokens(4096)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .stopSequences(List.of("stop1", "stop2"))
                .toolSpecifications(List.of(ToolSpecification.builder()
                        .name("testTool")
                        .description("test tool")
                        .build()))
                .toolChoice(ToolChoice.REQUIRED)
                .responseFormat(ResponseFormat.JSON)
                // qwen
                .seed(12345)
                .enableSearch(true)
                .vlHighResolutionImages(true)
                .isMultimodalModel(true)
                .supportIncrementalOutput(true)
                .enableThinking(true)
                .thinkingBudget(2048)
                .enableSanitizeMessages(false)
                .n(3)
                .size("1024*1024")
                .promptExtend(true)
                .negativePrompt("blurry, low quality")
                .parallelToolCalls(true)
                .enableCodeInterpreter(true)
                .strictJsonSchema(true)
                .custom(Map.of("customKey", "customValue"))
                .searchOptions(QwenChatRequestParameters.SearchOptions.builder()
                        .searchStrategy("standard")
                        .build())
                .asrOptions(QwenChatRequestParameters.AsrOptions.builder()
                        .language("zh")
                        .build())
                .ttsOptions(QwenChatRequestParameters.TtsOptions.builder()
                        .voice("Cherry")
                        .languageType("Chinese")
                        .instructions("Speak slowly")
                        .optimizeInstructions(true)
                        .build())
                .translationOptions(QwenChatRequestParameters.TranslationOptions.builder()
                        .sourceLang("English")
                        .targetLang("Chinese")
                        .domains("The sentence is from Ali Cloud IT domain.")
                        .terms(List.of(
                                QwenChatRequestParameters.TranslationOptionTerm.builder()
                                        .source("商品")
                                        .target("product")
                                        .build()
                        ))
                        .tmLists(List.of(
                                QwenChatRequestParameters.TranslationOptionTerm.builder()
                                        .source("下单")
                                        .target("place order")
                                        .build()
                        ))
                        .build())
                .build();

        // default parameter
        QwenChatRequestParameters defaults = QwenChatRequestParameters.builder()
                .modelName("qwen-turbo-latest")
                .temperature(0.2)
                .topP(0.3)
                .maxOutputTokens(8192)
                .frequencyPenalty(0.8)
                .presencePenalty(0.6)
                .stopSequences(List.of("defaultStop", "end", "finish"))
                .toolSpecifications(List.of(
                        ToolSpecification.builder()
                                .name("defaultTool")
                                .description("default tool for testing")
                                .build(),
                        ToolSpecification.builder()
                                .name("fallbackTool")
                                .description("fallback tool")
                                .build()
                ))
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                // qwen
                .seed(88888)
                .enableSearch(false)
                .vlHighResolutionImages(false)
                .isMultimodalModel(false)
                .supportIncrementalOutput(false)
                .enableThinking(false)
                .thinkingBudget(4096)
                .enableSanitizeMessages(true)
                .n(5)
                .size("2048*2048")
                .promptExtend(false)
                .negativePrompt("low resolution, error, worst quality, disfigured")
                .parallelToolCalls(false)
                .enableCodeInterpreter(false)
                .strictJsonSchema(false)
                .custom(Map.of(
                        "defaultKey1", "defaultValue1",
                        "defaultKey2", "defaultValue2"
                ))
                .searchOptions(QwenChatRequestParameters.SearchOptions.builder()
                        .searchStrategy("advanced")
                        .enableCitation(true)
                        .build())
                .asrOptions(QwenChatRequestParameters.AsrOptions.builder()
                        .language("en")
                        .enableItn(true)
                        .build())
                .ttsOptions(QwenChatRequestParameters.TtsOptions.builder()
                        .voice("Emily")
                        .languageType("English")
                        .instructions("Speak naturally")
                        .optimizeInstructions(false)
                        .build())
                .translationOptions(QwenChatRequestParameters.TranslationOptions.builder()
                        .sourceLang("Chinese")
                        .targetLang("English")
                        .domains("The sentence is from e-commerce domain.")

                        .terms(List.of(QwenChatRequestParameters.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .tmLists(List.of(QwenChatRequestParameters.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .build())
                .build();

        // execute defaultedBy
        ChatRequestParameters merged = current.defaultedBy(defaults);

        // assert class is QwenChatRequestParameters
        assertThat(merged).isInstanceOf(QwenChatRequestParameters.class);
        QwenChatRequestParameters qwenMerged = (QwenChatRequestParameters) merged;

        // assert value of Default-fields
        assertThat(qwenMerged.modelName()).isEqualTo("qwen-plus");
        assertThat(qwenMerged.temperature()).isEqualTo(0.8);
        assertThat(qwenMerged.topP()).isEqualTo(0.9);
        assertThat(qwenMerged.maxOutputTokens()).isEqualTo(4096);
        assertThat(qwenMerged.frequencyPenalty()).isEqualTo(0.5);
        assertThat(qwenMerged.presencePenalty()).isEqualTo(0.3);
        assertThat(qwenMerged.stopSequences()).containsExactly("stop1", "stop2");
        assertThat(qwenMerged.toolSpecifications()).hasSize(1);
        assertThat(qwenMerged.toolSpecifications().get(0).name()).isEqualTo("testTool");
        assertThat(qwenMerged.toolSpecifications().get(0).description()).isEqualTo("test tool");
        assertThat(qwenMerged.toolChoice()).isEqualTo(ToolChoice.REQUIRED);
        assertThat(qwenMerged.responseFormat()).isEqualTo(ResponseFormat.JSON);

        // assert value of Qwen-special fields
        assertThat(qwenMerged.seed()).isEqualTo(12345);
        assertThat(qwenMerged.enableSearch()).isTrue();
        assertThat(qwenMerged.vlHighResolutionImages()).isTrue();
        assertThat(qwenMerged.isMultimodalModel()).isTrue();
        assertThat(qwenMerged.supportIncrementalOutput()).isTrue();
        assertThat(qwenMerged.enableThinking()).isTrue();
        assertThat(qwenMerged.thinkingBudget()).isEqualTo(2048);
        assertThat(qwenMerged.enableSanitizeMessages()).isFalse();
        assertThat(qwenMerged.n()).isEqualTo(3);
        assertThat(qwenMerged.size()).isEqualTo("1024*1024");
        assertThat(qwenMerged.promptExtend()).isTrue();
        assertThat(qwenMerged.negativePrompt()).isEqualTo("blurry, low quality");
        assertThat(qwenMerged.parallelToolCalls()).isTrue();
        assertThat(qwenMerged.enableCodeInterpreter()).isTrue();
        assertThat(qwenMerged.strictJsonSchema()).isTrue();
        assertThat(qwenMerged.custom()).containsEntry("customKey", "customValue");

        assertThat(qwenMerged.searchOptions()).isNotNull();
        assertThat(qwenMerged.searchOptions().searchStrategy()).isEqualTo("standard");

        assertThat(qwenMerged.asrOptions()).isNotNull();
        assertThat(qwenMerged.asrOptions().language()).isEqualTo("zh");

        assertThat(qwenMerged.ttsOptions()).isNotNull();
        assertThat(qwenMerged.ttsOptions().voice()).isEqualTo("Cherry");
        assertThat(qwenMerged.ttsOptions().languageType()).isEqualTo("Chinese");
        assertThat(qwenMerged.ttsOptions().instructions()).isEqualTo("Speak slowly");
        assertThat(qwenMerged.ttsOptions().optimizeInstructions()).isTrue();

        assertThat(qwenMerged.translationOptions()).isNotNull();
        assertThat(qwenMerged.translationOptions().sourceLang()).isEqualTo("English");
        assertThat(qwenMerged.translationOptions().targetLang()).isEqualTo("Chinese");
        assertThat(qwenMerged.translationOptions().domains()).isEqualTo("The sentence is from Ali Cloud IT domain.");
        assertThat(qwenMerged.translationOptions().terms().get(0).source()).isEqualTo("商品");
        assertThat(qwenMerged.translationOptions().terms().get(0).target()).isEqualTo("product");
        assertThat(qwenMerged.translationOptions().tmList().get(0).source()).isEqualTo("下单");
        assertThat(qwenMerged.translationOptions().tmList().get(0).target()).isEqualTo("place order");
    }
}
