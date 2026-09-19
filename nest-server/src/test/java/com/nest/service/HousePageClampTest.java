package com.nest.service;

import com.nest.dto.HouseQueryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数收口测试：setter 里 clamp，任何反序列化路径都过这把闸。
 * 防止 pageSize=100000 一次捞全表（/user/house/list 是匿名可达接口）。
 */
class HousePageClampTest {

    private final HouseQueryDTO dto = new HouseQueryDTO();

    @Test
    @DisplayName("pageSize 超上限被截到 100")
    void pageSizeClampedToMax() {
        dto.setPageSize(100000);
        assertThat(dto.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("pageSize 非法值归一到边界")
    void pageSizeNormalized() {
        dto.setPageSize(0);
        assertThat(dto.getPageSize()).isEqualTo(1);
        dto.setPageSize(-3);
        assertThat(dto.getPageSize()).isEqualTo(1);
        dto.setPageSize(null);
        assertThat(dto.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("page 非法值归一到 1")
    void pageNormalized() {
        dto.setPage(0);
        assertThat(dto.getPage()).isEqualTo(1);
        dto.setPage(-1);
        assertThat(dto.getPage()).isEqualTo(1);
        dto.setPage(null);
        assertThat(dto.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("合法值原样通过")
    void legalValuesPassThrough() {
        dto.setPage(5);
        dto.setPageSize(20);
        assertThat(dto.getPage()).isEqualTo(5);
        assertThat(dto.getPageSize()).isEqualTo(20);
    }
}
