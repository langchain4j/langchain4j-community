package dev.langchain4j.community.web.search.searxng;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

/**
 * Represents a SearXNG instance with its API enabled as a {@code WebSearchEngine}.
 */
public class SearXNGWebSearchEngine implements WebSearchEngine {
	private SearXNGClient client;

	/**
	 * @param baseUrl base URL of the SearXNG instance e.g. http://localhost:8080
	 */
	public SearXNGWebSearchEngine(String baseUrl) {
		this(baseUrl, Duration.ofSeconds(10L));
	}

	/**
	 * @param baseUrl base URL of the SearXNG instance e.g. http://localhost:8080
	 * @param timeout connection timeout duration
	 */
	public SearXNGWebSearchEngine(String baseUrl, Duration timeout) {
		this.client = new SearXNGClient(baseUrl, timeout);
	}

	private SearXNGWebSearchEngine(Builder builder) {
		this(builder.baseUrl, builder.duration != null ? builder.duration : Duration.ofSeconds(10L));
	}
	
	/**
	 * @param baseUrl base URL of the SearXNG instance e.g. http://localhost:8080
	 * @return {@link Builder}
	 */
	public static Builder builder(String baseUrl) {
		return new Builder(baseUrl);
	}
	
	private static WebSearchOrganicResult toWebSearchOrganicResult(SearXNGResult result) {
		return WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getUrl()), result.getContent(), null);
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
		final SearXNGResults results = client.search(webSearchRequest);
		
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
		
		/**
		 * @param baseUrl base URL of the SearXNG instance e.g. http://localhost:8080
		 */
		public Builder(String baseUrl) {
			this.baseUrl = baseUrl;
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
