package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public enum WanxImageSize {
    SIZE_1024_1024("1024*1024"),
    SIZE_720_1280("720*1280"),
    SIZE_1280_720("1280*720");

    private final String size;

    WanxImageSize(String size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return size;
    }

    public String getSize() {
        return size;
    }

    public static WanxImageSize of(String size) {
        if (isNullOrBlank(size)) {
            return null;
        }

        for (WanxImageSize imageSize : values()) {
            if (imageSize.size.equalsIgnoreCase(size)) {
                return imageSize;
            }
        }

        return null;
    }
}
