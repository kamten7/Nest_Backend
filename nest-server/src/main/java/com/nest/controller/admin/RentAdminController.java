package com.nest.controller.admin;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.service.RentService;
import com.nest.vo.RentOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 房东端租房订单接口 —— 名下订单 / 详情 / 押金退回。
 */
@Slf4j
@RestController
@RequestMapping("/admin/rent")
@RequiredArgsConstructor
@Tag(name = "房东端-租房订单", description = "名下订单/详情/押金退回")
public class RentAdminController {

    private final RentService rentService;

    /** 房东名下租房订单（分页） */
    @GetMapping("/my")
    @Operation(summary = "我的租房订单", description = "分页查询房东名下订单，可按状态过滤")
    public Result<PageResult<RentOrderVO>> my(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(rentService.listByLandlord(status, page, pageSize));
    }

    /** 订单详情（含收款记录 + 退租信息） */
    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "查看订单收款记录与退租信息")
    public Result<RentOrderVO> detail(@PathVariable Long orderId) {
        return Result.success(rentService.getDetailAsLandlord(orderId));
    }

    /** 触发押金退回（退租申请中且租期结束） */
    @PostMapping("/{orderId}/refund")
    @Operation(summary = "退回押金", description = "退租申请中且已购租期结束，将押金退回租客钱包")
    public Result<RentOrderVO> refund(@PathVariable Long orderId) {
        return Result.success(rentService.refundDeposit(orderId));
    }
}
