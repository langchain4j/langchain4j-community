package dev.langchain4j.community.model.zhipu.assistant.problem;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * This class represents the Problems data for a specific conversation.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Problems {
    private List<String> problems;

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
