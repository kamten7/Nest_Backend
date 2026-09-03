package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 租客实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    private Long id;
    /** 微信 openid（小程序登录） */
    private String openid;
    /** 昵称 */
    private String nickname;
    /** 头像URL */
    private String avatar;
    /** 手机号 */
    private String phone;
    /** 性别：1 男，2 女，0 未知 */
    private Integer gender;
    /** 状态：1 正常，0 禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
