package com.nest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 租客注册请求体 */
@Data
public class TenantRegisterDTO {

    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Min(value = 0, message = "性别取值不合法")
    @Max(value = 2, message = "性别取值不合法")
    private Integer gender;
    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
