package com.nest.rag.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class KnowledgeMarkdownParser {

    private KnowledgeMarkdownParser() {
    }

    public record Parsed(String title, String category, String city, String district, String body) {
    }

    public static Parsed parse(String filename, String raw) {
        String text = raw == null ? "" : raw;
        Map<String, String> meta = new HashMap<>();

        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                String frontMatter = text.substring(3, end);
                for (String line : frontMatter.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String key = line.substring(0, colon).trim().toLowerCase();
                        String value = line.substring(colon + 1).trim();
                        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        meta.put(key, value);
                    }
                }
                text = text.substring(end + 4);
            }
        }

        String title = meta.get("title");
        if (title == null || title.isBlank()) {
            title = firstHeading(text).orElseGet(() -> stripExtension(filename));
        }
        return new Parsed(
                title.strip(),
                meta.getOrDefault("category", "faq").strip(),
                meta.getOrDefault("city", "").strip(),
                meta.getOrDefault("district", "").strip(),
                text.strip());
    }

    private static Optional<String> firstHeading(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return Optional.of(trimmed.substring(2).trim());
            }
        }
        return Optional.empty();
    }

    private static String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "未命名文档";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
