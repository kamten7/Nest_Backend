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

    /** 申请退租：状态 → 退租申请中，此后不再提醒与缴费；等待租期结束结算押金。 */
    RentOrderVO terminate(Long tenantId, Long orderId, String remark);


    /** 名下租房订单（分页，可按状态过滤）。 */
    PageResult<RentOrderVO> listByLandlord(Long landlordId, Integer status, Integer page, Integer pageSize);

    /** 房东视角订单详情。 */
    RentOrderVO getDetailByLandlord(Long landlordId, Long orderId);

    /** 退租结算（房东确认退押金）。 */
    RentOrderVO settleRefund(Long landlordId, Long orderId, BigDecimal deductAmount, String remark);


    /** 到期前 N 天需要提醒的订单，逐个落去重日志并推送；返回实际推送条数。 */
    int remindDueOrders(LocalDate today);

    /** 待自动退还的退租记录 ID（房东超期未结算）。 */
    List<Long> listAutoRefundDueIds(LocalDate today);

    /** 单条自动退还（全额、不扣款）。独立事务，供任务逐条调用，失败不影响其它记录。 */
    boolean autoRefundOne(Long terminationId);


    /** 房东「在租」订单的押金总额：这部分钱在房东钱包里但不可提现。 */
    BigDecimal lockedDepositOf(Long landlordId);
}
