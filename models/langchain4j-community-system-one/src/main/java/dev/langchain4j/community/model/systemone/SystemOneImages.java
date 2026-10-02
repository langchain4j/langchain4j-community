package dev.langchain4j.community.model.systemone;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

final class SystemOneImages {
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private SystemOneImages() {}

    static List<InlineImage> extract(List<Content> contents, int maxImages) {
        if (contents.size() > maxImages) {
            throw new UnsupportedFeatureException("Too many images: " + contents.size());
        }
        List<InlineImage> images = new ArrayList<>();
        for (Content content : contents) {
            if (!(content instanceof ImageContent image)) {
                throw new UnsupportedFeatureException("Only inline image content is supported");
            }
            String mimeType = image.image().mimeType();
            String data = image.image().base64Data();
            if (!SUPPORTED_MIME_TYPES.contains(mimeType) || data == null) {
                throw new UnsupportedFeatureException("Only inline PNG, JPEG, or WebP images are supported");
            }
            try {
                Base64.getDecoder().decode(data);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 image data", e);
            }
            images.add(new InlineImage(mimeType, data));
        }
        return images;
    }

    record InlineImage(String mimeType, String base64) {
        String dataUrl() {
            return "data:" + mimeType + ";base64," + base64;
        }

        byte[] bytes() {
            return Base64.getDecoder().decode(base64);
        }

        String extension() {
            return mimeType.substring("image/".length()).replace("jpeg", "jpg");
        }
    }
}
