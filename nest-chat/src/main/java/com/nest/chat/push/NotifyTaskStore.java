package com.nest.chat.push;

/** 推送失败补偿存储（依赖倒置：chat 定义接口，server 落库实现）。 */
public interface NotifyTaskStore {

    void save(String type, String toUserType, Long toUserId, String title, String content);
}
