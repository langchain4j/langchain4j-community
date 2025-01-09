package dev.langchain4j.community.model.zhipu.assistant;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * 变量类型
 * input: 文本输入
 * selection_list: 下拉框
 * upload_file: 文件上传
 * upload_image: 图片上传
 * upload_video: 视频上传
 * input_template: 输入模版
 */
public enum AssistantType {
    INPUT,
    SELECTION_LIST,
    UPLOAD_FILE,
    UPLOAD_IMAGE,
    UPLOAD_VIDEO,
    INPUT_TEMPLATE;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
