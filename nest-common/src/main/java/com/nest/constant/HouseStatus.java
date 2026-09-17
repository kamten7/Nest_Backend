package com.nest.constant;

/** 房源状态机常量。3 态：上架 / 在租中 / 下架。 */
public final class HouseStatus {

    private HouseStatus() {}

    /** 上架：可被租客预约、确认租房。租客端列表/地图只展示此状态。 */
    public static final int AVAILABLE = 1;

    /** 在租中：已有有效租约，自动由「确认租房」置入；租客端不可见。 */
    public static final int RENTED = 2;

    /** 下架：退租结算后落入此状态，需房东手动重新发布（改回 1）。 */
    public static final int OFFLINE = 0;

    public static String text(int status) {
        return switch (status) {
            case AVAILABLE -> "上架";
            case RENTED -> "在租中";
            case OFFLINE -> "下架";
            default -> "未知";
        };
    }
}
