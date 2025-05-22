package dev.langchain4j.community.embedding;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.store.embedding.ParentChildEmbeddingStoreIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.IngestionResult;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ParentChildEmbeddingStoreIngestorTest {

    @Test
    void should_extract_text_then_split_into_segments_then_embed_them_and_store_in_embedding_store() {

        Document firstDocument = Document.from("First sentence.");
        Document secondDocument = Document.from("Second sentence. Third sentence.");
        Document thirdDocument = Document.from("Fourth sentence.");
        Document fourthDocument = Document.from("Fifth sentence.");
        Document fifthDocument = Document.from("Sixth Sentence");

        // -- mock documentTransformer
        DocumentTransformer documentTransformer = mock(DocumentTransformer.class);
        when(documentTransformer.transformAll(singletonList(firstDocument))).thenReturn(singletonList(firstDocument));
        when(documentTransformer.transformAll(asList(secondDocument, thirdDocument)))
                .thenReturn(asList(secondDocument, thirdDocument));
        when(documentTransformer.transformAll(asList(fourthDocument, fifthDocument)))
                .thenReturn(asList(fourthDocument, fifthDocument));

        // -- mock documentSplitter
        DocumentSplitter documentSplitter = mock(DocumentSplitter.class);
        when(documentSplitter.splitAll(singletonList(firstDocument)))
                .thenReturn(singletonList(textSegment("First sentence.")));
        when(documentSplitter.splitAll(asList(secondDocument, thirdDocument)))
                .thenReturn(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence.")));
        when(documentSplitter.splitAll(asList(fourthDocument, fifthDocument)))
                .thenReturn(asList(textSegment("Fifth sentence."), textSegment("Sixth sentence.")));

        // -- mock textSegmentTransformer
        TextSegmentTransformer textSegmentTransformer = mock(TextSegmentTransformer.class);
        when(textSegmentTransformer.transformAll(singletonList(textSegment("First sentence."))))
                .thenReturn(singletonList(textSegment("Transformed first sentence.")));
        when(textSegmentTransformer.transformAll(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence."))))
                .thenReturn(asList(
                        textSegment("Transformed second sentence."),
                        textSegment("Transformed third sentence."),
                        textSegment("Transformed fourth sentence.")));
        when(textSegmentTransformer.transformAll(
                        asList(textSegment("Fifth sentence."), textSegment("Sixth sentence."))))
                .thenReturn(
                        asList(textSegment("Transformed fifth sentence."), textSegment("Transformed sixth sentence.")));

        // -- mock documentChildSplitter
        DocumentSplitter documentChildSplitter = mock(DocumentSplitter.class);
        when(documentChildSplitter.split(any())).thenAnswer(invocation -> {
            Document arg = invocation.getArgument(0);
            String originalText = arg.text();
            return Stream.of(originalText.split(" ")).map(TextSegment::from).toList();
        });

        // -- mock documentChildSplitter
        TextSegmentTransformer childTextSegmentTransformer = mock(TextSegmentTransformer.class);
        when(childTextSegmentTransformer.transform(any())).thenAnswer(i -> {
            final TextSegment argument = i.getArgument(0);
            return TextSegment.from("child_" + argument.text());
        });

        // -- mock embeddingModel
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        TokenUsage firstTokenUsage = new TokenUsage(1, 2, 3);
        TokenUsage secondTokenUsage = new TokenUsage(3, 5, 8);
        TokenUsage thirdTokenUsage = new TokenUsage(8, 10, 12);
        TokenUsage fourthTokenUsage = new TokenUsage(8, 10, 12);
        TokenUsage fifthTokenUsage = new TokenUsage(8, 10, 12);
        TokenUsage otherTokenUsage = new TokenUsage(18, 11, 13);

        final List<Embedding> firstEmbeddings = singletonList(Embedding.from(new float[] {1}));
        final List<Embedding> secondEmbeddings = asList(
                Embedding.from(new float[] {2}), Embedding.from(new float[] {3}), Embedding.from(new float[] {4}));
        final List<Embedding> thirdEmbeddings =
                asList(Embedding.from(new float[] {5}), Embedding.from(new float[] {6}));
        final List<Embedding> fourthEmbeddings =
                asList(Embedding.from(new float[] {7}), Embedding.from(new float[] {8}));
        final List<Embedding> fifthEmbeddings =
                asList(Embedding.from(new float[] {4}), Embedding.from(new float[] {7}));
        final List<Embedding> sixthEmbeddings =
                asList(Embedding.from(new float[] {5}), Embedding.from(new float[] {6}));

        when(embeddingModel.embedAll(any())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);

            if (segments.stream().anyMatch(i -> i.text().contains("first"))) {
                return Response.from(firstEmbeddings, firstTokenUsage);
            }
            if (segments.stream().anyMatch(i -> i.text().contains("second"))) {
                return Response.from(secondEmbeddings, secondTokenUsage);
            }

            if (segments.stream().anyMatch(i -> i.text().contains("third"))) {
                return Response.from(thirdEmbeddings, thirdTokenUsage);
            }

            if (segments.stream().anyMatch(i -> i.text().contains("fourth"))) {
                return Response.from(fourthEmbeddings, fourthTokenUsage);
            }

            if (segments.stream().anyMatch(i -> i.text().contains("fifth"))) {
                return Response.from(fifthEmbeddings, fifthTokenUsage);
            }

            return Response.from(sixthEmbeddings, otherTokenUsage);
        });

        @SuppressWarnings("unchecked")
        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        ParentChildEmbeddingStoreIngestor ingestor = ParentChildEmbeddingStoreIngestor.builder()
                .documentTransformer(documentTransformer)
                .documentSplitter(documentSplitter)
                .textSegmentTransformer(textSegmentTransformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentChildSplitter(documentChildSplitter)
                .childTextSegmentTransformer(childTextSegmentTransformer)
                .build();

        // Method overloads.
        IngestionResult ingestionResult1 = ingestor.ingest(firstDocument);
        IngestionResult ingestionResult2 = ingestor.ingest(secondDocument, thirdDocument);
        IngestionResult ingestionResult3 = ingestor.ingest(asList(fourthDocument, fifthDocument));

        // Assertions.
        assertThat(ingestionResult1.tokenUsage()).isEqualTo(firstTokenUsage);
        assertThat(ingestionResult2.tokenUsage())
                .isEqualTo(secondTokenUsage.add(thirdTokenUsage).add(fourthTokenUsage));
        assertThat(ingestionResult3.tokenUsage()).isEqualTo(fifthTokenUsage.add(otherTokenUsage));

        // -- verify documentTransformer interactions
        verify(documentTransformer).transformAll(singletonList(firstDocument));
        verify(documentTransformer).transformAll(asList(secondDocument, thirdDocument));
        verify(documentTransformer).transformAll(asList(fourthDocument, fifthDocument));
        verifyNoMoreInteractions(documentTransformer);

        // -- verify documentSplitter interactions
        verify(documentSplitter).splitAll(singletonList(firstDocument));
        verify(documentSplitter).splitAll(asList(secondDocument, thirdDocument));
        verify(documentSplitter).splitAll(asList(fourthDocument, fifthDocument));
        verifyNoMoreInteractions(documentSplitter);

        // -- verify textSegmentTransformer interactions
        verify(textSegmentTransformer).transformAll(singletonList(textSegment("First sentence.")));
        verify(textSegmentTransformer)
                .transformAll(asList(
                        textSegment("Second sentence."),
                        textSegment("Third sentence."),
                        textSegment("Fourth sentence.")));
        verify(textSegmentTransformer)
                .transformAll(asList(textSegment("Fifth sentence."), textSegment("Sixth sentence.")));
        verifyNoMoreInteractions(textSegmentTransformer);

        // -- verify embeddingModel interactions
        final List<TextSegment> firstSegments =
                asList(textSegment("child_Transformed"), textSegment("child_first"), textSegment("child_sentence."));
        final List<TextSegment> secondSegments =
                asList(textSegment("child_Transformed"), textSegment("child_second"), textSegment("child_sentence."));
        final List<TextSegment> thirdSegments =
                asList(textSegment("child_Transformed"), textSegment("child_third"), textSegment("child_sentence."));
        final List<TextSegment> fourthSegments =
                asList(textSegment("child_Transformed"), textSegment("child_fourth"), textSegment("child_sentence."));
        final List<TextSegment> fifthSegments =
                asList(textSegment("child_Transformed"), textSegment("child_fifth"), textSegment("child_sentence."));
        final List<TextSegment> sixthSegments =
                asList(textSegment("child_Transformed"), textSegment("child_sixth"), textSegment("child_sentence."));

        verify(embeddingModel).embedAll(firstSegments);
        verify(embeddingModel).embedAll(secondSegments);
        verify(embeddingModel).embedAll(thirdSegments);
        verify(embeddingModel).embedAll(fourthSegments);
        verify(embeddingModel).embedAll(fifthSegments);
        verify(embeddingModel).embedAll(sixthSegments);

        verifyNoMoreInteractions(embeddingModel);

        // -- verify embeddingStore interactions
        verify(embeddingStore).addAll(firstEmbeddings, firstSegments);
        verify(embeddingStore).addAll(secondEmbeddings, secondSegments);
        verify(embeddingStore).addAll(thirdEmbeddings, thirdSegments);
        verify(embeddingStore).addAll(fourthEmbeddings, fourthSegments);
        verify(embeddingStore).addAll(fifthEmbeddings, fifthSegments);
        verify(embeddingStore).addAll(sixthEmbeddings, sixthSegments);
        verifyNoMoreInteractions(embeddingStore);
    }

    @Test
    void should_not_split_when_no_splitter_is_specified() {

        // given
        String text = "Some text";
        Document document = Document.from(text);

        TextSegment expectedTextSegment = TextSegment.from(text, Metadata.from("index", "0"));
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embedAll(singletonList(expectedTextSegment)))
                .thenReturn(Response.from(singletonList(Embedding.from(new float[] {1})), tokenUsage));

        EmbeddingStore<TextSegment> embeddingStore = mock(EmbeddingStore.class);

        ParentChildEmbeddingStoreIngestor ingestor = ParentChildEmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        // when
        IngestionResult ingestionResult = ingestor.ingest(document);

        // then
        verify(embeddingStore)
                .addAll(singletonList(Embedding.from(new float[] {1})), singletonList(expectedTextSegment));
        verifyNoMoreInteractions(embeddingStore);

        assertThat(ingestionResult.tokenUsage()).isEqualTo(tokenUsage);
    }
}
