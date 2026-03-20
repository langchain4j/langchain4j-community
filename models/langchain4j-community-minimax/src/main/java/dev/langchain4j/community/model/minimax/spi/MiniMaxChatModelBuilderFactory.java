package dev.langchain4j.community.model.minimax.spi;

import dev.langchain4j.community.model.minimax.MiniMaxChatModel;
import java.util.function.Supplier;

public interface MiniMaxChatModelBuilderFactory extends Supplier<MiniMaxChatModel.MiniMaxChatModelBuilder> {}
