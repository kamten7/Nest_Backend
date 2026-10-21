package com.nest.rag.service;

import com.nest.entity.rag.KnowledgeDocument;

public interface KnowledgeIngestionService {

    KnowledgeDocument ingest(String title, String category, String city, String district,
                             String sourceType, String sourceUri, String content, Long createdBy);

    void reindex(Long docId);

    void delete(Long docId);
}
