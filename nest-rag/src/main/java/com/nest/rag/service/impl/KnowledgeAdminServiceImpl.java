package com.nest.rag.service.impl;

import com.nest.constant.MessageConstant;
import com.nest.entity.rag.KnowledgeDocument;
import com.nest.exception.BusinessException;
import com.nest.rag.mapper.KnowledgeDocumentMapper;
import com.nest.rag.service.KnowledgeAdminService;
import com.nest.rag.service.KnowledgeIngestionService;
import com.nest.service.KnowledgeRetrievalService;
import com.nest.vo.rag.KnowledgeDocumentVO;
import com.nest.vo.rag.RetrievedChunkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
public class KnowledgeAdminServiceImpl implements KnowledgeAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeIngestionService ingestionService;
    private final ObjectProvider<KnowledgeRetrievalService> retrievalServiceProvider;

    @Override
    public KnowledgeDocumentVO ingest(String title, String category, String city, String district,
                                      String sourceType, String sourceUri, String content, Long createdBy) {
        KnowledgeDocument document = ingestionService.ingest(
                title, category, city, district, sourceType, sourceUri, content, createdBy);
        return toVO(document);
    }

    @Override
    public List<KnowledgeDocumentVO> list(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return documentMapper.selectPage((safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public void reindex(Long id) {
        ingestionService.reindex(id);
    }

    @Override
    public void delete(Long id) {
        ingestionService.delete(id);
    }

    @Override
    public List<RetrievedChunkVO> search(String query, String city, String category, int topK) {
        KnowledgeRetrievalService retrievalService = retrievalServiceProvider.getIfAvailable();
        if (retrievalService == null) {
            throw new BusinessException("知识库检索未启用");
        }
        return retrievalService.retrieve(query, city, category, topK);
    }

    @Override
    public KnowledgeDocumentVO toVO(KnowledgeDocument document) {
        if (document == null) {
            return null;
        }
        return KnowledgeDocumentVO.builder()
                .id(document.getId())
                .title(document.getTitle())
                .category(document.getCategory())
                .sourceType(document.getSourceType())
                .sourceUri(document.getSourceUri())
                .city(document.getCity())
                .district(document.getDistrict())
                .status(document.getStatus())
                .chunkCount(document.getChunkCount())
                .version(document.getVersion())
                .errorMsg(document.getErrorMsg())
                .createTime(document.getCreateTime())
                .updateTime(document.getUpdateTime())
                .build();
    }
}
