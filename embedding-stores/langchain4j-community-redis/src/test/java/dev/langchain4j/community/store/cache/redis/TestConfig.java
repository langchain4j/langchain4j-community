package dev.langchain4j.community.store.cache.redis;

import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for loading test configuration properties.
 * This allows loading API keys from a properties file for local development
 * without requiring environment variables.
 */
public class TestConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = TestConfig.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input != null) {
                properties.load(input);
                System.out.println("Successfully loaded test.properties");
                System.out.println("OpenAI key from properties: "
                        + (properties.getProperty("openai.api.key") != null
                                ? properties.getProperty("openai.api.key").substring(0, 12) + "..."
                                : "not found"));
            } else {
                System.err.println("Could not find test.properties in classpath");
            }
        } catch (Exception e) {
            System.err.println("Failed to load test.properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the OpenAI API key from environment variables, system properties, or test.properties file.
     *
     * @return The OpenAI API key or null if not found
     */
    public static String getOpenAiApiKey() {
        // First try environment variable
        String key = System.getenv("OPENAI_API_KEY");

        // Then try system property
        if (key == null || key.isEmpty()) {
            key = System.getProperty("openai.api.key");
        }

        // Finally try the properties file
        if (key == null || key.isEmpty()) {
            key = properties.getProperty("openai.api.key");
        }

        return key;
    }

    /**
     * Gets the Anthropic API key from environment variables, system properties, or test.properties file.
     *
     * @return The Anthropic API key or null if not found
     */
    public static String getAnthropicApiKey() {
        // First try environment variable
        String key = System.getenv("ANTHROPIC_API_KEY");

        // Then try system property
        if (key == null || key.isEmpty()) {
            key = System.getProperty("anthropic.api.key");
        }

        // Finally try the properties file
        if (key == null || key.isEmpty()) {
            key = properties.getProperty("anthropic.api.key");
        }

        return key;
    }
}
