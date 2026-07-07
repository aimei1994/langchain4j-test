package com.test.review;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores past AI review comments plus the reviewer's accept/reject/edit decision
 * as embeddings, so future reviews can retrieve similar past cases instead of
 * repeating findings a human already dismissed. This is in-context learning
 * (RAG), not weight training — swap InMemoryEmbeddingStore for Chroma/PGVector/
 * Milvus to scale beyond a single instance.
 */
@Service
public class ReviewFeedbackStore {

    private static final Logger log = LoggerFactory.getLogger(ReviewFeedbackStore.class);

    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Path storeFile;

    public ReviewFeedbackStore(EmbeddingModel embeddingModel,
                                @Value("${review.feedback.store-file:./review-memory/feedback-store.json}") String storeFile) {
        this.embeddingModel = embeddingModel;
        this.storeFile = Path.of(storeFile);
        this.embeddingStore = loadOrCreate(this.storeFile);
    }

    private static InMemoryEmbeddingStore<TextSegment> loadOrCreate(Path file) {
        if (Files.exists(file)) {
            log.info("Loading review feedback store from {}", file);
            return InMemoryEmbeddingStore.fromFile(file);
        }
        return new InMemoryEmbeddingStore<>();
    }

    public EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    /** Records a reviewer's decision on one AI-generated comment. */
    public void record(ReviewFeedback feedback) {
        String text = "rule: %s%nai comment: %s%nfile: %s".formatted(
                feedback.ruleName(), feedback.aiComment(), feedback.filePath());

        Metadata metadata = Metadata.from(Map.of(
                "prId", feedback.prId(),
                "ruleName", feedback.ruleName(),
                "decision", feedback.decision().name(),
                "reviewerNote", feedback.reviewerNote() == null ? "" : feedback.reviewerNote(),
                "filePath", feedback.filePath()
        ));

        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        log.info("Recorded {} feedback for rule [{}] on PR [{}]",
                feedback.decision(), feedback.ruleName(), feedback.prId());
    }

    /** Finds past feedback relevant to the given code snippet, formatted for prompt injection. */
    public List<String> similarPastFeedback(String codeSnippet, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(codeSnippet).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.6)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(segment -> "[%s] %s (reviewer note: %s)".formatted(
                        segment.metadata().getString("decision"),
                        segment.text().replace("\n", " "),
                        segment.metadata().getString("reviewerNote")))
                .collect(Collectors.toList());
    }

    // Flush to disk on shutdown; loadOrCreate() picks it back up on next startup.
    @PreDestroy
    public void persist() {
        embeddingStore.serializeToFile(storeFile);
        log.info("Persisted review feedback store to {}", storeFile);
    }
}
