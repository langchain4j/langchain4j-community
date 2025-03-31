package dev.langchain4j.community.data.document.parser.llamaparse;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlamaParseResponse {

    public String id;
    public String status;
}
