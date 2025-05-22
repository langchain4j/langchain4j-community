package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.langchain4j.Internal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;

@Internal
class AuthorizationUtils {

    private AuthorizationUtils() throws InstantiationException {
        throw new InstantiationException("Can not instantiate utility class");
    }

    private static final long expireMillis = 1000 * 60 * 30;
    private static final String id = "HS256";
    private static final String jcaName = "HmacSHA256";
    private static final MacAlgorithm macAlgorithm;

    static {
        try {
            // create a custom MacAlgorithm with a custom minKeyBitLength
            int minKeyBitLength = 128;
            Class<?> c = Class.forName("io.jsonwebtoken.impl.security.DefaultMacAlgorithm");
            Constructor<?> ctor = c.getDeclaredConstructor(String.class, String.class, int.class);
            ctor.setAccessible(true);
            macAlgorithm = (MacAlgorithm) ctor.newInstance(id, jcaName, minKeyBitLength);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Cache<String, String> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(expireMillis))
            .build();

    public static String getToken(String apiKey) {
        String token = getOrDefault(cache.getIfPresent(apiKey), generateToken(apiKey));
        return "Bearer " + token;
    }

    private static String generateToken(String apiKey) {
        String[] apiKeyParts = apiKey.split("\\.");
        String keyId = apiKeyParts[0];
        String secret = apiKeyParts[1];
        Map<String, Object> payload = new HashMap<>(3);
        payload.put("api_key", keyId);
        payload.put("exp", currentTimeMillis() + expireMillis);
        payload.put("timestamp", currentTimeMillis());

        String token = Jwts.builder()
                .header()
                .add("alg", id)
                .add("sign_type", "SIGN")
                .and()
                .content(Json.toJson(payload))
                .signWith(new SecretKeySpec(secret.getBytes(UTF_8), jcaName), macAlgorithm)
                .compact();
        cache.put(apiKey, token);
        return token;
    }
}
