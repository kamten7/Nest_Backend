package com.nest.constant;

public final class MessageConstant {

    private MessageConstant() {}

    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String PASSWORD_ERROR = "密码错误";
    public static final String LOGIN_TOO_MANY_ATTEMPTS = "登录尝试次数过多，请稍后再试";
    public static final String ACCOUNT_DISABLED = "账号已被禁用，请联系管理员";
    public static final String NOT_LOGIN = "用户未登录";
    public static final String NO_PERMISSION = "无权限操作";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String UNKNOWN_ERROR = "系统繁忙，请稍后再试";

    public static final String HOUSE_NOT_FOUND = "房源不存在或已下架";
    public static final String HOUSE_NOT_OWNER = "不是您的房源，无权操作";
    public static final String IMAGE_UPLOAD_EMPTY = "上传文件不能为空";

    public static final String FILE_TYPE_NOT_ALLOWED = "不支持的文件类型，仅允许 jpg / jpeg / png / gif / webp / bmp";

    public static final String FILE_SIZE_EXCEED = "文件过大，图片不能超过 10MB";

    public static final String FILE_CONTENT_INVALID = "文件内容与扩展名不符，请上传真实图片";

    public static final String FILE_READ_FAILED = "文件读取失败，请重试";
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
    public static final String AI_CHAT_IN_PROGRESS = "上一轮对话还在进行，请等它结束后再发新消息";
    public static final String AI_MEMORY_CLEARED = "对话已清空";
    public static final String AI_QUOTA_EXCEEDED = "今天的 AI 对话次数已用完，请明天再来或联系客服";
    public static final String AI_INPUT_INVALID = "请用正常方式描述你的找房需求";
    public static final String REVIEW_DUPLICATE = "您已评价过该房源，请勿重复评价";
    public static final String REVIEW_NOT_FOUND = "评论不存在";
    public static final String REVIEW_COMMENT_NOT_FOUND = "回复不存在";
    public static final String REVIEW_NOT_OWNER = "不是您的评论，无权操作";
    public static final String REVIEW_LIKE_DUPLICATE = "不能重复点赞";

    public static final String REVIEW_CONTENT_EMPTY = "评论内容不能为空";

    public static final String REVIEW_CONTENT_TOO_LONG = "评论内容长度不能超过 500 个字符";

    public static final String REVIEW_RATING_INVALID = "评分必须是 1-5 分";

    public static final String REVIEW_HOUSE_FORBIDDEN = "该房源已下架，只有租住过的用户才能发表评论";

    public static final String REVIEW_PARENT_NOT_FOUND = "被回复的评论不存在";
    public static final String CONVERSATION_NOT_FOUND = "会话不存在";
    public static final String MESSAGE_SEND_FAILED = "消息发送失败";
    public static final String MESSAGE_CONTENT_EMPTY = "消息内容不能为空";

    public static final String MESSAGE_TO_USER_INVALID = "消息接收方不存在";

    public static final String MESSAGE_TO_TYPE_INVALID = "消息接收方身份不合法";

    public static final String MESSAGE_TO_SELF = "不能给自己发送消息";

    public static final String MESSAGE_TYPE_INVALID = "消息类型不支持";

    public static final String MESSAGE_CONTENT_TOO_LONG = "消息内容长度不能超过 500 个字符";

    public static final String KNOWLEDGE_CONTENT_EMPTY = "知识库内容不能为空";
    public static final String KNOWLEDGE_INDEX_FAILED = "知识库索引失败，请稍后重试";
    public static final String KNOWLEDGE_DOC_NOT_FOUND = "知识库文档不存在";
    public static final String KNOWLEDGE_FILE_TYPE_INVALID = "知识库仅支持 Markdown / 纯文本文件";
    public static final String KNOWLEDGE_FILE_TOO_LARGE = "知识库文件不能超过 5MB";
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
    public static final String RENT_SETTLE_NOT_DUE = "退租冷却期未满，暂不能结算";
    public static final String RENT_PHONE_REQUIRED = "请先绑定手机号后再租房（我的 → 个人信息）";
    public static final String WALLET_WITHDRAW_LOCKED = "在租订单的押金不可提现，当前可提现金额不足";

    public static final String WALLET_IDEM_KEY_REQUIRED = "缺少幂等键，请刷新页面后重试";

    public static final String WALLET_IDEM_DUPLICATE = "请勿重复提交，该提现申请已受理";

    public static final String WALLET_LOCK_SOURCE_UNAVAILABLE = "提现服务暂不可用，请联系管理员";
    public static final String TERMINATION_ALREADY_APPLIED = "您已提交退租申请";
    public static final String PHONE_ALREADY_REGISTERED = "该手机号已注册";
    public static final String PHONE_INVALID = "手机号格式不正确";
    public static final String PROFILE_UPDATE_FAILED = "个人信息更新失败";

    public static final String PHONE_LOGIN_DISABLED = "手机号登录已关闭，请使用微信登录";

    public static final String RENT_PAY_CONFLICT = "该期租金已缴清或缴费周期已变更，请刷新后重试";

    public static final String RECHARGE_DISABLED = "充值功能暂未开放";

    public static final String WALLET_AMOUNT_EXCEED = "单笔金额超出上限";

    public static final String HOUSE_NOT_RENTABLE = "该房源已被租出或已下架，无法确认租房";

    public static final String HOUSE_RENTED_NO_MANUAL = "房源在租中，无法手动上下架，请先完成退租";

    public static final String HOUSE_STATUS_INVALID = "房源状态参数不合法，仅支持上架或下架";

    public static final String HOUSE_RENTED_CANNOT_DELETE = "房源在租中或仍有未完成的租房订单，无法删除，请先完成退租或取消订单";

    public static final String RENT_ORDER_CANNOT_CANCEL = "仅待缴押金的订单可放弃租房";
}
