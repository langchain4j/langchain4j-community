package dev.langchain4j.community.store.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

/**
 * Integration test for RedisCache with TestContainers.
 *
 * This test requires Docker to run Redis in a container.
 */
@Testcontainers
public class RedisCacheIT {

    private static final int REDIS_PORT = 6379;

    @Container
    private final GenericContainer<?> redis =
            new GenericContainer<>("redis/redis-stack:latest").withExposedPorts(REDIS_PORT);

    private JedisPooled jedisPooled;
    private RedisCache redisCache;

    @BeforeEach
    public void setUp() {
        String host = redis.getHost();
        Integer port = redis.getMappedPort(REDIS_PORT);

        jedisPooled = new JedisPooled(host, port);
        redisCache = new RedisCache(jedisPooled, 3600, "test-cache");
    }

    @AfterEach
    public void tearDown() {
        redisCache.clear();
        redisCache.close();
    }

    @Test
    public void should_store_and_retrieve_response() {
        // given
        String prompt = "What is the capital of France?";
        String llmString = "test-llm";

        Response<String> response = new Response<>("Paris is the capital of France.", new TokenUsage(10, 20, 30), null);

        // when
        redisCache.update(prompt, llmString, response);

        // then
        Response<?> result = redisCache.lookup(prompt, llmString);
        assertThat(result).isNotNull();
    }

    @Test
    public void should_not_find_different_prompt() {
        // given
        String prompt1 = "What is the capital of France?";
        String prompt2 = "What is the capital of Germany?";
        String llmString = "test-llm";

        Response<String> response = new Response<>("Paris is the capital of France.", new TokenUsage(10, 20, 30), null);

        // when
        redisCache.update(prompt1, llmString, response);

        // then
        Response<?> result = redisCache.lookup(prompt2, llmString);
        assertThat(result).isNull();
    }

    @Test
    public void should_respect_llm_isolation() {
        // given
        String prompt = "What is the capital of France?";
        String llmString1 = "test-llm-1";
        String llmString2 = "test-llm-2";

        Response<String> response = new Response<>("Paris is the capital of France.", new TokenUsage(10, 20, 30), null);

        // when
        redisCache.update(prompt, llmString1, response);

        // then
        Response<?> result = redisCache.lookup(prompt, llmString2);
        assertThat(result).isNull();
    }
}
