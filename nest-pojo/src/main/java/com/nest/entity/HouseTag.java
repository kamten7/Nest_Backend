package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 房源标签实体（多对一关联房源） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseTag {

    private Long id;
    private Long houseId;
    private String tagName;
}
