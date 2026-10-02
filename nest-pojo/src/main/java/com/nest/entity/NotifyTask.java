package com.nest.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotifyTask {

    private Long id;

    private String type;

    private String userType;

    private Long userId;

    private String title;

    private String content;

    private Integer retryCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
