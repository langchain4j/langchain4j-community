package dev.langchain4j.community.model.qianfan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.List;
import org.junit.jupiter.api.Test;

class QianfanChatRequestParametersTest {

    @Test
    void should_override_when_using_qianfan_chat_request_parameters() {
        // given
        QianfanChatRequestParameters original = QianfanChatRequestParameters.builder()
                .modelName("ERNIE-4.0-8K")
                .temperature(0.7)
                .maxOutputTokens(500)
                .topP(0.9)
                .presencePenalty(0.1)
                .toolChoice(ToolChoice.AUTO)
                .responseFormat(ResponseFormat.TEXT)
                .userId("user-1")
                .endpoint("endpoint-1")
                .system("system-1")
                .build();

        QianfanChatRequestParameters override = QianfanChatRequestParameters.builder()
                .temperature(0.3)
                .maxOutputTokens(1000)
                .userId("user-2")
                .endpoint("endpoint-2")
                .system("system-2")
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(QianfanChatRequestParameters.class);
        QianfanChatRequestParameters qianfanResult = (QianfanChatRequestParameters) result;

        // overridden common fields
        assertThat(qianfanResult.temperature()).isEqualTo(0.3);
        assertThat(qianfanResult.maxOutputTokens()).isEqualTo(1000);

        // preserved original fields
        assertThat(qianfanResult.modelName()).isEqualTo("ERNIE-4.0-8K");
        assertThat(qianfanResult.topP()).isEqualTo(0.9);
        assertThat(qianfanResult.presencePenalty()).isEqualTo(0.1);

        // overridden Qianfan-specific fields
        assertThat(qianfanResult.userId()).isEqualTo("user-2");
        assertThat(qianfanResult.endpoint()).isEqualTo("endpoint-2");
        assertThat(qianfanResult.system()).isEqualTo("system-2");
    }

    @Test
    void should_preserve_qianfan_specific_fields_when_not_overridden() {
        // given
        QianfanChatRequestParameters original = QianfanChatRequestParameters.builder()
                .modelName("ERNIE-4.0-8K")
                .userId("user-1")
                .endpoint("endpoint-1")
                .system("system-1")
                .build();

        QianfanChatRequestParameters override =
                QianfanChatRequestParameters.builder().temperature(0.5).build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        QianfanChatRequestParameters qianfanResult = (QianfanChatRequestParameters) result;
        assertThat(qianfanResult.userId()).isEqualTo("user-1");
        assertThat(qianfanResult.endpoint()).isEqualTo("endpoint-1");
        assertThat(qianfanResult.system()).isEqualTo("system-1");
        assertThat(qianfanResult.temperature()).isEqualTo(0.5);
    }

    @Test
    void should_retain_qianfan_specific_fields_when_defaulted_by_generic_parameters() {
        // given
        QianfanChatRequestParameters parameters = QianfanChatRequestParameters.builder()
                .modelName("receiver-model")
                .userId("user-1")
                .endpoint("endpoint-1")
                .system("system-1")
                .build();
        ChatRequestParameters defaults = DefaultChatRequestParameters.builder()
                .modelName("default-model")
                .temperature(0.4)
                .maxOutputTokens(512)
                .build();

        // when
        ChatRequestParameters result = parameters.defaultedBy(defaults);

        // then
        assertThat(result).isInstanceOf(QianfanChatRequestParameters.class);
        QianfanChatRequestParameters qianfanResult = (QianfanChatRequestParameters) result;
        assertThat(qianfanResult.modelName()).isEqualTo("receiver-model");
        assertThat(qianfanResult.temperature()).isEqualTo(0.4);
        assertThat(qianfanResult.maxOutputTokens()).isEqualTo(512);
        assertThat(qianfanResult.userId()).isEqualTo("user-1");
        assertThat(qianfanResult.endpoint()).isEqualTo("endpoint-1");
        assertThat(qianfanResult.system()).isEqualTo("system-1");
    }

    @Test
    void should_default_missing_qianfan_fields_from_qianfan_parameters() {
        // given
        QianfanChatRequestParameters parameters = QianfanChatRequestParameters.builder()
                .modelName("receiver-model")
                .temperature(0.7)
                .userId("user-1")
                .build();
        QianfanChatRequestParameters defaults = QianfanChatRequestParameters.builder()
                .modelName("default-model")
                .temperature(0.4)
                .maxOutputTokens(512)
                .userId("default-user")
                .endpoint("default-endpoint")
                .system("default-system")
                .build();

        // when
        ChatRequestParameters result = parameters.defaultedBy(defaults);

        // then
        assertThat(result).isInstanceOf(QianfanChatRequestParameters.class);
        QianfanChatRequestParameters qianfanResult = (QianfanChatRequestParameters) result;
        assertThat(qianfanResult.modelName()).isEqualTo("receiver-model");
        assertThat(qianfanResult.temperature()).isEqualTo(0.7);
        assertThat(qianfanResult.maxOutputTokens()).isEqualTo(512);
        assertThat(qianfanResult.userId()).isEqualTo("user-1");
        assertThat(qianfanResult.endpoint()).isEqualTo("default-endpoint");
        assertThat(qianfanResult.system()).isEqualTo("default-system");
    }

    @Test
    void should_correctly_implement_equals_and_hashcode() {
        QianfanChatRequestParameters params1 = QianfanChatRequestParameters.builder()
                .modelName("ERNIE-4.0-8K")
                .userId("user-1")
                .endpoint("endpoint-1")
                .system("system-1")
                .temperature(0.7)
                .stopSequences(List.of("stop"))
                .build();

        QianfanChatRequestParameters params2 = QianfanChatRequestParameters.builder()
                .modelName("ERNIE-4.0-8K")
                .userId("user-1")
                .endpoint("endpoint-1")
                .system("system-1")
                .temperature(0.7)
                .stopSequences(List.of("stop"))
                .build();

        QianfanChatRequestParameters params3 = QianfanChatRequestParameters.builder()
                .modelName("ERNIE-4.0-8K")
                .userId("user-2")
                .endpoint("endpoint-1")
                .system("system-1")
                .temperature(0.7)
                .stopSequences(List.of("stop"))
                .build();

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
        assertThat(params1).isNotEqualTo(params3);
        assertThat(params1.toString()).contains("userId=\"user-1\"").contains("endpoint=\"endpoint-1\"");
    }
}
