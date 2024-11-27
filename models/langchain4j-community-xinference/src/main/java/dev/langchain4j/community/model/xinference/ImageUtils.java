package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.chat.message.ImageUrl;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.internal.Utils;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

class ImageUtils {

    private static final List<String> SUPPORTED_URL_SCHEMES = Arrays.asList("http", "https", "file");

    static byte[] imageByteArray(Image image) {
        if (image.base64Data() != null && !image.base64Data().isBlank()) {
            return Base64.getDecoder().decode(image.base64Data());
        } else {
            if (SUPPORTED_URL_SCHEMES.contains(image.url().getScheme())) {
                return Utils.readBytes(image.url().toString());
            } else {
                throw new RuntimeException("only supports http/https and file urls. unsupported url scheme: " + image.url().getScheme());
            }
        }
    }

    static ImageUrl base64Image(Image image, String detailLevel) {
        String url = "";
        if (Objects.nonNull(image.url())) {
            url = image.url().toString();
        } else {
            url = String.format("data:%s;base64,%s", image.mimeType(), image.base64Data());
        }
        return ImageUrl.of(url, ImageUrl.ImageDetail.valueOf(detailLevel));
    }
}
