package dev.langchain4j.community.model.zhipu.assistant.file;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 图片或视频信息
 * @author cuiwei
 * @since  2024/11/25
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileData {
    /**
     * 1: 图片
     * 2: 视频
     */
    private Integer type;
    /**
     * 图片或视频url
     */
    private String url;

    public Integer getType() {
        return type;
    }

    public void setType(final Integer type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
