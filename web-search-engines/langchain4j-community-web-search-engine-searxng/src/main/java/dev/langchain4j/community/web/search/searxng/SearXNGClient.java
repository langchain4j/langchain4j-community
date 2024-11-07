package dev.langchain4j.community.web.search.searxng;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.web.search.WebSearchRequest;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

class SearXNGClient {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
	private SearXNGApi api;

	public SearXNGClient(String baseUrl, Duration timeout) {
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
				.callTimeout(timeout)
				.connectTimeout(timeout)
				.readTimeout(timeout)
				.writeTimeout(timeout);
		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl(baseUrl)
				.client(okHttpClientBuilder.build())
				.addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
				.build();
		this.api = retrofit.create(SearXNGApi.class);
	}

	SearXNGResponse search(WebSearchRequest request) {
		try {
			final Map<String, Object> args = new HashMap<>();
			args.put("q", request.searchTerms());
			args.put("format", "json");
			// Only consider explicit safesearch requests, otherwise keep the default
			if (request.safeSearch() != null) {
				if (request.safeSearch()) {
					// Set to strict as opposed to moderate
					args.put("safesearch", 2);
				}
				else {
					args.put("safesearch", 0);
				}
			}
			if (request.startPage() != null) {
				args.put("pageno", request.startPage());
			}
			if (request.language() != null) {
				args.put("language", request.language());
			}
			final Response<SearXNGResponse> response = api.search(args).execute();
			return response.body();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
