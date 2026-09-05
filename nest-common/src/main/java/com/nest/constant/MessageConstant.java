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

    // ==================== 钱包 ====================
    public static final String WALLET_AMOUNT_INVALID = "金额必须大于0";
    public static final String WALLET_BALANCE_INSUFFICIENT = "钱包余额不足";

    // ==================== 租房订单 ====================
    public static final String RENT_ORDER_NOT_FOUND = "租房订单不存在";
    public static final String RENT_ORDER_NOT_OWNER = "不是您的订单，无权操作";
    public static final String RENT_ORDER_STATUS_INVALID = "当前订单状态不允许此操作";
    public static final String RENT_APPOINTMENT_INVALID = "请先完成看房后再确认租房";
    public static final String RENT_ALREADY_EXISTS = "您已存在进行中的租房订单";
    public static final String RENT_PERIOD_INVALID = "只能缴纳下一个待缴周期的房租";
    public static final String RENT_ALREADY_PAID = "该周期房租已缴纳";
    public static final String RENT_AHEAD_MONTHS_INVALID = "提前支付月数须在1-5个月之间";
}
