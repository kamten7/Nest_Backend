package com.nest.dto;

import lombok.Data;

/**
 * 提前支付未来房租请求体。
 */
@Data
public class RentAheadPayDTO {

    /** 提前支付月数（1-5） */
    private Integer months;
}
