package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DashScope-specific response metadata for {@link QwenScoringModel}, exposed via
 * {@link dev.langchain4j.model.output.Response#metadata()} under the
 * {@link #DASHSCOPE_RESPONSE} key.
 *
 * <p>It is always populated (regardless of whether {@code returnDocuments} was requested),
 * carrying the original {@code request_id} and the {@code output.results} returned by the
 * <a href="https://www.alibabacloud.com/help/en/model-studio/text-rerank-api">DashScope Text Rerank API</a>.
 * The {@link Result#document} map is only populated when {@code returnDocuments} was enabled.
 */
public class QwenScoringResponseMetadata {

    /**
     * Key under which a {@link QwenScoringResponseMetadata} instance is stored in
     * {@link dev.langchain4j.model.output.Response#metadata()}.
     */
    public static final String DASHSCOPE_RESPONSE = "dashscope_response";

    private final String requestId;
    private final List<Result> results;

    protected QwenScoringResponseMetadata(Builder builder) {
        this.requestId = builder.requestId;
        this.results = builder.results;
    }

    /**
     * The {@code request_id} returned by the DashScope API.
     *
     * @return the request id.
     */
    public String requestId() {
        return requestId;
    }

    /**
     * The {@code output.results} returned by the DashScope API, in the original (relevance-descending)
     * order. Each {@link Result#index} maps back to the position in the input documents.
     *
     * @return the results.
     */
    public List<Result> results() {
        return results;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QwenScoringResponseMetadata that = (QwenScoringResponseMetadata) o;
        return Objects.equals(requestId, that.requestId) && Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, results);
    }

    @Override
    public String toString() {
        return "QwenScoringResponseMetadata{" + "requestId=" + quoted(requestId) + ", results=" + results + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String requestId;
        private List<Result> results;

        /**
         * The {@code request_id} returned by the DashScope API.
         *
         * @param requestId the request id
         * @return {@code this}
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * The {@code output.results} returned by the DashScope API, in the original (relevance-descending)
         * order.
         *
         * @param results the results
         * @return {@code this}
         */
        public Builder results(List<Result> results) {
            this.results = results;
            return this;
        }

        public QwenScoringResponseMetadata build() {
            return new QwenScoringResponseMetadata(this);
        }
    }

    /**
     * A single rerank result entry from the DashScope {@code output.results}.
     *
     * @param document       the original document as a map of its fields (e.g. {@code {"text": "..."}}),
     *                       only populated when {@code returnDocuments} was enabled on the request;
     *                       {@code null} otherwise
     * @param index          the index of the document in the input {@code documents} array
     * @param relevanceScore the relevance score of the document with respect to the query
     */
    public record Result(Map<String, String> document, Integer index, Double relevanceScore) {}
}
