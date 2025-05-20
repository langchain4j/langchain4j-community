package dev.langchain4j.community.chain;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RetrievalQAChainTest {

    private static final Query QUERY = Query.from("query");
    private static final String ANSWER = "answer";
    public static final PromptTemplate promptTemplate = PromptTemplate.from(
            """
                    Answer the question based only on the context provided.

                    Context:
                    {{contents}}

                    Question:
                    {{userMessage}}

                    Answer:
                    """);

    @Mock
    ChatModel chatModel;

    @Mock
    ContentRetriever contentRetriever;

    @Captor
    ArgumentCaptor<String> messagesCaptor;

    @BeforeEach
    void beforeEach() {
        lenient().when(chatModel.chat(anyString())).thenReturn(ANSWER);
    }

    @Test
    void should_inject_retrieved_segments() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Segment 1"), Content.from("Segment 2")));

        RetrievalQAChain chain = RetrievalQAChain.builder()
                .chatModel(chatModel)
                // .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        String expectedUserMessage =
                "query\n" + "\n" + "Answer using the following information:\n" + "Segment 1\n" + "\n" + "Segment 2";
        assertThat(messagesCaptor.getValue()).isEqualTo(expectedUserMessage);
    }

    @Test
    void should_inject_retrieved_segments_using_custom_prompt_template() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Segment 1"), Content.from("Segment 2")));

        PromptTemplate promptTemplate = PromptTemplate.from(
                """
                    Answer the question based only on the context provided.

                    Context: {{contents}}

                    Question: {{userMessage}}

                    Answer:
                    """);

        // -- PromptTemplate via contentInjector
        RetrievalQAChain chain = RetrievalQAChain.builder()
                .chatModel(chatModel)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(promptTemplate)
                                .build())
                        .build())
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        String expectedUserMessage =
                """
                Answer the question based only on the context provided.
                Context:
                Segment 1
                Segment 2

                Question:
                query
                Answer:
                """;
        assertThat(messagesCaptor.getValue()).isEqualToIgnoringWhitespace(expectedUserMessage);

        // -- PromptTemplate via prompt builder
        RetrievalQAChain chainViaPromptBuilder = RetrievalQAChain.builder()
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .prompt(promptTemplate)
                .build();

        // when
        String answerViaPromptBuilder = chainViaPromptBuilder.execute(QUERY);

        // then
        assertThat(answerViaPromptBuilder).isEqualTo(ANSWER);

        assertThat(messagesCaptor.getValue()).isEqualToIgnoringWhitespace(expectedUserMessage);
    }

    @Test
    void should_inject_retrieved_segments_using_custom_prompt_template_and_metadata() {
        final List<Content> list1 = List.of(
                Content.from(TextSegment.from("Segment 1 with meta")),
                Content.from(TextSegment.from("Segment 2  with meta")));

        when(contentRetriever.retrieve(argThat(arg -> {
                    return arg != null && arg.metadata() != null;
                })))
                .thenReturn(list1);

        RetrievalQAChain chain = RetrievalQAChain.builder()
                .chatModel(chatModel)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(promptTemplate)
                                .build())
                        .build())
                .build();

        // when
        Metadata metadata1 = Metadata.from(
                UserMessage.from("user message"),
                42,
                List.of(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));
        final Query queryWithMetadata = Query.from("query", metadata1);
        String answer = chain.execute(queryWithMetadata);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        String expectedUserMessage =
                """
                Answer the question based only on the context provided.
                Context:
                Segment 1 with meta
                Segment 2  with meta

                Question:
                query
                Answer:
                """;
        assertThat(messagesCaptor.getValue()).isEqualToIgnoringWhitespace(expectedUserMessage);
    }

    @Test
    void should_throws_exception_if_neither_retriever_nor_retrieval_augmentor_is_defined() {
        try {
            RetrievalQAChain.builder().chatModel(chatModel).build();
            fail("Should fail due to missing builder configurations");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("queryRouter cannot be null");
        }
    }

    @Test
    void should_throws_exception_if_retriever_is_null() {
        try {
            RetrievalQAChain.builder()
                    .chatModel(chatModel)
                    .contentRetriever(null)
                    .build();
            fail("Should fail due to missing builder configurations");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("queryRouter cannot be null");
        }
    }
}
