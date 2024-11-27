package dev.langchain4j.community.model.dashscope;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

class WanxTestHelper {

    public static Stream<Arguments> imageModelNameProvider() {
        return Stream.of(
                Arguments.of(WanxModelName.WANX_V1)
        );
    }
}
