package dev.langchain4j.community.model.novitaai.spi;

import dev.langchain4j.community.model.novitaai.NovitaAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link NovitaAiStreamingChatModel} instances.
 */
public interface NovitaAiStreamingChatModelBuilderFactory extends Supplier<NovitaAiStreamingChatModel.Builder> {
}
