package dev.langchain4j.model.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.registry.dto.ModelInfo;
import dev.langchain4j.model.registry.dto.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for managing and querying AI model providers and their
 * models.
 */
public class ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRegistry.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_API_URL = "https://models.dev/api.json";
    private final Map<String, Provider> providers;

    private String apiUrl;

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ModelRegistry(Map<String, Provider> providers) {
        this.providers = providers;
        this.apiUrl = DEFAULT_API_URL;
    }

    /**
     * Loads model providers from the default API endpoint.
     * This method fetches the latest model registry data from the default API URL.
     * If loading fails, an empty registry is returned.
     *
     * @return a {@link ModelRegistry} instance containing loaded providers, or an empty registry if loading fails
     */
    public static ModelRegistry loadProvidersFromApi() {
        try {
            return loadProvidersFromApi(DEFAULT_API_URL);
        } catch (Exception e) {
            LOGGER.error("Error occurred while initializing ModelRegistry {}", e);
            return new ModelRegistry(Collections.emptyMap());
        }
    }

    /**
     * Loads model providers from a specified API endpoint.
     *
     * @param apiUrl the API URL to load providers from
     * @return a {@link ModelRegistry} instance containing loaded providers
     * @throws IOException if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public static ModelRegistry loadProvidersFromApi(String apiUrl) throws IOException, InterruptedException {
        return loadProvidersFromApi(apiUrl, null);
    }

    /**
     * Load providers from a custom API endpoint.
     *
     * @param apiUrl the API URL
     * @return ModelRegistry instance
     * @throws IOException          if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public static ModelRegistry loadProvidersFromApi(String apiUrl, HttpClient httpClient)
            throws IOException, InterruptedException {
        HttpClient client = httpClient != null
                ? httpClient
                : HttpClientBuilderLoader.loadHttpClientBuilder().build();

        HttpRequest request =
                HttpRequest.builder().url(apiUrl).method(HttpMethod.GET).build();

        SuccessfulHttpResponse response = client.execute(request);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch API data: HTTP " + response.statusCode());
        }

        ModelRegistry registry = loadProvidersFromJson(response.body());
        registry.apiUrl = apiUrl;
        return registry;
    }

    /**
     * Load providers from a JSON string.
     *
     * @param json the JSON string
     * @return ModelRegistry instance
     * @throws IOException if parsing fails
     */
    public static ModelRegistry loadProvidersFromJson(String json) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(json, Map.class);

        Map<String, Provider> providers = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String providerId = entry.getKey();
            Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
            provider.setId(providerId);
            providers.put(providerId, provider);
        }

        return new ModelRegistry(providers);
    }

    /**
     * Load providers from a classpath resource.
     *
     * @param resourcePath the resource path
     * @return ModelRegistry instance
     * @throws IOException if loading fails
     */
    public static ModelRegistry loadProvidersFromResource(String resourcePath) throws IOException {
        try (InputStream is = ModelRegistry.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(is, Map.class);

            Map<String, Provider> providers = new LinkedHashMap<>();

            for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
                String providerId = entry.getKey();
                Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
                provider.setId(providerId);
                providers.put(providerId, provider);
            }

            return new ModelRegistry(providers);
        }
    }

    // Provider methods
    /**
     * Retrieves a provider by its unique identifier.
     *
     * @param providerId the unique identifier of the provider
     * @return the {@link Provider} with the specified ID, or {@code null} if not found
     */
    public Provider getProvider(String providerId) {
        return providers.get(providerId);
    }

    /**
     * Retrieves all registered model providers.
     *
     * @return a list of all {@link Provider} instances in the registry
     */
    public List<Provider> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    /**
     * Retrieves the unique identifiers of all registered providers.
     *
     * @return a list of provider IDs
     */
    public List<String> getProviderIds() {
        return new ArrayList<>(providers.keySet());
    }

    /**
     * Returns the total number of registered providers.
     *
     * @return the count of providers in the registry
     */
    public int getProviderCount() {
        return providers.size();
    }

    // Model lookup methods

    /**
     * Get a model by provider ID and model ID.
     *
     * @param providerId the provider ID
     * @param modelId    the model ID
     * @return the model, or null if not found
     */
    public ModelInfo getModelInfo(String providerId, String modelId) {
        Provider provider = providers.get(providerId);
        return provider != null ? provider.getModel(modelId) : null;
    }

    /**
     * Get a model by provider and model ID using a single method call. This is a
     * convenience method that combines provider and model lookup.
     *
     * @param provider the provider instance
     * @param modelId  the model ID
     * @return the model, or null if not found
     * @throws IllegalArgumentException if provider is null
     */
    public ModelInfo getModelInfo(Provider provider, String modelId) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        return provider.getModel(modelId);
    }

    /**
     * Retrieves information about all models across all providers.
     *
     * @return a list of all {@link ModelInfo} instances from all providers
     */
    public List<ModelInfo> getAllModelsInfo() {
        return providers.values().stream()
                .flatMap(p -> p.getAllModels().stream())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all models offered by a specific provider.
     *
     * @param providerId the unique identifier of the provider
     * @return a list of {@link ModelInfo} instances for the specified provider, or an empty list if provider not found
     */
    public List<ModelInfo> getModelsByProvider(String providerId) {
        Provider provider = providers.get(providerId);
        return provider != null ? provider.getAllModels() : List.of();
    }

    /**
     * Retrieves all models belonging to a specific model family.
     *
     * @param family the name of the model family (e.g., "gpt-4", "claude-3")
     * @return a list of {@link ModelInfo} instances from the specified family
     */
    public List<ModelInfo> getModelsByFamily(String family) {
        return getAllModelsInfo().stream()
                .filter(m -> family.equals(m.getFamily()))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all models that are available for free (no cost for input or output).
     *
     * @return a list of {@link ModelInfo} instances that are free to use
     */
    public List<ModelInfo> getFreeModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::isFree).collect(Collectors.toList());
    }

    /**
     * Retrieves all models that support reasoning capabilities.
     *
     * @return a list of {@link ModelInfo} instances with reasoning support
     */
    public List<ModelInfo> getReasoningModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::supportsReasoning).collect(Collectors.toList());
    }

    /**
     * Retrieves all models that support multiple modalities (e.g., text, image, audio, video).
     *
     * @return a list of {@link ModelInfo} instances with multimodal capabilities
     */
    public List<ModelInfo> getMultimodalModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::isMultimodal).collect(Collectors.toList());
    }

    /**
     * Retrieves all models that have open-source weights available.
     *
     * @return a list of {@link ModelInfo} instances with open weights
     */
    public List<ModelInfo> getOpenWeightModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::hasOpenWeights).collect(Collectors.toList());
    }

    /**
     * Retrieves all models that support tool/function calling.
     *
     * @return a list of {@link ModelInfo} instances that support tool calls
     */
    public List<ModelInfo> getModelsWithToolCalls() {
        return getAllModelsInfo().stream().filter(ModelInfo::supportsToolCalls).collect(Collectors.toList());
    }

    // Search methods
    /**
     * Searches for models by name or ID using a case-insensitive substring match.
     *
     * @param query the search query string
     * @return a list of {@link ModelInfo} instances matching the query
     */
    public List<ModelInfo> searchByName(String query) {
        String lowerQuery = query.toLowerCase();
        return getAllModelsInfo().stream()
                .filter(m -> m.getName().toLowerCase().contains(lowerQuery)
                        || m.getId().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all models that support at least the specified context size.
     *
     * @param minContextSize the minimum required context size in tokens
     * @return a list of {@link ModelInfo} instances with context size greater than or equal to the specified minimum
     */
    public List<ModelInfo> getModelsWithLargeContext(int minContextSize) {
        return getAllModelsInfo().stream()
                .filter(m -> m.getLimit() != null
                        && m.getLimit().getContext() != null
                        && m.getLimit().getContext() >= minContextSize)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all models with costs at or below the specified thresholds.
     *
     * @param maxInputCost  the maximum acceptable input cost per million tokens
     * @param maxOutputCost the maximum acceptable output cost per million tokens
     * @return a list of {@link ModelInfo} instances within the cost constraints
     */
    public List<ModelInfo> getModelsBelowCost(double maxInputCost, double maxOutputCost) {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null
                        && (m.getCost().getInput() == null || m.getCost().getInput() <= maxInputCost)
                        && (m.getCost().getOutput() == null || m.getCost().getOutput() <= maxOutputCost))
                .collect(Collectors.toList());
    }

    // Statistics methods
    /**
     * Returns the total number of models across all providers.
     *
     * @return the total count of models in the registry
     */
    public int getTotalModelCount() {
        return getAllModelsInfo().size();
    }

    /**
     * Calculates the number of models offered by each provider.
     *
     * @return a map with provider IDs as keys and model counts as values
     */
    public Map<String, Long> getModelCountByProvider() {
        return providers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e ->
                (long) e.getValue().getModelCount()));
    }

    /**
     * Calculates the number of models in each model family.
     *
     * @return a map with family names as keys and model counts as values
     */
    public Map<String, Long> getModelCountByFamily() {
        return getAllModelsInfo().stream().collect(Collectors.groupingBy(ModelInfo::getFamily, Collectors.counting()));
    }

    /**
     * Calculates the average input cost across all models that have cost information.
     *
     * @return the average input cost per million tokens, or 0.0 if no models have cost data
     */
    public double getAverageInputCost() {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null && m.getCost().getInput() != null)
                .mapToDouble(m -> m.getCost().getInput())
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates the average output cost across all models that have cost information.
     *
     * @return the average output cost per million tokens, or 0.0 if no models have cost data
     */
    public double getAverageOutputCost() {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null && m.getCost().getOutput() != null)
                .mapToDouble(m -> m.getCost().getOutput())
                .average()
                .orElse(0.0);
    }

    // Refresh methods

    /**
     * Refresh the model data from the API. This will reload all providers and
     * models from the original API URL.
     *
     * @throws IOException                   if loading fails
     * @throws InterruptedException          if the request is interrupted
     * @throws UnsupportedOperationException if the registry was not loaded from an
     *                                       API
     */
    public void refresh() throws IOException, InterruptedException {
        if (apiUrl == null) {
            throw new UnsupportedOperationException("Cannot refresh: registry was not loaded from an API. "
                    + "Use refreshFrom(url) to specify an API endpoint.");
        }
        refreshFrom(apiUrl);
    }

    /**
     * Refreshes the model data from a specified API URL.
     * This is a convenience method that uses the default HTTP client.
     *
     * @param apiUrl the API URL to refresh from
     * @throws IOException if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public void refreshFrom(String apiUrl) throws IOException, InterruptedException {
        refreshFrom(apiUrl, null);
    }

    /**
     * Refresh the model data from a specific API URL. This will reload all
     * providers and models, replacing the existing data.
     *
     * @param apiUrl the API URL to refresh from
     * @throws IOException          if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public void refreshFrom(String apiUrl, HttpClient httpClient) throws IOException, InterruptedException {
        HttpClient client = httpClient != null
                ? httpClient
                : HttpClientBuilderLoader.loadHttpClientBuilder().build();

        HttpRequest request =
                HttpRequest.builder().url(apiUrl).method(HttpMethod.GET).build();

        SuccessfulHttpResponse response = client.execute(request);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch API data: HTTP " + response.statusCode());
        }

        // Parse the new data
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(response.body(), Map.class);

        // Clear existing providers
        providers.clear();

        // Load new providers
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String providerId = entry.getKey();
            Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
            provider.setId(providerId);
            providers.put(providerId, provider);
        }

        // Update the API URL
        this.apiUrl = apiUrl;
    }

    /**
     * Gets the API URL that this registry is using (if loaded from an API).
     *
     * @return the API URL, or {@code null} if not loaded from an API
     */
    public String getApiUrl() {
        return apiUrl;
    }

    @Override
    public String toString() {
        return "ModelRegistry{" + "providers=" + providers.size() + ", totalModels=" + getTotalModelCount()
                + ", apiUrl='" + apiUrl + '\'' + '}';
    }
}
