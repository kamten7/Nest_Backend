package com.nest.service;

import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.entity.House;
import com.nest.exception.BusinessException;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.HouseTagMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.minio.service.MinioService;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.service.impl.HouseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 房源手动上下架状态机测试（白名单 + 条件 UPDATE 守卫）。
 *
 * <p>对应修复：入口白名单只允许 0/1；SQL 带 AND status != 2，
 * 与「确认租房置入在租中」并发时影响行数为 0 → 拒绝，杜绝在租中被下架覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class HouseStatusUpdateTest {

    private static final Long HOUSE_ID = 10L;
    private static final Long LANDLORD_ID = 1L;

    @Mock
    private HouseMapper houseMapper;
    @Mock
    private HouseImageMapper houseImageMapper;
    @Mock
    private HouseTagMapper houseTagMapper;
    @Mock
    private LandlordMapper landlordMapper;
    @Mock
    private MinioService minioService;
    @Mock
    private RentOrderMapper rentOrderMapper;

    @InjectMocks
    private HouseServiceImpl houseService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(LANDLORD_ID);
        BaseContext.setCurrentType("landlord");
        House owned = new House();
        owned.setId(HOUSE_ID);
        owned.setLandlordId(LANDLORD_ID);
        // 归属校验存根仅"放行路径"的用例会用到；白名单用例在进 validateOwnership 前就被拒绝
        lenient().when(houseMapper.selectById(HOUSE_ID)).thenReturn(owned);
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private void rejected(Integer badStatus) {
        assertThatThrownBy(() -> houseService.updateStatus(HOUSE_ID, badStatus))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.HOUSE_STATUS_INVALID);
        verify(houseMapper, never()).updateStatus(HOUSE_ID, badStatus);
    }

    @Test
    @DisplayName("白名单：负数/超范围脏值直接拒绝，SQL 不会被调用")
    void rejectDirtyValues() {
        rejected(-5);
        rejected(99);
    }

    @Test
    @DisplayName("白名单：在租中(2)不属于手动可写状态，直接拒绝")
    void rejectRentedValue() {
        rejected(2);
    }

    @Test
    @DisplayName("白名单：null 拒绝")
    void rejectNull() {
        rejected(null);
    }

    @Test
    @DisplayName("条件 UPDATE 守卫：影响行数 0（刚被置入在租中）→ 抛在租中不可手动改")
    void conditionalGuardRacedToRented() {
        when(houseMapper.updateStatus(HOUSE_ID, 0)).thenReturn(0);

        assertThatThrownBy(() -> houseService.updateStatus(HOUSE_ID, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.HOUSE_RENTED_NO_MANUAL);
    }

    @Test
    @DisplayName("合法路径：上架(1)/下架(0) 正常放行")
    void legalStatusPasses() {
        when(houseMapper.updateStatus(HOUSE_ID, 1)).thenReturn(1);
        when(houseMapper.updateStatus(HOUSE_ID, 0)).thenReturn(1);

        houseService.updateStatus(HOUSE_ID, 1);
        houseService.updateStatus(HOUSE_ID, 0);

        verify(houseMapper).updateStatus(HOUSE_ID, 1);
        verify(houseMapper).updateStatus(HOUSE_ID, 0);
    }
}
