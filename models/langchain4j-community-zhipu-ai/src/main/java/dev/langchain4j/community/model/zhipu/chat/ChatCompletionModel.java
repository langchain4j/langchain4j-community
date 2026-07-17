package dev.langchain4j.community.model.zhipu.chat;

public enum ChatCompletionModel {
    GLM_5_1("glm-5.1"),
    GLM_5("glm-5"),
    GLM_5V_TURBO("glm-5v-turbo"),
    GLM_4_7("glm-4.7"),
    GLM_4_7_FLASH("glm-4.7-flash"),
    GLM_4_6("glm-4.6"),
    GLM_4_6V("glm-4.6v"),
    GLM_4_5("glm-4.5"),
    GLM_4("glm-4"),
    GLM_4V("glm-4v"),
    GLM_4_0520("glm-4-0520"),
    GLM_4_AIR("glm-4-air"),
    GLM_4_AIRX("glm-4-airx"),
    GLM_4_FLASH("glm-4-flash"),
    GLM_3_TURBO("glm-3-turbo"),
    CHATGLM_TURBO("chatglm_turbo");

    private final String value;

    ChatCompletionModel(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
