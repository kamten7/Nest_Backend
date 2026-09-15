package com.nest.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建预约请求体。
 */
@Data
public class AppointmentCreateDTO {

    /** 房源 ID */
    @NotNull(message = "房源 ID 不能为空")
    private Long houseId;
    /** 联系电话 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;
    /** 期望看房时间 */
    @NotNull(message = "期望看房时间不能为空")
    @Future(message = "看房时间必须晚于当前时间")
    private LocalDateTime appointmentTime;
    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200 个字符")
    private String remark;
}
