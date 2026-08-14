package dev.langchain4j.community.model.oracle.oci.genai;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.spi.services.TokenStreamAdapter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public final class TestTokenStreamToFluxAdapter implements TokenStreamAdapter {

    public TestTokenStreamToFluxAdapter() {}

    @Override
    public boolean canAdaptTokenStreamTo(Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        if (parameterizedType.getRawType() != Flux.class) {
            return false;
        }
        var actualTypeArguments = parameterizedType.getActualTypeArguments();
        return actualTypeArguments.length == 1 && actualTypeArguments[0] == String.class;
    }

    @Override
    public Object adapt(TokenStream tokenStream) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        tokenStream
                .onPartialResponse(sink::tryEmitNext)
                .onCompleteResponse(ignored -> sink.tryEmitComplete())
                .onError(sink::tryEmitError)
                .start();
        return sink.asFlux();
    }
}
