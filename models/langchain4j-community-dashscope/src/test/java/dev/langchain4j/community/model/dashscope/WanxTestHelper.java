package dev.langchain4j.community.model.dashscope;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

class WanxTestHelper {

    public static Stream<Arguments> imageModelNameProvider() {
        return Stream.of(
                Arguments.of(WanxModelName.WANX_V1),
                Arguments.of(WanxModelName.WANX2_0_T2I_TURBO),
                Arguments.of(WanxModelName.WANX2_1_T2I_TURBO),
                Arguments.of(WanxModelName.WANX2_1_T2I_PLUS));
    }

    // Some parameter features are only supported by v2
    // (v1 will not throw exceptions, but have no effect)
    public static Stream<Arguments> imageModelNameV2Provider() {
        return Stream.of(
                Arguments.of(WanxModelName.WANX2_0_T2I_TURBO),
                Arguments.of(WanxModelName.WANX2_1_T2I_TURBO),
                Arguments.of(WanxModelName.WANX2_1_T2I_PLUS));
    }
}
