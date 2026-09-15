package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 租客实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    private Long id;
    private String openid;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer gender;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
