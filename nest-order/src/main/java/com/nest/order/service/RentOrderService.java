package com.nest.order.service;

import com.nest.common.PageResult;
import com.nest.vo.RentOrderVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 租房订单服务。 */
public interface RentOrderService {


    /** 看房结束后确认租房，生成订单（状态=待缴押金）。 */
    RentOrderVO confirmRent(Long tenantId, Long appointmentId);

    /** 用钱包余额缴纳押金：扣租客、入房东，订单进入「租房中」。 */
    RentOrderVO payDeposit(Long tenantId, Long orderId);

    /** 我的租房订单（分页，可按状态过滤）。 */
    PageResult<RentOrderVO> listByTenant(Long tenantId, Integer status, Integer page, Integer pageSize);

    /** 订单详情（含缴费记录与退租信息）。 */
    RentOrderVO getDetail(Long tenantId, Long orderId);

    /** 缴纳当期租金（period 缺省取订单的 nextDuePeriod，且必须与之一致）。 */
    RentOrderVO payRent(Long tenantId, Long orderId, String period);

    /** 提前支付未来 N 期（1–5）租金，一次扣款、落 N 条缴费记录、共享同一 bizNo。 */
    RentOrderVO payAhead(Long tenantId, Long orderId, Integer months);

    /**
     * 申请退租：状态 → 退租申请中，此后不再提醒与缴费。
     * 生效期记为申请当月（视为已住满，该月租金不退）；生效期之后、已预付的整月记为可退，
     * 与押金一起在结算时退回。
     */
    RentOrderVO terminate(Long tenantId, Long orderId, String remark);

    /** 放弃租房：仅待缴押金(1) 可放弃 → 已取消(5)，并把房源恢复为上架。 */
    RentOrderVO cancelOrder(Long tenantId, Long orderId);


    /** 名下租房订单（分页，可按状态过滤）。 */
    PageResult<RentOrderVO> listByLandlord(Long landlordId, Integer status, Integer page, Integer pageSize);

    /** 房东视角订单详情。 */
    RentOrderVO getDetailByLandlord(Long landlordId, Long orderId);

    /** 退租结算（房东在冷却期满后执行）：押金按扣款后余额退回，未消耗的预付租金另行退回。 */
    RentOrderVO settleRefund(Long landlordId, Long orderId, BigDecimal deductAmount, String remark);


    /** 到期前 N 天需要提醒的订单，逐个落去重日志并推送；返回实际推送条数。 */
    int remindDueOrders(LocalDate today);

    /** 待自动结算的退租记录 ID（退租申请满冷却期、房东仍未结算）。 */
    List<Long> listAutoRefundDueIds(LocalDate today);

    /** 单条自动结算（押金全额不扣款 + 未消耗的预付租金一并退回）。独立事务，供任务逐条调用，失败不影响其它记录。 */
    boolean autoRefundOne(Long terminationId);


    /** 待缴押金超时的订单 ID（创建已超过 timeoutMinutes 分钟仍未缴押金，房源一直被占着）。 */
    List<Long> listExpiredPendingDepositIds(int timeoutMinutes);

    /**
     * 超时自动取消单笔订单：待缴押金(1) → 已取消(5)，并把房源从「在租中」恢复为「上架」。
     * 独立事务，供任务逐条调用，失败不影响其它记录；返回是否真的取消了。
     */
    boolean autoCancelExpiredOrder(Long orderId, int timeoutMinutes);


    /**
     * 房东「在租」订单的锁定金额总额 = 押金 + 未消耗的预付租金。
     * 这部分钱在房东钱包里但不可提现，须留住以备退租结算时退回租客。
     */
    BigDecimal lockedAmountOf(Long landlordId);
}
