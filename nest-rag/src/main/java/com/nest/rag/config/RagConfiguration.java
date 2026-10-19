package com.nest.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
public class RagConfiguration {

    private final RagProperties properties;

    @Bean
    public EmbeddingModel knowledgeEmbeddingModel() {
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> knowledgeEmbeddingStore(EmbeddingModel knowledgeEmbeddingModel) {
        RagProperties.VectorStore v = properties.getVectorStore();
        return PgVectorEmbeddingStore.builder()
                .host(v.getHost())
                .port(v.getPort())
                .database(v.getDatabase())
                .user(v.getUsername())
                .password(v.getPassword())
                .table(v.getTable())
                .dimension(knowledgeEmbeddingModel.dimension())
                .createTable(true)
                .useIndex(false)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .build())
                .build();
    }

    @Bean
    public EmbeddingStoreContentRetriever knowledgeVectorRetriever(
            EmbeddingStore<TextSegment> knowledgeEmbeddingStore,
            EmbeddingModel knowledgeEmbeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(knowledgeEmbeddingStore)
                .embeddingModel(knowledgeEmbeddingModel)
                .maxResults(Math.max(properties.getTopK() * 2, 6))
                .minScore(properties.getMinScore())
                .build();
    }
}
