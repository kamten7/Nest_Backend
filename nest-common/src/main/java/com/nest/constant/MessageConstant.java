package com.nest.constant;

/**
 * 业务提示消息常量。
 */
public final class MessageConstant {

    private MessageConstant() {}

    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_DISABLED = "账号已被禁用，请联系管理员";
    public static final String NOT_LOGIN = "用户未登录";
    public static final String NO_PERMISSION = "无权限操作";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String UNKNOWN_ERROR = "系统繁忙，请稍后再试";

    public static final String HOUSE_NOT_FOUND = "房源不存在或已下架";
    public static final String HOUSE_NOT_OWNER = "不是您的房源，无权操作";
    public static final String IMAGE_UPLOAD_EMPTY = "上传文件不能为空";
    public static final String APPOINTMENT_DUPLICATE = "您已预约过该房源，请勿重复预约";
    public static final String APPOINTMENT_NOT_FOUND = "预约不存在";
    public static final String APPOINTMENT_STATUS_INVALID = "当前状态不允许此操作";
    public static final String APPOINTMENT_NOT_OWNER = "不是您的预约，无权操作";
    public static final String FAVORITE_DUPLICATE = "您已收藏过该房源";
    public static final String FAVORITE_NOT_FOUND = "尚未收藏该房源";
    public static final String GEOCODE_FAILED = "地址解析失败，请检查地址是否正确";
    public static final String MAP_PARAM_INVALID = "地图查询参数无效，请提供经纬度范围或中心点加半径";
    public static final String AI_NOT_LOGIN = "抱歉，请先登录后再使用AI助手";
    public static final String AI_SERVICE_ERROR = "抱歉，AI 服务暂时不可用，请稍后再试";
    public static final String REVIEW_DUPLICATE = "您已评价过该房源，请勿重复评价";
    public static final String REVIEW_NOT_FOUND = "评论不存在";
    public static final String REVIEW_COMMENT_NOT_FOUND = "回复不存在";
    public static final String REVIEW_NOT_OWNER = "不是您的评论，无权操作";
    public static final String REVIEW_LIKE_DUPLICATE = "不能重复点赞";
    public static final String CONVERSATION_NOT_FOUND = "会话不存在";
    public static final String MESSAGE_SEND_FAILED = "消息发送失败";
    public static final String MESSAGE_CONTENT_EMPTY = "消息内容不能为空";

    // 合同相关
    public static final String CONTRACT_NOT_FOUND = "合同不存在";
    public static final String CONTRACT_STATUS_INVALID = "当前合同状态不允许此操作";
    public static final String INSPECTION_NOT_FOUND = "检测记录不存在";
    public static final String INSPECTION_ALREADY_SUBMITTED = "该周期检测已提交";
    public static final String WALLET_NOT_FOUND = "钱包不存在";
    public static final String WALLET_BALANCE_INSUFFICIENT = "钱包余额不足";
    public static final String WALLET_AMOUNT_INVALID = "金额必须大于 0";
    public static final String WALLET_FROZEN = "钱包已被冻结，请联系客服";
    public static final String RENT_ORDER_NOT_FOUND = "租房订单不存在";
    public static final String RENT_ORDER_STATUS_INVALID = "当前订单状态不允许此操作";
    public static final String RENT_ORDER_NOT_OWNER = "不是您的订单，无权操作";
    public static final String RENT_APPOINTMENT_NOT_VISITED = "该预约尚未看房完成，无法确认租房";
    public static final String RENT_HOUSE_PRICE_INVALID = "房源租金或押金信息缺失，请联系房东补充";
    public static final String RENT_PAY_PERIOD_INVALID = "缴费周期与订单待缴周期不一致";
    public static final String RENT_DEDUCT_EXCEED_DEPOSIT = "扣款金额不能超过押金总额";
    public static final String RENT_SETTLE_NOT_DUE = "已购租期尚未结束，暂不能结算押金";
    public static final String WALLET_WITHDRAW_LOCKED = "在租订单的押金不可提现，当前可提现金额不足";
    public static final String TERMINATION_ALREADY_APPLIED = "您已提交退租申请";
    public static final String PHONE_ALREADY_REGISTERED = "该手机号已注册";
    public static final String PHONE_INVALID = "手机号格式不正确";
    public static final String PROFILE_UPDATE_FAILED = "个人信息更新失败";
}
