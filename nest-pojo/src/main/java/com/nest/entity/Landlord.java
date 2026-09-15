package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 房东实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Landlord {

    private Long id;
    private String name;
    private String phone;
    private String avatar;
    private String password;
    private String idNumber;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
