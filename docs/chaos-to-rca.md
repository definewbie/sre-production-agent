# Chaos 故障注入 → 告警触发 → RCA 排查：端到端流程

## 概述

本文档描述 SRE Agent 如何通过 **故障注入 → 指标采集 → 告警触发 → 确定性 RCA** 完成完整的可观测性验证闭环。

核心链路：

```
故障注入（/fault API）
    ↓
Demo 服务产生异常指标（Micrometer → Prometheus）
    ↓
Prometheus Alert 规则触发 → Alertmanager 收到告警
    ↓
SRE Agent 拉取告警 → 告警分类（AlertRelevanceClassifier）
    ↓
SERVICE_ALERT → 构建 IncidentTask → 收集四源证据
    ↓
确定性 RCA → 基础决策（不可变） + 可选 LLM 辅助假设
```

---

## 1. 故障注入

### 1.1 注入方式

每个 Demo 服务暴露 `POST /fault` REST API，支持运行时注入，无需重启。

| 故障类型 | 参数 | 效果 |
|---------|------|------|
| `latency` | `delayMs`（默认 2000） | 所有请求 sleep 指定毫秒 |
| `error` | `errorRate`（0.0-1.0）+ `statusCode`（默认 500） | 按比例返回错误状态码 |
| `crash` | 无 | 所有请求返回 503 |
| `timeout` | `timeoutMs`（默认 5000） | 模拟超时（由 LiveScenarioService 注入） |

实现原理：`FaultInjectionFilter`（Spring WebFilter）拦截所有请求，根据 `AtomicReference<FaultConfig>` 状态决定注入行为。

### 1.2 注入示例

```bash
# 通过 SRE Agent 代理注入 2s 延迟到 payment-service
curl -X POST http://localhost:8080/api/demo/services/payment-service/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"latency","enabled":true,"delayMs":2000}'

# 直接注入 50% 错误率到 inventory-service
curl -X POST http://localhost:8083/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"error","enabled":true,"errorRate":0.5,"statusCode":500}'

# 清除故障
curl -X POST http://localhost:8080/api/demo/services/payment-service/fault \
  -H 'Content-Type: application/json' \
  -d '{"type":"none","enabled":false}'
```

### 1.3 安全约束

- **运行时生效**：服务重启自动清除
- **单服务隔离**：不会跨服务级联
- **完全可逆**：`POST /fault {"enabled":false}` 清除
- **无数据损坏**：只影响响应时间/状态码
- **无资源耗尽**：crash 模式返回 503，不 kill 进程

---

## 2. 指标采集

### 2.1 Demo 服务指标

每个 Demo 服务通过 Micrometer + Prometheus registry 暴露标准 Spring Boot 指标：

| 指标 | 含义 | 故障影响 |
|------|------|---------|
| `http_server_requests_seconds` | 服务端延迟直方图 | latency 注入 → p95/p99 飙升 |
| `http_server_requests_seconds_count{status=5xx}` | 5xx 错误计数 | error/crash 注入 → 计数飙升 |
| `http_client_requests_seconds` | 下游调用延迟 | 下游 latency → 上游 p95 上升 |
| `checkout_requests_total` | 业务请求总量 | 不变 |
| `checkout_errors_total` | 业务错误总量 | error/crash → 增加 |

### 2.2 Prometheus 采集链路

```
Demo Service (:8081/8082/8083)
    /actuator/prometheus
        ↓ ServiceMonitor（demo namespace）
Prometheus（observability namespace）
    跨 namespace 服务发现
    scrape_interval: 15s
        ↓
Prometheus Alert 规则评估
        ↓
Alertmanager
```

### 2.3 时间窗口

故障注入后需要等待 Prometheus 完成至少 1-2 个 scrape 周期（~15-30s）才能采集到异常指标。`LiveScenarioService` 默认 `effectiveWait = max(waitSeconds, 15)` 秒。

---

## 3. 告警触发

### 3.1 Prometheus 告警规则

告警规则部署在 Prometheus 配置中，当指标超过阈值时触发：

| 规则名 | 条件 | 对应故障 |
|--------|------|---------|
| HighErrorRate | `rate(http_server_requests_seconds_count{status=5xx}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.1` | error/crash |
| HighLatencyP95 | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2.0` | latency |
| ServiceDown | `up{job="demo-services"} == 0` for 1m | crash |
| HighRequestLatency | `rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m]) > 1.0` | latency/timeout |

### 3.2 Alertmanager 接收

Prometheus 触发的告警发送到 Alertmanager：

```
Prometheus alert → Alertmanager /api/v2/alerts
    ↓ Alertmanager 分组/去重/路由
    ↓
SRE Agent 通过 AlertmanagerClient 拉取
    GET /api/v2/alerts?filter=...
