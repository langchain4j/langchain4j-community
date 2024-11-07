package dev.langchain4j.community.web.search.searxng;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a SearXNG instance with its API enabled as a {@code WebSearchEngine}.
 */
public class SearXNGWebSearchEngine implements WebSearchEngine {
	private final SearXNGClient client;

	private SearXNGWebSearchEngine(Builder builder) {
		ensureNotNull(builder.baseUrl, "baseUrl");
		this.client = new SearXNGClient(builder.baseUrl, getOrDefault(builder.duration, Duration.ofSeconds(10L)), builder.optionalParams);
	}
	
	/**
	 * builder for a new SearXNG instance
	 * @return {@link Builder}
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	private static String toCSV(List<?> values) {
		if (values == null) {
			return null;
		}
		return String.join(",", values.toString());
	}
	
	private static Map<String, String> extractMetadata(SearXNGResult result) {
		final Map<String, String> metadata = new HashMap<>();
		metadata.put("engine", result.getEngine());
		metadata.put("engines", toCSV(result.getEngines()));
		metadata.put("score", Double.toString(result.getScore()));
		metadata.put("category", result.getCategory());
		metadata.put("positions", toCSV(result.getPositions()));
		return metadata;
	}
	
	private static WebSearchOrganicResult toWebSearchOrganicResult(SearXNGResult result) {
		return WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getUrl()), result.getContent(), null, extractMetadata(result));
	}
	
	private static boolean hasValue(String value) {
		return value != null && !value.trim().isEmpty();
	}
	
	private static boolean includeResult(SearXNGResult result) {
		return hasValue(result.getTitle()) && hasValue(result.getUrl()) && hasValue(result.getContent());
	}
	
	private static int maxResults(WebSearchRequest webSearchRequest) {
		return webSearchRequest.maxResults() != null ? webSearchRequest.maxResults() : Integer.MAX_VALUE;
	}
	
	@Override
	public WebSearchResults search(WebSearchRequest webSearchRequest) {
		final SearXNGResponse results = client.search(webSearchRequest);
		
		return WebSearchResults.from(WebSearchInformationResult.from((long) results.getNumberOfResults()),
				results.getResults().stream().filter(r -> includeResult(r)).map(r -> toWebSearchOrganicResult(r)).limit(maxResults(webSearchRequest)).collect(Collectors.toList()));
	}
	
	/**
	 *  <p>{@summary Builder for new instances of
	 *  {@link SearXNGWebSearchEngine}.}</p>
	 */  
	public static class Builder {
		private String baseUrl;
		private Duration duration;
		private Map<String, Object> optionalParams;
		
		/**
		 * @param baseUrl base URL of the SearXNG instance e.g. http://localhost:8080
		 * @return {@link Builder}
		 */
		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}
		
		/**
		 * @param duration connection timeout specified as a {@link Duration} 
		 * @return {@link Builder}
		 */
		public Builder duration(Duration duration) {
			this.duration = duration;
			return this;
		}

		/**
		 * @param optionalParams any optional parameters to be passed in on all requests
		 * @return {@link Builder}
		 */
		public Builder optionalParams(Map<String, Object> optionalParams) {
			this.optionalParams = copyIfNotNull(optionalParams);
			return this;
		}

		/**
		 *  Creates a new instance of
		 *  {@link SearXNGWebSearchEngine}.
		 *
		 *  @return The new instance.
		 */
		public SearXNGWebSearchEngine build() {
			return new SearXNGWebSearchEngine(this);
		}
	}
}
