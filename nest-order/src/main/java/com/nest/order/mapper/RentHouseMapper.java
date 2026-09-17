package com.nest.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单模块「写房源状态」的 Mapper。
 *
 * <p>与只读的 {@code RentSourceMapper} 分离：房源状态由订单事件驱动流转
 * （确认租房→在租中、放弃租房→恢复上架、退租结算→下架），都在这里集中写入。
 * 全部用条件更新，天然幂等。</p>
 */
@Mapper
public interface RentHouseMapper {

    /** 上架(1) → 在租中(2)。条件更新 ⇒ 原子地防"同一房源被多人同时租下"。 */
    int markRentedIfAvailable(@Param("houseId") Long houseId);

    /** 在租中(2) → 上架(1)。租客放弃租房（待缴押金阶段）时恢复可租。 */
    int markAvailableIfRented(@Param("houseId") Long houseId);

    /** 在租中(2) → 下架(0)。退租结算完成，需房东手动重新发布。 */
    int markOfflineIfRented(@Param("houseId") Long houseId);
}
