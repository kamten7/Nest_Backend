package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房源标签实体（多对一关联房源）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseTag {

    private Long id;
    /** 房源ID */
    private Long houseId;
    /** 标签名：近地铁/朝南/可短租/有电梯/精装修等 */
    private String tagName;
}
