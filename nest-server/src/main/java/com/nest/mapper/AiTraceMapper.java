package com.nest.mapper;

import com.nest.entity.AiTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiTraceMapper {

    int insert(AiTrace trace);

    List<AiTrace> selectRecent(@Param("limit") int limit);

    Map<String, Object> statsSince(@Param("since") LocalDateTime since);
}
