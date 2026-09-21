package com.nest.order.controller;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.RentRefundDTO;
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

/** 房东端租房订单接口（URL 与 frontend/src/api/rent.ts 对齐）。 */
@Slf4j
@RestController
@RequestMapping("/admin/rent")
@RequiredArgsConstructor
@Tag(name = "房东端-租房订单", description = "名下订单列表/详情/退租结算退押金")
public class RentOrderAdminController {

    private final RentOrderService rentOrderService;

    @GetMapping("/my")
    @Operation(summary = "名下租房订单", description = "分页查询，可按状态过滤")
    public Result<PageResult<RentOrderVO>> my(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(rentOrderService.listByLandlord(BaseContext.getCurrentId(), status, page, pageSize));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "含收款记录与退租信息")
    public Result<RentOrderVO> detail(@PathVariable Long orderId) {
        return Result.success(rentOrderService.getDetailByLandlord(BaseContext.getCurrentId(), orderId));
    }

    /** 退租结算：把押金与未消耗的预付租金一并退回租客。请求体可省略（等价于不扣款、全额退回押金）。 */
    @PostMapping("/{orderId}/refund")
    @Operation(summary = "退租结算", description = "退租申请满冷却期(SETTLE_GRACE_DAYS)后即可结算；可带扣款金额(从押金扣)，不传=押金全额退回；未消耗的预付租金另行退回")
    public Result<RentOrderVO> refund(@PathVariable Long orderId,
                                      @Valid @RequestBody(required = false) RentRefundDTO dto) {
        String remark = dto == null ? null : dto.getRemark();
        java.math.BigDecimal deduct = dto == null ? null : dto.getDeductAmount();
        log.info("退租结算: landlordId={}, orderId={}, deductAmount={}", BaseContext.getCurrentId(), orderId, deduct);
        return Result.success("退租已结算，押金与预付租金已退回",
                rentOrderService.settleRefund(BaseContext.getCurrentId(), orderId, deduct, remark));
    }
}
