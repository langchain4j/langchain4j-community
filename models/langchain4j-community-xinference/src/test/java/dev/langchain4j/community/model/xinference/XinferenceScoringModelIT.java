package dev.langchain4j.community.model.xinference;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class XinferenceScoringModelIT extends AbstractXinferenceScoringModelInfrastructure {

    ScoringModel model = XinferenceScoringModel.builder()
            .baseUrl(baseUrl())
            .apiKey(apiKey())
            .modelName(modelName())
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_score_single_text() {
        String text =
                "北京市（Beijing），简称“京”，古称燕京、北平，是中华人民共和国首都、直辖市、国家中心城市、超大城市， [185]国务院批复确定的中国政治中心、文化中心、国际交往中心、科技创新中心， [1]中国历史文化名城和古都之一，世界一线城市。 [3] [142] [188]截至2023年10月，北京市下辖16个区，总面积16410.54平方千米。 [82] [193] [195]2023年末，北京市常住人口2185.8万人。 [214-215]";
        String query = "中国首都是哪座城市";
        Response<Double> response = model.score(text, query);
        assertThat(response.content()).isCloseTo(0.661, withPercentage(3));
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {
        TextSegment segment1 = TextSegment.from(
                "上海市（Shanghai），简称“沪”，别名“申”，是中华人民共和国直辖市， [38]位于中国东部，地处长江入海口， [175]境域北界长江，东濒东海，南临杭州湾，西接江苏省和浙江省，总面积6340.5平方千米， [38]下辖16个区。 [37]截至2022年末，全市常住人口2475.89万人， [204]上海话属吴语方言太湖片。 [159]市政府驻地上海市黄浦区人民大道200号。 [173]");
        TextSegment segment2 = TextSegment.from(
                "北京市（Beijing），简称“京”，古称燕京、北平，是中华人民共和国首都、直辖市、国家中心城市、超大城市， [185]国务院批复确定的中国政治中心、文化中心、国际交往中心、科技创新中心， [1]中国历史文化名城和古都之一，世界一线城市。 [3] [142] [188]截至2023年10月，北京市下辖16个区，总面积16410.54平方千米。 [82] [193] [195]2023年末，北京市常住人口2185.8万人。 [214-215]");
        List<TextSegment> segments = asList(segment1, segment2);
        String query = "中国首都是哪座城市";
        Response<List<Double>> response = model.scoreAll(segments, query);
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        assertThat(response.finishReason()).isNull();
    }
}
