package com.test.skill;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);
    private static final long MAX_FILE_BYTES = 512 * 1024; // 500 KB safety cap

    @Tool("Read the content of a file at the given absolute path. Returns file content as a string.")
    public String readFile(String absolutePath) {
        Path path = Path.of(absolutePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return "ERROR: file not found or not a regular file: " + absolutePath;
        }
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return "ERROR: file too large (> 500 KB), skipping: " + absolutePath;
            }
            List<String> lines = Files.readAllLines(path);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.warn("readFile failed for {}: {}", absolutePath, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("List all files under a directory matching a file extension (e.g. '.java', '.kt', '.py'). Returns newline-separated absolute paths.")
    public String listFiles(String directory, String extension) {
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return "ERROR: directory not found: " + directory;
        }
        try {
            String glob = "**/*" + (extension.startsWith(".") ? extension : "." + extension);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<String> results = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matcher.matches(root.relativize(file))) {
                        results.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
            if (results.isEmpty()) return "No files found matching extension " + extension + " in " + directory;
            return String.join("\n", results);
        } catch (IOException e) {
            log.warn("listFiles failed for {}: {}", directory, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Search for a keyword in all files matching an extension under a directory. Returns matching lines as 'filepath:lineNumber: content'.")
    public String grepInDirectory(String directory, String extension, String keyword) {
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return "ERROR: directory not found: " + directory;
        }
        try {
            String glob = "**/*" + (extension.startsWith(".") ? extension : "." + extension);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<String> matches = new ArrayList<>();
            String lowerKeyword = keyword.toLowerCase();

            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!matcher.matches(root.relativize(file))) return FileVisitResult.CONTINUE;
                    try {
                        if (Files.size(file) > MAX_FILE_BYTES) return FileVisitResult.CONTINUE;
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).toLowerCase().contains(lowerKeyword)) {
                                matches.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                            }
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            if (matches.isEmpty()) return "No matches found for '" + keyword + "' in " + directory;
            return matches.stream().limit(200).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.warn("grepInDirectory failed for {}: {}", directory, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Check if a directory exists. Returns 'EXISTS' or 'NOT_FOUND'.")
    public String checkDirectory(String absolutePath) {
        Path path = Path.of(absolutePath).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? "EXISTS" : "NOT_FOUND";
    }
}
