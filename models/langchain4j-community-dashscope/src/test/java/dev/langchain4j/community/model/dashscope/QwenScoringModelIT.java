package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenScoringModelIT {

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#scoringModelNameProvider")
    void should_score_single_text(String modelName) {
        // given
        ScoringModel model =
                QwenScoringModel.builder().apiKey(apiKey()).modelName(modelName).build();

        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isNotNull();
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
        assertThat(response.finishReason()).isNull();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#scoringModelNameProvider")
    void should_score_multiple_segments(String modelName) {
        // given
        ScoringModel model =
                QwenScoringModel.builder().apiKey(apiKey()).modelName(modelName).build();

        TextSegment catSegment = TextSegment.from("The Maine Coon is a large domesticated cat breed.");
        TextSegment dogSegment = TextSegment.from(
                "The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds, year after year.");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
        assertThat(response.finishReason()).isNull();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#scoringModelNameProvider")
    void should_respect_top_n(String modelName) {
        // given
        ScoringModel model = QwenScoringModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .topN(1)
                .build();

        TextSegment catSegment = TextSegment.from("The Maine Coon is a large domesticated cat breed.");
        TextSegment dogSegment = TextSegment.from(
                "The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds, year after year.");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
        assertThat(response.finishReason()).isNull();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_DASHSCOPE");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
