package dev.langchain4j.community.web.search.searxng;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SearXNGResponse {

    private String query;
    private long numberOfResults;
    private List<SearXNGResult> results;
    private List<Answer> answers;
    private List<String> corrections;
    private List<String> suggestions;
    private List<List<String>> unresponsiveEngines;
    // Skipping other returned fields like infoboxes for now

    public String getQuery() {
        return query;
    }

    public long getNumberOfResults() {
        return numberOfResults;
    }

    public List<SearXNGResult> getResults() {
        return results;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public List<String> getCorrections() {
        return corrections;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public List<List<String>> getUnresponsiveEngines() {
        return unresponsiveEngines;
    }

    @JsonNaming(SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Answer {

        private String url;
        private String template;
        private String engine;
        private List<String> parsedUrl;
        private String answer;

        public String getUrl() {
            return url;
        }

        public String getTemplate() {
            return template;
        }

        public String getEngine() {
            return engine;
        }

        public List<String> getParsedUrl() {
            return parsedUrl;
        }

        public String getAnswer() {
            return answer;
        }
    }
}
