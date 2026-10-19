package com.nest.rag.mapper;

import com.nest.entity.KnowledgeChunk;
import com.nest.rag.domain.KnowledgeChunkHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {

    int insert(KnowledgeChunk chunk);

    int deleteByDocId(@Param("docId") Long docId);

    List<KnowledgeChunk> selectByDocId(@Param("docId") Long docId);

    long countByDocId(@Param("docId") Long docId);

    List<KnowledgeChunkHit> searchKeyword(@Param("query") String query, @Param("limit") int limit);
}
