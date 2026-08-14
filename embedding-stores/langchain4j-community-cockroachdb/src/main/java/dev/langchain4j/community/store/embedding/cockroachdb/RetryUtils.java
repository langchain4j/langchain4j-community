package dev.langchain4j.community.store.embedding.cockroachdb;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry helper for CockroachDB serialization failures (SQLSTATE 40001).
 *
 * <p>Under CockroachDB's default SERIALIZABLE isolation, transactions can be aborted
 * with a {@code restart transaction} error and must be retried by the client. This
 * mirrors the retry logic in the Python {@code langchain-cockroachdb} library.
 */
final class RetryUtils {

    static final int DEFAULT_MAX_RETRIES = 5;
    static final long DEFAULT_INITIAL_BACKOFF_MS = 100L;
    static final long DEFAULT_MAX_BACKOFF_MS = 10_000L;
    static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final Logger logger = LoggerFactory.getLogger(RetryUtils.class);

    private RetryUtils() {}

    static <T> T withRetry(SqlAction<T> action) {
        return withRetry(action, DEFAULT_MAX_RETRIES);
    }

    static <T> T withRetry(SqlAction<T> action, int maxRetries) {
        long backoff = DEFAULT_INITIAL_BACKOFF_MS;
        SQLException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.run();
            } catch (SQLException e) {
                last = e;
                if (!isRetryable(e) || attempt == maxRetries) {
                    throw new CockroachDbRequestFailedException(
                            "CockroachDB operation failed after " + (attempt + 1) + " attempt(s)", e);
                }
                long jittered =
                        (long) (backoff * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5));
                logger.debug(
                        "Retryable CockroachDB error on attempt {}/{}: {}, sleeping {} ms",
                        attempt + 1,
                        maxRetries + 1,
                        e.getSQLState(),
                        jittered);
                sleep(jittered);
                backoff = Math.min((long) (backoff * DEFAULT_BACKOFF_MULTIPLIER), DEFAULT_MAX_BACKOFF_MS);
            }
        }
        throw new CockroachDbRequestFailedException("Exhausted retries", last);
    }

    static boolean isRetryable(SQLException e) {
        if (e == null) return false;
        if ("40001".equals(e.getSQLState())) return true;
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("restart transaction")
                || msg.contains("serialization failure")
                || msg.contains("connection reset")
                || msg.contains("broken pipe")
                || msg.contains("server closed");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CockroachDbRequestFailedException("Interrupted during CockroachDB retry backoff", ie);
        }
    }

    @FunctionalInterface
    interface SqlAction<T> {
        T run() throws SQLException;
    }
}
