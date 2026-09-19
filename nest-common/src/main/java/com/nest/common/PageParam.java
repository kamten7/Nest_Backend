package com.nest.common;

/**
 * 分页参数统一收口。全项目所有 {@code PageHelper.startPage} 的入参都必须经过这里。
 */
public final class PageParam {

    private PageParam() {}

    public static final int MAX_PAGE = 1000;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** page 收口：null/负数归 1，超上限截到 {@value MAX_PAGE}。 */
    public static int pageOf(Integer page) {
        return (page == null || page < 1) ? 1 : Math.min(page, MAX_PAGE);
    }

    /** pageSize 收口：null 归默认 {@value DEFAULT_PAGE_SIZE}，强制落在 [1, {@value MAX_PAGE_SIZE}]。 */
    public static int pageSizeOf(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }
}
