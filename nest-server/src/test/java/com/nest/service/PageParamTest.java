package com.nest.service;

import com.nest.common.PageParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PageParam 统一分页收口测试。全项目 PageHelper.startPage 的参数都必须过这里。 */
class PageParamTest {

    @Test
    @DisplayName("page：null/0/负数归 1，超上限截到 1000")
    void pageClamp() {
        assertThat(PageParam.pageOf(null)).isEqualTo(1);
        assertThat(PageParam.pageOf(0)).isEqualTo(1);
        assertThat(PageParam.pageOf(-1)).isEqualTo(1);
        assertThat(PageParam.pageOf(100000)).isEqualTo(1000);
        assertThat(PageParam.pageOf(5)).isEqualTo(5);
    }

    @Test
    @DisplayName("pageSize：null 归 10，截到 [1,100]")
    void pageSizeClamp() {
        assertThat(PageParam.pageSizeOf(null)).isEqualTo(10);
        assertThat(PageParam.pageSizeOf(0)).isEqualTo(1);
        assertThat(PageParam.pageSizeOf(-50)).isEqualTo(1);
        assertThat(PageParam.pageSizeOf(100000)).isEqualTo(100);
        assertThat(PageParam.pageSizeOf(20)).isEqualTo(20);
    }
}
