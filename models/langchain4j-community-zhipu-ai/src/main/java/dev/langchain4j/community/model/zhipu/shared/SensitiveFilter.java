package dev.langchain4j.community.model.zhipu.shared;

public class SensitiveFilter {

    private String role;
    private Integer level;

    public String getRole() {
        return role;
    }

    public void setRole(final String role) {
        this.role = role;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(final Integer level) {
        this.level = level;
    }
}
