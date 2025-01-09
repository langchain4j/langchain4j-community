package dev.langchain4j.community.model.zhipu.assistant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.zhipu.assistant.file.FileData;
import java.util.List;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantKeyValuePair {
    /**
     * 组件id
     */
    private String id;
    /**
     * 组件名称
     */
    private String name;
    /**
     * 变量类型
     * input: 文本输入
     * selection_list: 下拉框
     * upload_file: 文件上传
     * upload_image: 图片上传
     * upload_video: 视频上传
     * input_template: 输入模版
     */
    private String type;
    /**
     * 当组件值 type为Input、selection_list 时必填
     */
    private String value;
    /**
     * 提示词
     */
    private String tips;
    /**
     * 下拉框选项, 当type = selection_list, 会有此值
     */
    private List<String> allowValues;
    /**
     * 当组件type为upload_file, 代表文本文件id;
     * 当组件type为upload_image, 代表图片url;
     * 当组件type为upload_video, 代表视频url;
     */
    private List<String> files;
    /**
     * 图片或视频列表, 对话类智能体（应用）时参数有效
     */
    private List<FileData> ivfiles;
    /**
     * 输入模版, 当type = input_template, 会有此值
     */
    private InputTemplate inputTemplate;
    /**
     * 输入模版的组列表, 至少一组, 最多10组, list最大10
     */
    private List<InputTemplate> inputTemplates;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getTips() {
        return tips;
    }

    public void setTips(final String tips) {
        this.tips = tips;
    }

    public List<String> getAllowValues() {
        return allowValues;
    }

    public void setAllowValues(final List<String> allowValues) {
        this.allowValues = allowValues;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(final List<String> files) {
        this.files = files;
    }

    public List<FileData> getIvfiles() {
        return ivfiles;
    }

    public void setIvfiles(final List<FileData> ivfiles) {
        this.ivfiles = ivfiles;
    }

    public InputTemplate getInputTemplate() {
        return inputTemplate;
    }

    public void setInputTemplate(final InputTemplate inputTemplate) {
        this.inputTemplate = inputTemplate;
    }

    public List<InputTemplate> getInputTemplates() {
        return inputTemplates;
    }

    public void setInputTemplates(final List<InputTemplate> inputTemplates) {
        this.inputTemplates = inputTemplates;
    }
}
