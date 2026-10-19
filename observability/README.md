# 可观测性

## 启动

```bash
# 先在 .env 里设置 NEST_PROMETHEUS_PORT / NEST_GRAFANA_PORT / NEST_GRAFANA_PASSWORD（可选）
docker compose --profile obs up -d
```

| 服务 | 地址 | 说明 |
|------|------|------|
| Prometheus | `http://localhost:9091` | 抓取 `backend:9090/actuator/prometheus` |
| Grafana | `http://localhost:3000` | 默认 admin / `${NEST_GRAFANA_PASSWORD:-admin}`，数据源已自动配置 |

## 指标

| 指标 | 说明 |
|------|------|
| `nest_ai_requests_total{channel,outcome}` | AI 对话次数（成功/失败） |
| `nest_ai_latency_seconds{channel}` | AI 对话总耗时 |
| `http_server_requests_seconds` | Spring MVC 接口 QPS / p95 / 错误率 |
| `jvm_*` / `process_*` | JVM 内存、GC、线程、CPU |
| `hikaricp_*` / `druid_*` | 连接池（如已启用对应 binder） |

AI 的**单次明细**（工具调用、token、延迟、错误）不在 Prometheus 里，而是落在 `ai_trace` 表，
通过 `GET /admin/ai/traces` 与 `GET /admin/ai/stats` 查询，避免高基数标签。

## Grafana 面板建议

- AI：requests/s、成功率、p95 latency、token/s
- HTTP：按 URI 的 QPS / p95 / 5xx
- JVM：heap、GC pause、threads
- 业务：登录失败数（如有指标）、提现结果、通知重试

后续会把 dashboard JSON 放进 `observability/grafana/dashboards/` 并通过 provisioning 自动加载。
