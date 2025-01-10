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

    private String id;
    private String name;
    private String type;
    private String value;
    private String tips;
    private List<String> allowValues;
    private List<String> files;
    private List<FileData> ivfiles;
    private InputTemplate inputTemplate;
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
