# cpu_spike 注入模式 — 后续待办

## 背景
当前 Chaos 实验覆盖了 error injection、OOM kill 等场景，但缺少「流量激增 → CPU 飙升 → 5xx」这一故障模式。

区别于已实现的 `service_internal_error`（应用内部逻辑错误如空指针、配置错误，无资源压力），`cpu_spike` 关注的是**资源耗尽**导致的降级。

## 设计要点
- 新增 chaos 注入类型：`cpu_pressure`（模拟 CPU 高负载）
- demo services 增加 CPU 压力端点（如 `/fault/cpu-spike?seconds=30&cores=2`）
- Prometheus 采集 CPU 指标（`METRIC_CPU_USAGE_SPIKE` 等）
- 诊断模式：可新建 `capacity_saturation` 或扩展现有 `pod_oom_killed` 覆盖 CPU 场景

## 诊断模式证据组合（待细化）
- METRIC_CPU_USAGE_SPIKE — CPU 利用率激增
- METRIC_ERROR_RATE_SPIKE — 伴随 5xx
- LOG_HTTP_5XX — 5xx 日志
- METRIC_MEMORY_USAGE_NORMAL — 排除 OOM
- METRIC_GC_FREQUENCY_HIGH — GC 频繁（可选）

## 实现路径
1. demo services: 新增 `/fault/cpu-spike` 端点
2. sre-agent-prometheus-provider: 注册 CPU 指标查询
3. sre-agent-core: 新建 `capacity_saturation` 诊断模式或扩展现有模式
4. 测试: ScenarioGVerificationTest
5. 前端: chaos 面板增加 cpu_spike 注入按钮

## 关联
- service_internal_error (已实现，2026-05-11)
- pod_oom_killed (已有，覆盖内存饱和)
