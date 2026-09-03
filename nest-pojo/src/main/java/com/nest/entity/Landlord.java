package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 房东实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Landlord {

    private Long id;
    /** 房东姓名 */
    private String name;
    /** 手机号（登录账号） */
    private String phone;
    /** 头像URL */
    private String avatar;
    /** 密码（MD5 摘要） */
    private String password;
    /** 身份证号 */
    private String idNumber;
    /** 状态：1 启用，0 禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
