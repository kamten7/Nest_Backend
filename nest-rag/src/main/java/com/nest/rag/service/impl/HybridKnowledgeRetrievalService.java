package com.nest.rag.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nest.rag.config.RagProperties;
import com.nest.rag.retriever.KeywordContentRetriever;
import com.nest.service.KnowledgeRetrievalService;
import com.nest.vo.rag.RetrievedChunkVO;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
public class HybridKnowledgeRetrievalService implements KnowledgeRetrievalService {

    private final EmbeddingStoreContentRetriever vectorRetriever;
    private final KeywordContentRetriever keywordRetriever;
    private final RagProperties properties;

    private final Cache<String, List<RetrievedChunkVO>> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @Override
    public List<RetrievedChunkVO> retrieve(String query, String city, String category, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(topK, properties.getTopK()));
        String key = query.trim() + "|" + nullToEmpty(city) + "|" + nullToEmpty(category) + "|" + size;
        return cache.get(key, ignored -> doRetrieve(query, city, category, size));
    }

    private List<RetrievedChunkVO> doRetrieve(String queryText, String city, String category, int topK) {
        Query query = Query.from(queryText);
        List<Content> vectorContents = safeRetrieve(() -> vectorRetriever.retrieve(query), "vector");
        List<Content> keywordContents = properties.isHybrid()
                ? safeRetrieve(() -> keywordRetriever.retrieve(query), "keyword")
                : List.of();

        List<Content> fused;
        if (!vectorContents.isEmpty() && !keywordContents.isEmpty()) {
            fused = ReciprocalRankFuser.fuse(List.of(vectorContents, keywordContents));
        } else if (!vectorContents.isEmpty()) {
            fused = vectorContents;
        } else {
            fused = keywordContents;
        }

        Map<String, RetrievedChunkVO> deduped = new LinkedHashMap<>();
        for (Content content : fused) {
            RetrievedChunkVO vo = toVO(content);
            if (vo == null) {
                continue;
            }
            if (!matches(vo.getCity(), city) || !matches(vo.getCategory(), category)) {
                continue;
            }
            deduped.putIfAbsent(vo.getDocId() + ":" + vo.getChunkIndex(), vo);
        }

        List<RetrievedChunkVO> result = new ArrayList<>(deduped.values());
        result.sort(Comparator.comparing(
                (RetrievedChunkVO vo) -> vo.getScore() == null ? 0.0 : vo.getScore()).reversed());
        if (result.size() > topK) {
            result = result.subList(0, topK);
        }
        log.debug("RAG 检索: query='{}', city={}, category={}, vector={}, keyword={}, 返回 {} 条",
                abbreviate(queryText), city, category, vectorContents.size(), keywordContents.size(), result.size());
        return result;
    }

    private List<Content> safeRetrieve(java.util.function.Supplier<List<Content>> supplier, String source) {
        try {
            List<Content> contents = supplier.get();
            return contents == null ? List.of() : contents;
        } catch (Exception e) {
            log.warn("RAG {} 检索失败，按空结果处理: {}", source, e.getMessage());
            return List.of();
        }
    }

    private RetrievedChunkVO toVO(Content content) {
        if (content == null || content.textSegment() == null) {
            return null;
        }
        TextSegment segment = content.textSegment();
        Metadata metadata = segment.metadata();
        Object score = content.metadata() == null ? null : content.metadata().get(ContentMetadata.SCORE);
        return RetrievedChunkVO.builder()
                .docId(parseLong(metadata.getString("docId")))
                .title(metadata.getString("title"))
                .category(metadata.getString("category"))
                .city(metadata.getString("city"))
                .district(metadata.getString("district"))
                .chunkIndex(metadata.getInteger("chunkIndex"))
                .content(segment.text())
                .source(metadata.getString("source"))
                .score(score instanceof Number number ? number.doubleValue() : null)
                .build();
    }

    private static boolean matches(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual == null || actual.isBlank() || expected.trim().equals(actual);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String abbreviate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "...";
    }
}
