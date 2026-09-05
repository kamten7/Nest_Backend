package com.nest.dto;

import lombok.Data;

/**
 * 申请退租请求体。
 */
@Data
public class RentTerminateDTO {

    /** 退租原因 */
    private String remark;
}
