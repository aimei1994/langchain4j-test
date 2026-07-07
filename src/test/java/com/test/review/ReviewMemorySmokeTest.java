package com.test.review;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Exercises the two "learning" building blocks without needing a live LLM:
// persistent per-PR chat memory, and the accept/reject feedback store.
class ReviewMemorySmokeTest {

    @Test
    void chatMemorySurvivesAcrossStoreInstances(@TempDir Path tempDir) {
        String prId = "azure-pr-42";

        PersistentChatMemoryStore firstInstance = new PersistentChatMemoryStore(tempDir.toString());
        firstInstance.updateMessages(prId, List.of(UserMessage.from("review this diff: ...")));

        // Simulate an app restart: brand new store instance, same directory.
        PersistentChatMemoryStore afterRestart = new PersistentChatMemoryStore(tempDir.toString());
        List<ChatMessage> restored = afterRestart.getMessages(prId);

        assertThat(restored).hasSize(1);
        assertThat(((UserMessage) restored.get(0)).singleText()).contains("review this diff");
    }

    @Test
    void feedbackStoreRetrievesSimilarPastRejection(@TempDir Path tempDir) {
        ReviewFeedbackStore store = new ReviewFeedbackStore(
                new AllMiniLmL6V2EmbeddingModel(),
                tempDir.resolve("feedback-store.json").toString());

        store.record(new ReviewFeedback(
                "azure-pr-42",
                "src/main/java/com/test/UserRepository.java",
                14,
                "null pointer exception",
                "cache.get(key) may return null before calling toUpperCase()",
                FeedbackDecision.REJECTED,
                "false positive — key presence is checked by an assertion earlier in the method"));

        List<String> similar = store.similarPastFeedback(
                "String value = cache.get(key); return value.toUpperCase();", 3);

        assertThat(similar).isNotEmpty();
        assertThat(similar.get(0)).contains("REJECTED");
        assertThat(similar.get(0)).contains("false positive");
    }
}
