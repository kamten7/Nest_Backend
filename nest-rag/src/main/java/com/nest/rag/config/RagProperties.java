package com.nest.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nest.rag")
public class RagProperties {

    private boolean enabled = false;

    private boolean ingestOnStartup = false;

    private int chunkSize = 450;

    private int chunkOverlap = 60;

    private int topK = 5;

    private double minScore = 0.55;

    private boolean hybrid = true;

    private VectorStore vectorStore = new VectorStore();

    @Data
    public static class VectorStore {
        private String host = "localhost";
        private int port = 5433;
        private String database = "nest_rag";
        private String username = "nest";
        private String password = "nest";
        private String table = "nest_knowledge_embedding";
    }
}