```

### 3.3 告警数据结构

Alertmanager 告警标准字段：

```json
{
  "labels": {
    "alertname": "HighErrorRate",
    "service": "order-service",
    "namespace": "demo",
    "severity": "warning"
  },
  "annotations": {
    "summary": "order-service error rate > 10%"
  },
  "state": "active",
  "startsAt": "2025-01-15T10:30:00Z",
  "fingerprint": "abc123"
}
```

---

## 4. 告警分类

### 4.1 AlertRelevanceClassifier

SRE Agent 拉取告警后，通过 `AlertRelevanceClassifier` 进行分类，决定哪些告警可以触发业务 RCA：

| 分类 | 判定规则 | RCA 资格 |
|------|---------|---------|
| `SERVICE_ALERT` | demo namespace 或 order/payment/inventory 服务 | ✅ 可触发 RCA |
| `PLATFORM_ALERT` | Node/Kube/etcd 前缀，platform namespace/job | ❌ 平台告警 |
| `WATCHDOG_ALERT` | alertname == "Watchdog" | ❌ 自检告警 |
| `UNSUPPORTED_ALERT` | 无法映射到已知服务 | ❌ 不支持 |
| `IGNORED_ALERT` | 被静默或忽略规则排除 | ❌ 已忽略 |

### 4.2 分类优先级（按顺序评估）

1. **Watchdog** → 直接标记为 WATCHDOG_ALERT
2. **平台告警名前缀**（Node/Kube/etcd）→ PLATFORM_ALERT
3. **平台告警名包含**（Clock/Node/Etcd）→ PLATFORM_ALERT
4. **Demo 服务名匹配** → SERVICE_ALERT
5. **Demo namespace + 非 platform job** → SERVICE_ALERT
6. **Platform namespace** → PLATFORM_ALERT
7. **Platform job** → PLATFORM_ALERT
8. **TargetDown 特殊处理** → 检查目标是否为 demo 服务
9. **默认** → UNSUPPORTED_ALERT

### 4.3 设计原则

只有 `SERVICE_ALERT` 可以触发业务 RCA。Watchdog/Node/etcd 告警即使在 Alertmanager 中 firing，也不会触发 SRE Agent 的 RCA 流程。

---

## 5. RCA 排查

### 5.1 触发 RCA

当 `SERVICE_ALERT` 被识别后，SRE Agent 执行完整 RCA 流程：

```
SERVICE_ALERT → IncidentTask 构建
    ↓
四源证据收集：
  1. Prometheus（指标）
  2. Loki（日志）
  3. Jaeger（链路）
  4. Kubernetes（资源状态）
    ↓
EvidenceNormalizer → 统一证据格式
    ↓
InvestigationWorkflow → 确定性 RCA
    ↓
baseDecision（不可变）
    ↓
[可选] LLM Hypothesis Proposal（仅建议，不修改决策）
```

### 5.2 LiveScenarioService 端到端

通过 `POST /api/live-scenario/run` 可以一键执行完整流程：

```bash
# Simulation 模式（无需 K8s，使用 fixture 数据）
curl http://localhost:8080/api/live-scenario/simulate

# Live 模式（需要 kind 集群 + demo services）
curl -X POST http://localhost:8080/api/live-scenario/run \
  -H 'Content-Type: application/json' \
  -d '{
    "mode": "live",
    "faultMode": "latency",
    "faultParams": {"latencyMs": 2000},
    "waitSeconds": 30,
    "runLlmProposal": true
  }'
```

### 5.3 Live 模式执行流程

| 阶段 | 动作 | 耗时 |
|------|------|------|
| Pre-flight | 检查 demo services 可达性 | <1s |
| Phase 1: 注入 | payment-service 注入故障 + 生成流量 | ~1s |
| Phase 1: 等待 | 等待 metrics 传播（Prometheus scrape） | 15-30s |
| Phase 2: 采集 | 从 Prometheus/Loki/Jaeger/K8s 收集证据 | 5-15s |
| Phase 3: 构建 | 构建 IncidentTask | <1s |
| Phase 4: RCA | 确定性 InvestigationWorkflow | <1s |
| Phase 5: LLM | [可选] LLM 假设提议 | 5-30s |
| Phase 6: 清理 | 重置故障到 normal | <1s |

### 5.4 结果查看

```bash
# 查看最新结果
curl http://localhost:8080/api/live-scenario/latest

# 查看指定结果
curl http://localhost:8080/api/live-scenario/{scenarioId}
```

---

## 6. Scenario G：Payment Latency → Order Error Spike

当前实现的预设场景。

### 6.1 场景描述

```
payment-service 注入 2s 延迟
    ↓
order-service 调用 payment-service /charge 超时
    ↓
order-service 错误率上升
    ↓
Prometheus HighLatencyP95 + HighErrorRate 触发
    ↓
Alertmanager 发送告警
    ↓
SRE Agent 识别为 SERVICE_ALERT
    ↓
RCA 结论：DOWNSTREAM_LATENCY → payment-service 是根因
```

### 6.2 支持的故障模式

| faultMode | 注入目标 | 默认参数 | 预期 RCA 结论 |
|-----------|---------|---------|--------------|
| `latency` | payment-service | 2000ms | DOWNSTREAM_LATENCY |
| `error` | payment-service | 50% errorRate | DOWNSTREAM_ERROR |
| `timeout` | payment-service | 5000ms timeout | DOWNSTREAM_TIMEOUT |
| `normal` | 无注入 | — | HEALTHY（无告警） |

---

## 7. 快速验证清单

### 7.1 最小验证（Simulation 模式，无需 K8s）

```bash
# 启动后端
cd /Users/kriswang/work/projects/ai/sre-production-agent
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn spring-boot:run -pl sre-agent-server -Dspring-boot.run.jvmArguments="--enable-preview"

# 启动前端
cd sre-agent-ui && npx vite --port 5173

# 运行模拟场景
curl http://localhost:8080/api/live-scenario/simulate | jq .
```

### 7.2 完整验证（Live 模式，需要 kind 集群）

```bash
# 1. 部署 observability stack
make obs-install

# 2. 部署 demo services
make demo-build demo-deploy demo-traffic

# 3. 端口转发
make demo-port-forward

# 4. 运行 live 场景
curl -X POST http://localhost:8080/api/live-scenario/run \
  -H 'Content-Type: application/json' \
  -d '{"mode":"live","faultMode":"latency","waitSeconds":30}'

# 5. 在 UI 查看
open http://localhost:5173
```

---

## 相关文档

- [Demo Services](demo-services.md) — Demo 服务拓扑和部署
- [Fault Injection API](fault-injection.md) — 故障注入 API 参考
- [Alertmanager 告警规则](alertmanager-rules.md) — 告警规则与 Prometheus 指标关联
