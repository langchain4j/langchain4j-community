package dev.langchain4j.model.registry;

import dev.langchain4j.model.registry.dto.ModelInfo;
import dev.langchain4j.model.registry.dto.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ModelRegistryTest implements WithAssertions {

    private static final String JSON =
            """
			{
			  "evroc": {
			    "id": "evroc",
			    "env": ["EVROC_API_KEY"],
			    "npm": "@ai-sdk/openai-compatible",
			    "api": "https://models.think.evroc.com/v1",
			    "name": "evroc",
			    "doc": "https://docs.evroc.com/products/think/overview.html",
			    "models": {
			      "nvidia/Llama-3.3-70B-Instruct-FP8": {
			        "id": "nvidia/Llama-3.3-70B-Instruct-FP8",
			        "name": "Llama 3.3 70B",
			        "family": "llama",
			        "attachment": false,
			        "reasoning": false,
			        "tool_call": false,
			        "release_date": "2024-12-01",
			        "last_updated": "2024-12-01",
			        "modalities": {
			          "input": ["text"],
			          "output": ["text"]
			        },
			        "open_weights": true,
			        "cost": {
			          "input": 1.18,
			          "output": 1.18
			        },
			        "limit": {
			          "context": 131072,
			          "output": 32768
			        }
			      },
			      "microsoft/Phi-4-multimodal-instruct": {
			        "id": "microsoft/Phi-4-multimodal-instruct",
			        "name": "Phi-4 15B",
			        "family": "phi",
			        "attachment": false,
			        "reasoning": false,
			        "tool_call": false,
			        "release_date": "2025-01-01",
			        "last_updated": "2025-01-01",
			        "modalities": {
			          "input": ["text", "image"],
			          "output": ["text"]
			        },
			        "open_weights": true,
			        "cost": {
			          "input": 0.24,
			          "output": 0.47
			        },
			        "limit": {
			          "context": 32000,
			          "output": 32000
			        }
			      }
			    }
			  }
			}
			""";

    private void verifyRegistry(ModelRegistry registry) {

        // ----- Registry metadata -----
        assertThat(registry.getProviderCount()).isEqualTo(1);
        assertThat(registry.getProviderIds()).containsExactly("evroc");
        assertThat(registry.getTotalModelCount()).isEqualTo(2);

        // ----- Provider metadata -----
        Provider provider = registry.getProvider("evroc");

        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo("evroc");
        assertThat(provider.getEnv()).contains("EVROC_API_KEY");
        assertThat(provider.getApi()).contains("models.think.evroc.com");
        assertThat(provider.getModelCount()).isEqualTo(2);

        // ----- Model metadata -----
        ModelInfo llama = registry.getModelInfo("evroc", "nvidia/Llama-3.3-70B-Instruct-FP8");

        assertThat(llama).isNotNull();
        assertThat(llama.getFamily()).isEqualTo("llama");
        assertThat(llama.getReleaseDate()).isEqualTo("2024-12-01");
        assertThat(llama.getLastUpdated()).isEqualTo("2024-12-01");
        assertThat(llama.hasOpenWeights()).isTrue();

        // ----- Multimodal detection -----
        List<ModelInfo> multimodal = registry.getMultimodalModels();

        assertThat(multimodal).hasSize(1);
        assertThat(multimodal.get(0).getId()).isEqualTo("microsoft/Phi-4-multimodal-instruct");

        // ----- Open weight models -----
        List<ModelInfo> openWeightModels = registry.getOpenWeightModels();
        assertThat(openWeightModels).hasSize(2);

        // ----- Context filter -----
        List<ModelInfo> largeContext = registry.getModelsWithLargeContext(100000);

        assertThat(largeContext).hasSize(1);
        assertThat(largeContext.get(0).getFamily()).isEqualTo("llama");

        // ----- Cost filter -----
        List<ModelInfo> cheapModels = registry.getModelsBelowCost(0.5, 0.5);

        assertThat(cheapModels).hasSize(1);
        assertThat(cheapModels.get(0).getFamily()).isEqualTo("phi");

        // ----- Family statistics -----
        Map<String, Long> familyStats = registry.getModelCountByFamily();

        assertThat(familyStats).containsEntry("llama", 1L).containsEntry("phi", 1L);

        // ----- Average cost -----
        assertThat(registry.getAverageInputCost()).isGreaterThan(0);
        assertThat(registry.getAverageOutputCost()).isGreaterThan(0);

        // ----- Search -----
        List<ModelInfo> searchResult = registry.searchByName("phi");

        assertThat(searchResult).hasSize(1);
        assertThat(searchResult.get(0).getFamily()).isEqualTo("phi");

        // ----- toString contract -----
        assertThat(registry.toString())
                .contains("providers=1")
                .contains("totalModels=2")
                .contains("apiUrl");
    }

    @Test
    void load_and_validate_real_payload() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        verifyRegistry(registry);
    }

    @Test
    void load_from_api_mock_server() throws Exception {

        try (MockWebServer server = new MockWebServer()) {

            server.enqueue(new MockResponse().setResponseCode(200).setBody(JSON));

            server.start();

            String url = server.url("/api.json").toString();

            ModelRegistry registry = ModelRegistry.fromApi(url);

            verifyRegistry(registry);
        }
    }
}
