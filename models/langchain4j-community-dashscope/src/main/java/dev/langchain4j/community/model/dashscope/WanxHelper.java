package dev.langchain4j.community.model.dashscope;

import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.OSSUtils;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.internal.Utils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WanxHelper {

    static List<Image> imagesFrom(ImageSynthesisResult result) {
        return Optional.of(result)
                .map(ImageSynthesisResult::getOutput)
                .map(ImageSynthesisOutput::getResults)
                .orElse(Collections.emptyList())
                .stream()
                .map(resultMap -> Image.builder()
                        .url(resultMap.get("url"))
                        .revisedPrompt(resultMap.get("actual_prompt"))
                        .build())
                .collect(toList());
    }

    static String imageUrl(Image image, String model, String apiKey) {
        String imageUrl;

        if (image.url() != null) {
            imageUrl = image.url().toString();
        } else if (Utils.isNotNullOrBlank(image.base64Data())) {
            String filePath = saveDataAsTemporaryFile(image.base64Data(), image.mimeType());
            try {
                imageUrl = OSSUtils.upload(model, filePath, apiKey);
            } catch (NoApiKeyException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            throw new IllegalArgumentException("Failed to get image url from " + image);
        }

        return imageUrl;
    }

    static String saveDataAsTemporaryFile(String base64Data, String mimeType) {
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        String tmpFileName = UUID.randomUUID().toString();
        if (Utils.isNotNullOrBlank(mimeType)) {
            // e.g. "image/png", "image/jpeg"...
            int lastSlashIndex = mimeType.lastIndexOf("/");
            if (lastSlashIndex >= 0 && lastSlashIndex < mimeType.length() - 1) {
                String fileSuffix = mimeType.substring(lastSlashIndex + 1);
                tmpFileName = tmpFileName + "." + fileSuffix;
            }
        }

        Path tmpFilePath = Paths.get(tmpDir, tmpFileName);
        byte[] data = Base64.getDecoder().decode(base64Data);
        try {
            Files.copy(new ByteArrayInputStream(data), tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return tmpFilePath.toAbsolutePath().toString();
    }
}
