package com.nest.service;

import com.nest.vo.rag.RetrievedChunkVO;

import java.util.List;

public interface KnowledgeRetrievalService {

    List<RetrievedChunkVO> retrieve(String query, String city, String category, int topK);
}
