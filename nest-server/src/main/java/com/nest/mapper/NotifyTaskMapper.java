package com.nest.mapper;

import com.nest.entity.NotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotifyTaskMapper {

    int insert(NotifyTask task);

    List<NotifyTask> selectPending(@Param("limit") int limit);

    int markSent(@Param("id") Long id);

    int incrementRetry(@Param("id") Long id);
}
