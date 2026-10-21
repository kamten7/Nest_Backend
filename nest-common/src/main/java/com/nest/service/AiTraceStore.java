package com.nest.service;

import com.nest.vo.ai.AiTraceRecord;

public interface AiTraceStore {

    void save(AiTraceRecord record);
}
