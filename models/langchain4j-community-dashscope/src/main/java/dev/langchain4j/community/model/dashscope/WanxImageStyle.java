package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public enum WanxImageStyle {

    PHOTOGRAPHY("<photography>"),
    PORTRAIT("<portrait>"),
    CARTOON_3D("<3d cartoon>"),
    ANIME("<anime>"),
    OIL_PAINTING("<oil painting>"),
    WATERCOLOR("<watercolor>"),
    SKETCH("<sketch>"),
    CHINESE_PAINTING("<chinese painting>"),
    FLAT_ILLUSTRATION("<flat illustration>"),
    AUTO("<auto>");

    private final String style;

    WanxImageStyle(String style) {
        this.style = style;
    }

    @Override
    public String toString() {
        return style;
    }

    public String getStyle() {
        return style;
    }

    public static WanxImageStyle of(String style) {
        if (isNullOrBlank(style)) {
            return null;
        }

        for (WanxImageStyle imageStyle : values()) {
            if (imageStyle.toString().equalsIgnoreCase(style)) {
                return imageStyle;
            }
        }

        return null;
    }
}
