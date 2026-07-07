package com.test.review;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/**
 * Wires memory + feedback retrieval into a single AiServices instance:
 * - chatMemoryProvider: one memory window per PR id, persisted via PersistentChatMemoryStore
 * - contentRetriever: pulls similar past reviewer feedback into the prompt automatically
 */
@Service
public class PrReviewService {

    private final ChatModel chatModel;
    private final PersistentChatMemoryStore chatMemoryStore;
    private final ReviewFeedbackStore feedbackStore;
    private final EmbeddingModel embeddingModel;

    private PrReviewAiService aiService;

    public PrReviewService(ChatModel chatModel,
                            PersistentChatMemoryStore chatMemoryStore,
                            ReviewFeedbackStore feedbackStore,
                            EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.chatMemoryStore = chatMemoryStore;
        this.feedbackStore = feedbackStore;
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    void init() {
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(feedbackStore.embeddingStore())
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        aiService = AiServices.builder(PrReviewAiService.class)
                .chatModel(chatModel)
                .chatMemoryProvider(prId -> MessageWindowChatMemory.builder()
                        .id(prId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .contentRetriever(contentRetriever)
                .build();
    }

    /** Reviews a diff for the given PR; memory of prior turns on that PR carries over automatically. */
    public String review(String prId, String diff) {
        return aiService.review(prId, diff);
    }

    /** Call once a human accepts/rejects/edits an AI comment on the Azure DevOps PR. */
    public void recordFeedback(ReviewFeedback feedback) {
        feedbackStore.record(feedback);
    }
}
