package dev.langchain4j.community.model.minimax.spi;

import dev.langchain4j.community.model.minimax.MiniMaxStreamingChatModel;
import java.util.function.Supplier;

public interface MiniMaxStreamingChatModelBuilderFactory
        extends Supplier<MiniMaxStreamingChatModel.MiniMaxStreamingChatModelBuilder> {}
