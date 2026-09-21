package com.nest.order.controller;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.RentConfirmDTO;
import com.nest.dto.RentPayAheadDTO;
import com.nest.dto.RentPayRentDTO;
import com.nest.dto.RentTerminateDTO;
import com.nest.order.service.RentOrderService;
import com.nest.vo.RentOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 租客端租房订单接口（URL 与 miniapp/api/rent.js 逐一对齐）。 */
@Slf4j
@RestController
@RequestMapping("/user/rent")
@RequiredArgsConstructor
@Tag(name = "租客端-租房订单", description = "确认租房/缴押金/缴租/提前支付/退租/列表详情")
public class RentOrderUserController {

    private final RentOrderService rentOrderService;

    @PostMapping("/confirm")
    @Operation(summary = "确认租房", description = "看房结束后创建租房订单，状态=待缴押金")
    public Result<RentOrderVO> confirm(@Valid @RequestBody RentConfirmDTO dto) {
        return Result.success("已生成租房订单",
                rentOrderService.confirmRent(BaseContext.getCurrentId(), dto.getAppointmentId()));
    }

    @PostMapping("/{orderId}/pay-deposit")
    @Operation(summary = "缴纳押金", description = "用钱包余额缴纳押金，订单进入租房中")
    public Result<RentOrderVO> payDeposit(@PathVariable Long orderId) {
        return Result.success("押金已缴纳", rentOrderService.payDeposit(BaseContext.getCurrentId(), orderId));
    }

    @GetMapping("/my")
    @Operation(summary = "我的租房订单", description = "分页查询，可按状态过滤")
    public Result<PageResult<RentOrderVO>> my(@RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(rentOrderService.listByTenant(BaseContext.getCurrentId(), status, page, pageSize));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "含缴费记录与退租信息")
    public Result<RentOrderVO> detail(@PathVariable Long orderId) {
        return Result.success(rentOrderService.getDetail(BaseContext.getCurrentId(), orderId));
    }

    @PostMapping("/{orderId}/pay-rent")
    @Operation(summary = "缴纳当期租金", description = "period 缺省取订单的 nextDuePeriod")
    public Result<RentOrderVO> payRent(@PathVariable Long orderId,
                                       @Valid @RequestBody(required = false) RentPayRentDTO dto) {
        String period = dto == null ? null : dto.getPeriod();
        return Result.success("租金已缴纳", rentOrderService.payRent(BaseContext.getCurrentId(), orderId, period));
    }

    @PostMapping("/{orderId}/pay-ahead")
    @Operation(summary = "提前支付未来房租", description = "单次 1–5 个月，一次扣款并落多条缴费记录")
    public Result<RentOrderVO> payAhead(@PathVariable Long orderId,
                                        @Valid @RequestBody RentPayAheadDTO dto) {
        return Result.success("已提前支付", rentOrderService.payAhead(BaseContext.getCurrentId(), orderId, dto.getMonths()));
    }

    @PostMapping("/{orderId}/terminate")
    @Operation(summary = "申请退租", description = "生效期=申请当月(视为已住满不退)；已预付但未消耗的整月租金与押金一并在结算时退回；满冷却期后房东可结算")
    public Result<RentOrderVO> terminate(@PathVariable Long orderId,
                                         @Valid @RequestBody(required = false) RentTerminateDTO dto) {
        String remark = dto == null ? null : dto.getRemark();
        return Result.success("退租申请已提交", rentOrderService.terminate(BaseContext.getCurrentId(), orderId, remark));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "放弃租房", description = "仅待缴押金订单可用：置取消并把房源恢复上架")
    public Result<RentOrderVO> cancel(@PathVariable Long orderId) {
        return Result.success("已放弃租房", rentOrderService.cancelOrder(BaseContext.getCurrentId(), orderId));
    }
}
