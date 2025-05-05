package dev.langchain4j.community.rag.content;

import dev.langchain4j.chain.Chain;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;

public class RetrievalQAChain implements Chain<Query, String> {
    private final ChatModel chatModel;
    private final RetrievalAugmentor retrievalAugmentor;

    public RetrievalQAChain(final ChatModel chatModel, final RetrievalAugmentor retrievalAugmentor) {
        this.chatModel = chatModel;
        this.retrievalAugmentor = retrievalAugmentor;
    }

    @Override
    public String execute(final Query query) {
        UserMessage userMessage = augment(query);
        return chatModel.chat(userMessage.singleText());
    }

    private UserMessage augment(Query query) {

        final UserMessage from = UserMessage.from(query.text());

        final Metadata metadata = query.metadata() == null ? Metadata.from(from, null, null) : query.metadata();
        AugmentationRequest request = new AugmentationRequest(from, metadata);
        AugmentationResult result = retrievalAugmentor.augment(request);
        return (UserMessage) result.chatMessage();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public static final String CONTENT_RETRIEVER_NULL_ERROR = "contentRetriever cannot be null";
        private ChatModel chatModel;
        private final DefaultRetrievalAugmentor.DefaultRetrievalAugmentorBuilder augmentorBuilder =
                DefaultRetrievalAugmentor.builder();
        private RetrievalAugmentor retrievalAugmentor;

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder contentRetriever(ContentRetriever contentRetriever) {
            if (contentRetriever != null) {
                augmentorBuilder.contentRetriever(contentRetriever);
            }
            return this;
        }

        public Builder prompt(PromptTemplate promptTemplate) {
            DefaultContentInjector contentInjector = DefaultContentInjector.builder()
                    .promptTemplate(promptTemplate)
                    .build();
            augmentorBuilder.contentInjector(contentInjector);
            return this;
        }

        public Builder retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
            this.retrievalAugmentor = retrievalAugmentor;
            return this;
        }

        public RetrievalQAChain build() {
            if (retrievalAugmentor == null) {
                try {
                    return new RetrievalQAChain(chatModel, augmentorBuilder.build());
                } catch (RuntimeException e) {
                    // we populate the `queryRouter` of the RetrievalQAChain.builder() via
                    // augmentorBuilder.contentRetriever(..)
                    if (e.getMessage().contains("queryRouter cannot be null")) {
                        throw Exceptions.illegalArgument(CONTENT_RETRIEVER_NULL_ERROR);
                    }
                    throw e;
                }
            }
            return new RetrievalQAChain(chatModel, retrievalAugmentor);
        }
    }
}
