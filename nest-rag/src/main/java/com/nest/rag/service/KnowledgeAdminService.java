package com.nest.rag.service;

import com.nest.entity.KnowledgeDocument;
import com.nest.vo.KnowledgeDocumentVO;
import com.nest.vo.RetrievedChunkVO;

import java.util.List;

public interface KnowledgeAdminService {

    KnowledgeDocumentVO ingest(String title, String category, String city, String district,
                               String sourceType, String sourceUri, String content, Long createdBy);

    List<KnowledgeDocumentVO> list(int page, int size);

    void reindex(Long id);

    void delete(Long id);

    List<RetrievedChunkVO> search(String query, String city, String category, int topK);

    KnowledgeDocumentVO toVO(KnowledgeDocument document);
}
