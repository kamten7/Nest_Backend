package com.nest.controller.user;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.RentAheadPayDTO;
import com.nest.dto.RentConfirmDTO;
import com.nest.dto.RentPayRentDTO;
import com.nest.dto.RentTerminateDTO;
import com.nest.service.RentService;
import com.nest.vo.RentOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租客端租房订单接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/rent")
@RequiredArgsConstructor
@Tag(name = "租客端-租房订单", description = "确认租房/缴押金/缴租/提前支付/退租/列表/详情")
public class RentController {

    private final RentService rentService;

    /** 看房结束确认租房，创建订单 */
    @PostMapping("/confirm")
    @Operation(summary = "确认租房", description = "看房结束后确认租房，创建订单（待缴押金）")
    public Result<RentOrderVO> confirm(@RequestBody(required = false) RentConfirmDTO dto) {
        Long appointmentId = dto == null ? null : dto.getAppointmentId();
        return Result.success(rentService.confirm(appointmentId));
    }

    /** 用钱包缴纳押金，订单进入租房中 */
    @PostMapping("/{orderId}/pay-deposit")
    @Operation(summary = "缴纳押金", description = "用钱包余额缴纳押金，订单转为租房中")
    public Result<RentOrderVO> payDeposit(@PathVariable Long orderId) {
        return Result.success(rentService.payDeposit(orderId));
    }

    /** 我的租房订单（分页） */
    @GetMapping("/my")
    @Operation(summary = "我的租房订单", description = "分页查询我的租房订单，可按状态过滤")
    public Result<PageResult<RentOrderVO>> my(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(rentService.listMyOrders(status, page, pageSize));
    }

    /** 订单详情 */
    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "订单详情（含缴费记录与退租信息）")
    public Result<RentOrderVO> detail(@PathVariable Long orderId) {
        return Result.success(rentService.getDetail(orderId));
    }

    /** 缴纳当月租金 */
    @PostMapping("/{orderId}/pay-rent")
    @Operation(summary = "缴纳当月租金", description = "缴纳下一个待缴周期的房租，nextDuePeriod 后移一个月")
    public Result<RentOrderVO> payRent(@PathVariable Long orderId,
                                       @RequestBody(required = false) RentPayRentDTO dto) {
        String period = dto == null ? null : dto.getPeriod();
        return Result.success(rentService.payRent(orderId, period));
    }

    /** 提前支付未来 N（1-5）个月房租 */
    @PostMapping("/{orderId}/pay-ahead")
    @Operation(summary = "提前支付房租", description = "单次最多提前支付 5 个月，nextDuePeriod 前移")
    public Result<RentOrderVO> payAhead(@PathVariable Long orderId,
                                        @RequestBody(required = false) RentAheadPayDTO dto) {
        Integer months = dto == null ? null : dto.getMonths();
        return Result.success(rentService.payAhead(orderId, months));
    }

    /** 申请退租 */
    @PostMapping("/{orderId}/terminate")
    @Operation(summary = "申请退租", description = "退租申请，租期结束后由定时任务退还押金")
    public Result<RentOrderVO> terminate(@PathVariable Long orderId,
                                         @RequestBody(required = false) RentTerminateDTO dto) {
        String remark = dto == null ? null : dto.getRemark();
        return Result.success(rentService.terminate(orderId, remark));
    }
}
