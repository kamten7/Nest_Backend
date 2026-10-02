package com.nest.service.impl;

import com.nest.chat.push.NotifyTaskStore;
import com.nest.entity.NotifyTask;
import com.nest.mapper.NotifyTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyTaskStoreImpl implements NotifyTaskStore {

    private final NotifyTaskMapper notifyTaskMapper;

    @Override
    public void save(String type, String toUserType, Long toUserId, String title, String content) {
        try {
            notifyTaskMapper.insert(NotifyTask.builder()
                    .type(type)
                    .userType(toUserType)
                    .userId(toUserId)
                    .title(title)
                    .content(content)
                    .build());
            log.info("推送失败落补偿任务: type={}, toType={}, toId={}", type, toUserType, toUserId);
        } catch (Exception e) {
            log.error("补偿任务落库失败: type={}, toType={}, toId={}", type, toUserType, toUserId, e);
        }
    }
}
