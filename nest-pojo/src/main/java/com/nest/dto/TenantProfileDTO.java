package com.nest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 租客个人信息请求体（昵称 / 手机号 / 头像 / 性别）。
 *
 * <p><b>所有字段都是可选的</b>：只更新「传了值」的字段，未传的保持原值
 * （TenantMapper.update 是动态 SQL，只拼非 null 字段）。
 * 传空串等价于不修改。
 *
 * <p>手机号当前<b>只校验格式与长度</b>，不校验号码是否真实存在/是否本人。
 * 短信验证码校验属于上线后才启用的功能，与微信支付一样先预留（见 TenantServiceImpl.updateProfile 注释）。
 */
@Data
public class TenantProfileDTO {

    /** 手机号（可选）：只校验格式长度，不校验真实性 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 昵称（可选） */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;

    /** 头像 URL（可选，一般由 /user/tenant/avatar 上传后回填） */
    @Size(max = 500, message = "头像地址过长")
    private String avatar;

    /** 性别（可选）：1 男，2 女，0 未知 */
    @Min(value = 0, message = "性别取值不合法")
    @Max(value = 2, message = "性别取值不合法")
    private Integer gender;
}
