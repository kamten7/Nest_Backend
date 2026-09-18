package com.nest.dto;

import lombok.Data;

/** 租客简要信息（房东侧订单页展示「租客信息 / 联系租客」用，批量查避免 N+1） */
@Data
public class TenantBriefDTO {

    private Long tenantId;
    private String tenantName;
}
