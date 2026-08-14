package dev.langchain4j.community.model.zhipu.image;

public enum ImageModelName {
    GLM_IMAGE("glm-image"),
    COGVIEW_4("cogview-4"),
    COGVIEW_3("cogview-3"),
    COGVIEW_3_FLASH("cogview-3-flash"),
    ;

    private final String value;

    ImageModelName(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
