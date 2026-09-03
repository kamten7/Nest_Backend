package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

    private Long id;
    /** 租客ID */
    private Long tenantId;
    /** 房源ID */
    private Long houseId;
    /** 创建时间 */
    private LocalDateTime createTime;
}
