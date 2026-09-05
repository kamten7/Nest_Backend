package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.RentOrderVO;

/**
 * 租房订单服务接口 —— 确认/缴押金/缴租/提前支付/退租/列表/详情。
 *
 * 说明：租客身份由 Controller 从登录态注入，业务层经 BaseContext 获取；
 * 所有缴费均走钱包转账（租客 → 对应房东），余额不足会抛出业务异常。
 */
public interface RentService {

    /** 看房结束确认租房，创建订单（status=1 待缴押金） */
    RentOrderVO confirm(Long appointmentId);

    /** 用钱包缴纳押金，订单进入租房中(2) */
    RentOrderVO payDeposit(Long orderId);

    /** 缴纳下一个待缴周期的房租，nextDuePeriod 后移 1 月 */
    RentOrderVO payRent(Long orderId, String period);

    /** 提前支付未来 N（1-5）个月房租，nextDuePeriod 前移 N 月 */
    RentOrderVO payAhead(Long orderId, Integer months);

    /** 申请退租：停止提醒与缴费，租期结束后由定时任务退押金 */
    RentOrderVO terminate(Long orderId, String remark);

    /** 我的租房订单（分页，可按状态过滤） */
    PageResult<RentOrderVO> listMyOrders(Integer status, Integer page, Integer pageSize);

    /** 订单详情（含缴费记录 + 退租信息） */
    RentOrderVO getDetail(Long orderId);

    // ==================== 房东端 ====================

    /** 房东名下租房订单（分页，可按状态过滤） */
    PageResult<RentOrderVO> listByLandlord(Integer status, Integer page, Integer pageSize);

    /** 房东查看订单详情（含收款记录 + 退租信息） */
    RentOrderVO getDetailAsLandlord(Long orderId);

    /** 房东触发押金退回（仅退租申请中且已购租期结束） */
    RentOrderVO refundDeposit(Long orderId);
}
