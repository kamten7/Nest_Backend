package com.nest.rag.service.impl;

import com.nest.constant.MessageConstant;
import com.nest.entity.KnowledgeChunk;
import com.nest.entity.KnowledgeDocument;
import com.nest.exception.BusinessException;
import com.nest.rag.config.RagProperties;
import com.nest.rag.mapper.KnowledgeChunkMapper;
import com.nest.rag.mapper.KnowledgeDocumentMapper;
import com.nest.rag.service.KnowledgeIngestionService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private static final int STATUS_INDEXING = 1;
    private static final int STATUS_INDEXED = 2;
    private static final int STATUS_FAILED = 3;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagProperties properties;

    @Override
    public KnowledgeDocument ingest(String title, String category, String city, String district,
                                    String sourceType, String sourceUri, String content, Long createdBy) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_CONTENT_EMPTY);
        }
        String normalizedTitle = truncate(title == null || title.isBlank() ? "未命名文档" : title.strip(), 200);
        String hash = sha256(normalizedTitle + "\n" + text);
        KnowledgeDocument existing = documentMapper.selectByHash(hash);
        if (existing != null) {
            log.info("知识库内容已存在，跳过: id={}, title={}", existing.getId(), existing.getTitle());
            return existing;
        }

        KnowledgeDocument document = KnowledgeDocument.builder()
                .title(normalizedTitle)
                .category(truncate(category == null || category.isBlank() ? "faq" : category.strip(), 50))
                .sourceType(sourceType == null || sourceType.isBlank() ? "MARKDOWN" : sourceType)
                .sourceUri(truncate(sourceUri, 500))
                .city(blankToNull(city))
                .district(blankToNull(district))
                .status(STATUS_INDEXING)
                .chunkCount(0)
                .contentHash(hash)
                .version(1)
                .content(text)
                .createdBy(createdBy)
                .build();
        documentMapper.insert(document);
        try {
            index(document);
            return document;
        } catch (Exception e) {
            documentMapper.updateIndexStatus(document.getId(), STATUS_FAILED, 0, truncate(e.getMessage(), 500));
            log.error("知识库索引失败: id={}, title={}", document.getId(), document.getTitle(), e);
            throw new BusinessException(MessageConstant.KNOWLEDGE_INDEX_FAILED);
        }
    }

    @Override
    public void reindex(Long docId) {
        KnowledgeDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_DOC_NOT_FOUND);
        }
        documentMapper.updateIndexStatus(docId, STATUS_INDEXING, 0, null);
        try {
            index(document);
        } catch (Exception e) {
            documentMapper.updateIndexStatus(docId, STATUS_FAILED, 0, truncate(e.getMessage(), 500));
            log.error("知识库重建索引失败: id={}", docId, e);
            throw new BusinessException(MessageConstant.KNOWLEDGE_INDEX_FAILED);
        }
    }

    @Override
    public void delete(Long docId) {
        KnowledgeDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_DOC_NOT_FOUND);
        }
        removeVectors(docId);
        chunkMapper.deleteByDocId(docId);
        documentMapper.deleteById(docId);
        log.info("知识库文档已删除: id={}, title={}", docId, document.getTitle());
    }

    private void index(KnowledgeDocument document) {
        documentMapper.updateIndexStatus(document.getId(), STATUS_INDEXING, 0, null);
        chunkMapper.deleteByDocId(document.getId());
        removeVectors(document.getId());

        Metadata metadata = new Metadata()
                .put("docId", String.valueOf(document.getId()))
                .put("title", safe(document.getTitle()))
                .put("category", safe(document.getCategory()))
                .put("city", safe(document.getCity()))
                .put("district", safe(document.getDistrict()))
                .put("source", safe(document.getSourceUri()));

        Document parsed = Document.from(document.getContent(), metadata);
        List<TextSegment> segments = DocumentSplitters
                .recursive(properties.getChunkSize(), properties.getChunkOverlap())
                .split(parsed);

        int index = 0;
        for (TextSegment segment : segments) {
            if (segment.text() == null || segment.text().isBlank()) {
                continue;
            }
            Embedding embedding = embeddingModel.embed(segment).content();
            String embeddingId = embeddingStore.add(embedding, segment);
            chunkMapper.insert(KnowledgeChunk.builder()
                    .docId(document.getId())
                    .chunkIndex(index)
                    .content(segment.text())
                    .contentHash(sha256(segment.text()))
                    .embeddingId(embeddingId)
                    .build());
            index++;
        }

        if (index == 0) {
            throw new IllegalStateException("文档切分后没有有效内容");
        }
        documentMapper.updateIndexStatus(document.getId(), STATUS_INDEXED, index, null);
        document.setStatus(STATUS_INDEXED);
        document.setChunkCount(index);
        log.info("知识库索引完成: id={}, title={}, chunks={}", document.getId(), document.getTitle(), index);
    }

    private void removeVectors(Long docId) {
        try {
            embeddingStore.removeAll(metadataKey("docId").isEqualTo(String.valueOf(docId)));
        } catch (Exception e) {

            log.warn("清理旧向量失败（可忽略，reindex 会重建）: docId={}, {}", docId, e.getMessage());
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
