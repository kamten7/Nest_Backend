package com.nest.service;

public interface AiQuotaService {

    boolean tryAcquire(Long tenantId, int dailyLimit);
}
