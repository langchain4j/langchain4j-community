package dev.langchain4j.community.web.search.searxng;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SearXNGResult {
	private String title;
	private String content;
	private String url;
	private String engine;
	private List<String> parsedUrl;
	private String template;
	private List<String> engines;
	private List<Integer> positions;
	private double score;
	private String category;
	
	public String getTitle() {
		return title;
	}
	
	public String getContent() {
		return content;
	}
	
	public String getUrl() {
		return url;
	}
	
	public String getEngine() {
		return engine;
	}
	
	public List<String> getParsedUrl() {
		return parsedUrl;
	}
	
	public String getTemplate() {
		return template;
	}
	
	public List<String> getEngines() {
		return engines;
	}
	
	public List<Integer> getPositions() {
		return positions;
	}
	
	public double getScore() {
		return score;
	}
	
	public String getCategory() {
		return category;
	}
}
