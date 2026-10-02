package com.nest.service;

import com.nest.vo.ReviewVO;

import java.util.List;

/** 评论只读查询能力（供 AI 工具等只读消费方使用，依赖倒置：接口在下层、实现由 server 提供） */
public interface ReviewQueryService {

    List<ReviewVO> latestByHouse(Long houseId, int limit);
}
