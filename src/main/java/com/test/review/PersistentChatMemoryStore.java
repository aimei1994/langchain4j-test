package com.test.review;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Persists chat memory per PR (memoryId) to disk as JSON so review context
 * survives app restarts. memoryId is the PR identifier — one memory file per PR.
 * Swap the file I/O below for a JDBC/Redis-backed implementation if you run
 * more than one instance.
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final Path storageDir;

    public PersistentChatMemoryStore(@Value("${review.memory.storage-dir:./review-memory}") String storageDir) {
        this.storageDir = Path.of(storageDir);
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Path file = fileFor(memoryId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            Files.writeString(fileFor(memoryId), ChatMessageSerializer.messagesToJson(messages), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            Files.deleteIfExists(fileFor(memoryId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileFor(Object memoryId) {
        String safeName = memoryId.toString().replaceAll("[^a-zA-Z0-9_-]", "_");
        return storageDir.resolve(safeName + ".json");
    }
}
