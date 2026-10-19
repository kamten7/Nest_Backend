package com.nest.rag.retriever;

import com.nest.rag.domain.KnowledgeChunkHit;
import com.nest.rag.mapper.KnowledgeChunkMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordContentRetriever implements ContentRetriever {

    private static final int KEYWORD_TOP_K = 8;

    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        try {
            List<KnowledgeChunkHit> hits = chunkMapper.searchKeyword(query.text(), KEYWORD_TOP_K);
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            List<Content> contents = new ArrayList<>(hits.size());
            for (KnowledgeChunkHit hit : hits) {
                Metadata metadata = new Metadata()
                        .put("docId", nullToEmpty(hit.getDocId()))
                        .put("title", nullToEmpty(hit.getTitle()))
                        .put("category", nullToEmpty(hit.getCategory()))
                        .put("city", nullToEmpty(hit.getCity()))
                        .put("district", nullToEmpty(hit.getDistrict()))
                        .put("source", nullToEmpty(hit.getSource()));
                if (hit.getChunkIndex() != null) {
                    metadata.put("chunkIndex", hit.getChunkIndex());
                }
                TextSegment segment = TextSegment.from(nullToEmpty(hit.getContent()), metadata);
                contents.add(Content.from(segment,
                        Map.of(ContentMetadata.SCORE, hit.getScore() == null ? 0.0 : hit.getScore())));
            }
            return contents;
        } catch (Exception e) {

            log.warn("关键词检索失败，降级为纯向量检索: {}", e.getMessage());
            return List.of();
        }
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
