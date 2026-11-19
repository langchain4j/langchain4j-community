package dev.langchain4j.community.web.search.deepseek;

import java.util.List;

class DeepSeekSearchResponse {

    private List<Result> results;

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    static class Result {
        private String title;
        private String url;
        private String snippet;
        private String summary;
        private String content;

        @com.fasterxml.jackson.annotation.JsonProperty("site_name")
        private String siteName;

        @com.fasterxml.jackson.annotation.JsonProperty("site_icon")
        private String siteIcon;

        private String date;

        @com.fasterxml.jackson.annotation.JsonProperty("rerank_score")
        private Float rerankScore;

        private Integer authority;

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getSnippet() {
            return snippet;
        }

        public String getSummary() {
            return summary;
        }

        public String getContent() {
            return content;
        }

        public String getSiteName() {
            return siteName;
        }

        public String getSiteIcon() {
            return siteIcon;
        }

        public String getDate() {
            return date;
        }

        public Float getRerankScore() {
            return rerankScore;
        }

        public Integer getAuthority() {
            return authority;
        }
    }
}
