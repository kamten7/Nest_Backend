package com.nest.rag.mapper;

import com.nest.entity.rag.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper {

    int insert(KnowledgeDocument document);

    KnowledgeDocument selectById(@Param("id") Long id);

    KnowledgeDocument selectByHash(@Param("contentHash") String contentHash);

    List<KnowledgeDocument> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    long count();

    int updateIndexStatus(@Param("id") Long id,
                          @Param("status") int status,
                          @Param("chunkCount") int chunkCount,
                          @Param("errorMsg") String errorMsg);

    int deleteById(@Param("id") Long id);
}
