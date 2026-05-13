# Alertmanager 告警规则与分类体系

## 概述

本文档详细描述 Alertmanager 在 SRE Agent 中的双重角色、告警分类体系、证据映射规则，以及 API 接口参考。

核心组件关系：

```
Prometheus Alert Rule 触发
    ↓
Alertmanager v2 API（/api/v2/alerts）
    ↓
IncidentService.fetchClassifiedAlerts()  ← 拉取模式
    ↓
AlertRelevanceClassifier 5 级分类
    ↓
┌─ SERVICE_ALERT → 可触发 RCA（IncidentService.triggerRcaFromAlert）
│   ↓
│   AlertmanagerIncidentMapper → IncidentTask
│   AlertmanagerEvidenceMapper → Evidence（Evidence Source Role）
│   LiveEvidenceCollector → 四源证据合并
│   InvestigationWorkflow.runFromMemory() → 确定性 RCA
│
├─ PLATFORM_ALERT → 仅展示，不触发 RCA
├─ WATCHDOG_ALERT → 仅展示，不触发 RCA
├─ UNSUPPORTED_ALERT → 仅展示，不触发 RCA
└─ IGNORED_ALERT → 仅展示，不触发 RCA
```

---

## 1. Alertmanager 的双重角色

### 1.1 Trigger Role（告警触发源）

Alertmanager 作为 RCA 流程的**入口触发器**：

- `IncidentService` 通过 `HttpAlertmanagerClient` 拉取当前 firing 告警
- 每个告警经过 `AlertRelevanceClassifier` 分类
- 只有 `SERVICE_ALERT` 类型可触发 `triggerRcaFromAlert()` 流程
- 触发后，告警映射为 `IncidentTask`，进入 RCA 排查

关键代码路径：
```
IncidentController.listFiringAlerts()        → GET /api/incidents/alerts
IncidentController.triggerRcaFromAlert()     → POST /api/incidents/from-alert
```

### 1.2 Evidence Source Role（证据来源）

告警数据同时作为 RCA 的**证据输入**：

- `AlertmanagerEvidenceMapper` 将告警状态映射为语义化 Evidence 对象
- 证据类型包括：firing、resolved、severity、silenced、inhibited、grouped、no_signal
- 证据与其他数据源（Prometheus/Loki/Jaeger/K8s）合并后统一输入 `InvestigationWorkflow`

---

## 2. 告警数据流

### 2.1 完整链路

```
Prometheus Alert Rule 评估
    ↓ HTTP POST
Alertmanager /api/v2/alerts（接收）
    ↓ Alertmanager 分组/去重/路由/抑制/静默
SRE Agent IncidentService（拉取）
    ↓ HttpAlertmanagerClient.getAlerts()
    GET {alertmanager-url}/api/v2/alerts?silenced=false&inhibited=false
    ↓ AlertmanagerResponseParser.parse()
    ↓ JSON → List<AlertmanagerAlert>
    ↓ AlertRelevanceClassifier.classify()
    ↓ 每条告警 → ClassifiedAlert(relevance, rcaEligible, ineligibleReason)
    ↓
AlertView + AlertsResponse（返回给前端）
```

### 2.2 Fixture 模式

当使用 `FixtureAlertmanagerClient` 时（开发/测试环境），告警数据来自 classpath fixture 文件而非真实 Alertmanager：

```
FixtureAlertmanagerClient.getAlerts()
    ↓ 根据 alertName 解析 fixture 文件名
    ↓ 例：HighErrorRate → firing_high_error_rate.json
    ↓ 从 classpath:fixtures/alertmanager/ 加载
    ↓ 返回预定义 JSON
```

Fixture 名称解析逻辑（`AlertmanagerProvider.resolveFixtureName`）：
- 从 `labelMatchers["alertname"]` 提取
- 转小写 + 空格替换为下划线 + `.json`
- 默认回退：`firing_high_error_rate.json`

---

## 3. 告警分类体系

### 3.1 AlertRelevance 5 级分类

| 分类 | 枚举值 | RCA 资格 | 说明 |
|------|--------|---------|------|
| **SERVICE_ALERT** | `SERVICE_ALERT` | ✅ 可触发 | 与 demo 业务服务直接相关的告警 |
| **PLATFORM_ALERT** | `PLATFORM_ALERT` | ❌ 不可 | 平台/基础设施层告警（Node/etcd/Kubelet 等） |
| **WATCHDOG_ALERT** | `WATCHDOG_ALERT` | ❌ 不可 | 告警链路自检告警（Watchdog） |
| **UNSUPPORTED_ALERT** | `UNSUPPORTED_ALERT` | ❌ 不可 | 无法映射到已知服务的告警 |
| **IGNORED_ALERT** | `IGNORED_ALERT` | ❌ 不可 | 被静默/忽略规则排除的告警 |

`AlertRelevance.isRcaEligible()` 实现：
```java
public boolean isRcaEligible() {
    return this == SERVICE_ALERT;
}
```

### 3.2 分类判定规则（按优先级顺序）

`AlertRelevanceClassifier.classify()` 按以下顺序评估，命中即返回：

| 优先级 | 判定条件 | 结果 | ineligibleReason |
|--------|---------|------|------------------|
| 1 | `alertName == "Watchdog"` | `WATCHDOG_ALERT` | "Watchdog 是告警链路自检告警，不应触发业务 RCA" |
| 2 | `alertName` 以 "Node"/"Kube"/"etcd" 开头 | `PLATFORM_ALERT` | "平台告警（{name}），不属于当前业务服务范围" |
| 3 | `alertName` 包含 "Clock"/"Node"/"Etcd" | `PLATFORM_ALERT` | "平台告警（{name}），不属于当前业务服务范围" |
| 4 | `service` ∈ {order-service, payment-service, inventory-service} | `SERVICE_ALERT` | — |
| 5 | `namespace` == "demo" && `job` 不是 platform job | `SERVICE_ALERT` | — |
| 6 | `namespace` ∈ {kube-system, monitoring, observability} | `PLATFORM_ALERT` | "平台命名空间（{ns}），不属于当前业务服务范围" |
| 7 | `job` 匹配 platform job（含 contains） | `PLATFORM_ALERT` | "平台组件（{job}），不属于当前业务服务范围" |
| 8 | `alertName == "TargetDown"` && demo 相关 | `SERVICE_ALERT` | — |
| 9 | `alertName == "TargetDown"` && 非 demo | `PLATFORM_ALERT` | "TargetDown 目标（{service}）不属于当前业务服务范围" |
| 10 | 默认 | `UNSUPPORTED_ALERT` | "告警（{name} / {service}）无法映射到当前支持的业务服务" |

### 3.3 分类常量定义

**Demo 服务集合**：
```java
DEMO_SERVICES = {"order-service", "payment-service", "inventory-service"}
DEMO_NAMESPACES = {"demo"}
```

**Platform 集合**：
```java
PLATFORM_NAMESPACES = {"kube-system", "monitoring", "observability"}
PLATFORM_JOBS = {"prometheus", "alertmanager", "kubelet", "node-exporter",
                 "etcd", "kube-state-metrics", "kube-proxy"}
PLATFORM_ALERT_PREFIXES = {"Node", "Kube", "etcd"}
PLATFORM_ALERT_CONTAINS = {"Clock", "Node", "Etcd"}
```

---

## 4. RCA Eligibility Guard

### 4.1 设计原因

只有 `SERVICE_ALERT` 可触发 RCA，原因如下：

1. **范围控制**：SRE Agent 的 RCA 能力针对业务服务设计，平台告警（NodeDown、etcd 异常等）应由平台团队通过其他工具处理
2. **避免误触发**：Watchdog 是告警系统自身的健康检查，不应产生 RCA 事件
3. **证据相关性**：RCA 收集的四源证据（Prometheus/Loki/Jaeger/K8s）以 demo 服务为目标，平台告警的证据收集方向不同
4. **资源保护**：RCA 流程涉及多数据源查询和计算，限制触发范围可避免资源浪费

### 4.2 Guard 实现位置

`IncidentService.triggerRcaFromAlert()` 中：

```java
// Step 2: RCA Eligibility Guard
ClassifiedAlert classification = relevanceClassifier.classify(alert);
if (!classification.rcaEligible()) {
    return IncidentRcaResultView.failed(id, alert.alertName(),
        alert.service(),
        "该告警不可触发 RCA：" + classification.ineligibleReason());
}
```

---

## 5. 告警证据类型

### 5.1 AlertmanagerEvidenceTypes 常量

`AlertmanagerEvidenceTypes` 定义了 9 种告警证据类型常量，统一映射到 `Evidence.evidenceType`：

| 常量 | 值 | 触发条件 | 默认强度 |
|------|----|---------|---------|
| `ALERT_FIRING` | `alert_firing` | 告警状态为 active/firing | 0.80 |
| `ALERT_RESOLVED` | `alert_resolved` | 告警状态为 resolved | 0.50 |
| `ALERT_STILL_FIRING` | `alert_still_firing` | 持续 firing（预留） | — |
| `ALERT_SEVERITY_HIGH` | `alert_severity_high` | severity 为 critical/high/page | 0.75 |
| `ALERT_GROUPED` | `alert_grouped` | 同服务/namespace 有多条告警 | 0.65 |
| `ALERT_SILENCED` | `alert_silenced` | silencedBy 列表非空 | 0.60 |
| `ALERT_INHIBITED` | `alert_inhibited` | inhibitedBy 列表非空 | 0.60 |
| `ALERT_NEAR_WINDOW` | `alert_near_window` | 接近时间窗口（预留） | — |
| `ALERT_NO_SIGNAL` | `alert_no_signal` | 告警列表为空 | 0.00 |

Source 标识：`AlertmanagerEvidenceTypes.SOURCE = "alertmanager"`

### 5.2 证据映射规则（AlertmanagerEvidenceMapper）

每条告警可产生 1-N 条证据，映射规则：

```
AlertmanagerAlert
├── isFiring()    → alert_firing evidence
├── isResolved()  → alert_resolved evidence
├── severity ∈ {critical, high, page} → alert_severity_high evidence
├── isSilenced()  → alert_silenced evidence
└── isInhibited() → alert_inhibited evidence

List<AlertmanagerAlert>（size > 1）
└── alert_grouped evidence（附加）

List<AlertmanagerAlert>（empty）
└── alert_no_signal evidence（替代）
```

### 5.3 证据通用属性

每条 Evidence 的 `attributes` 包含：

```json
{
  "alertName": "HighErrorRate",
  "service": "order-service",
  "namespace": "demo",
  "severity": "warning",
  "state": "active",
  "startsAt": "2026-04-28T10:08:00Z",
  "endsAt": "2026-04-28T10:15:00Z",
  "fingerprint": "abc123def456",
  "labels": { ... },
  "annotations": { ... },
  "silencedBy": [],
  "inhibitedBy": []
}
```

`alert_grouped` 额外包含：
```json
{
  "groupedAlertCount": 2,
  "groupedAlertNames": ["DownstreamLatencyHigh"]
}
```

---

## 6. Prometheus 指标 → 告警规则映射

### 6.1 告警规则表

| 告警规则名 | Prometheus Metric | 阈值条件 | 对应故障注入类型 | Fixture |
|-----------|------------------|---------|----------------|---------|
| **HighErrorRate** | `http_server_requests_seconds_count{status=5xx}` | `rate(…[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.1` | error / crash | `firing_high_error_rate.json` |
| **HighLatencyP95** | `http_server_requests_seconds_bucket` | `histogram_quantile(0.95, rate(…[5m])) > 2.0` | latency | — |
| **ServiceDown** | `up{job="demo-services"}` | `== 0` for 1m | crash | — |
| **HighRequestLatency** | `http_server_requests_seconds_sum` / `_count` | `rate(…[5m]) > 1.0` | latency / timeout | — |
| **DownstreamLatencyHigh** | `http_client_requests_seconds` | p95 > 2s 到下游服务 | latency（下游） | `grouped_downstream_latency.json` |
| **HighMemoryUsage** | JVM/容器内存指标 | 使用率 > 85% | — | `multiple_alerts.json` |
| **PodCrashLoop** | K8s pod restart count | restart > threshold | — | `multiple_alerts.json` |

### 6.2 告警规则 PromQL 参考

```yaml
# HighErrorRate
- alert: HighErrorRate
  expr: >
    rate(http_server_requests_seconds_count{status=~"5.."}[5m])
    / rate(http_server_requests_seconds_count[5m]) > 0.1
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "{{ $labels.service }} error rate > 10%"

# HighLatencyP95
- alert: HighLatencyP95
  expr: >
    histogram_quantile(0.95,
      rate(http_server_requests_seconds_bucket[5m])
    ) > 2.0
  for: 2m
  labels:
    severity: warning
```

---

## 7. Fixture 数据

### 7.1 Fixture 文件列表

位于 `sre-agent-alertmanager-provider/src/main/resources/fixtures/alertmanager/`：

| 文件 | 告警数 | 告警名 | 服务 | 状态 | 用途 |
|------|-------|--------|------|------|------|
| `firing_high_error_rate.json` | 1 | HighErrorRate | order-service | active | 模拟单条 firing 告警，最基本的 RCA 触发场景 |
| `resolved_high_error_rate.json` | 1 | HighErrorRate | order-service | resolved | 模拟已恢复告警，验证 resolved 状态的解析和证据映射 |
| `grouped_downstream_latency.json` | 2 | DownstreamLatencyHigh | payment-service | active | 模拟同服务多条分组告警（下游 inventory-service + order-service），验证 grouped 证据生成 |
| `multiple_alerts.json` | 3 | HighErrorRate + HighMemoryUsage + PodCrashLoop | order-service | active | 模拟多告警并发场景，包含 silenced 告警（PodCrashLoop），验证多证据类型映射 |
| `empty_alerts.json` | 0 | — | — | — | 模拟无告警场景，验证 `alert_no_signal` 证据生成 |

### 7.2 Fixture 数据结构

每条 fixture 告警遵循 Alertmanager v2 API 响应格式：

```json
{
  "labels": {
    "alertname": "HighErrorRate",
    "service": "order-service",
    "namespace": "demo",
    "severity": "warning"
  },
  "annotations": {
    "summary": "order-service error rate is high",
    "description": "5xx error rate exceeded threshold"
  },
  "startsAt": "2026-04-28T10:08:00Z",
  "endsAt": "0001-01-01T00:00:00Z",
  "status": {
    "state": "active",
    "silencedBy": [],
    "inhibitedBy": []
  },
  "fingerprint": "abc123def456"
}
```

关键字段说明：
- `endsAt = "0001-01-01T00:00:00Z"` → 表示告警仍在 firing（未设置结束时间）
- `status.state` → `active`（firing）或 `resolved`
- `silencedBy` / `inhibitedBy` → 非空列表表示被静默/抑制

---

## 8. API 参考

### 8.1 GET /api/incidents/alerts

获取当前 Alertmanager firing 告警（带分类信息）。

**请求**：
```
GET /api/incidents/alerts
```

**响应** `AlertsResponse`：
```json
{
  "source": "alertmanager",
  "checkedAt": "2026-04-28T10:10:00Z",
  "summary": {
    "totalAlerts": 2,
    "serviceAlerts": 2,
    "platformAlerts": 0,
    "watchdogAlerts": 0,
    "unsupportedAlerts": 0,
    "ignoredAlerts": 0,
    "rcaEligibleAlerts": 2
  },
  "alerts": [
    {
      "fingerprint": "abc123def456",
      "alertName": "HighErrorRate",
      "service": "order-service",
      "namespace": "demo",
      "severity": "warning",
      "state": "active",
      "startsAt": "2026-04-28T10:08:00Z",
      "summary": "order-service error rate is high",
      "relevance": "SERVICE_ALERT",
      "rcaEligible": true,
      "ineligibleReason": null,
      "labels": { ... },
      "annotations": { ... }
    }
  ]
}
```

**错误情况**：
- Alertmanager 不可达 → 返回空列表 `{"source":"alertmanager","summary":{"totalAlerts":0,...},"alerts":[]}`
- Alertmanager 返回非 200 → 同上（异常被 catch，日志 warn）

### 8.2 POST /api/incidents/from-alert

从特定告警触发 RCA。

**请求体** `IncidentRcaTriggerRequest`：

方式 1 — 按 fingerprint 精确匹配：
```json
{
  "fingerprint": "abc123def456",
  "alertName": null,
  "service": null
}
```

方式 2 — 按 alertName + service 匹配：
```json
{
  "fingerprint": null,
  "alertName": "HighErrorRate",
  "service": "order-service"
}
```

**匹配优先级**：fingerprint 优先，alertName+service 次之。

**成功响应** `IncidentRcaResultView`（status=COMPLETED）：
```json
{
  "incidentId": "inc-alertmanager-abc123def456",
  "status": "COMPLETED",
  "triggerSource": "alertmanager",
  "alertName": "HighErrorRate",
  "service": "order-service",
  "namespace": "demo",
  "severity": "warning",
  "startedAt": "2026-04-28T10:08:00Z",
  "decisionType": "CONFIRMED",
  "selectedHypothesisId": "h_error_spike",
  "confidenceScore": 0.85,
  "scoreGap": 0.25,
  "scores": {
    "h_error_spike": 0.85,
    "h_deployment": 0.60
  },
  "reportUrl": "/api/incidents/inc-alertmanager-abc123def456/report",
  "durationMs": 150,
  "errorMessage": null
}
```

**失败响应**（status=FAILED，HTTP 400）：
```json
{
  "incidentId": "inc-alert-1714286400000",
  "status": "FAILED",
  "triggerSource": "alertmanager",
  "alertName": "Watchdog",
  "service": "prometheus",
  "errorMessage": "该告警不可触发 RCA：Watchdog 是告警链路自检告警，不应触发业务 RCA"
}
```

### 8.3 其他 Incident API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/incidents` | GET | 列出所有 incident 记录 |
| `/api/incidents/{incidentId}` | GET | 获取单个 incident + RCA 结果 |
| `/api/incidents/{incidentId}/report` | GET | 获取 RCA markdown 报告 |
| `/api/incidents/{incidentId}/rca` | GET | 获取完整 RCA 数据（LiveScenarioResult 兼容格式） |

---

## 9. 配置参数

### 9.1 Observability 配置

`application.properties` 中的配置项：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `sre-agent.observability.alertmanager-url` | `http://localhost:9093` | Alertmanager v2 API 地址 |
| `sre-agent.observability.prometheus-url` | `http://localhost:9090` | Prometheus 查询 API 地址 |
| `sre-agent.observability.loki-url` | `http://localhost:3100` | Loki 日志查询 API 地址 |
| `sre-agent.observability.trace-url` | `http://localhost:16686` | Jaeger 链路追踪 API 地址 |
| `sre-agent.observability.trace-backend` | `jaeger` | 链路追踪后端类型 |
| `sre-agent.observability.grafana-url` | `http://localhost:3000` | Grafana 面板地址 |

### 9.2 IncidentService 初始化

```java
public IncidentService(Environment env) {
    String alertmanagerUrl = env.getProperty(
        "sre-agent.observability.alertmanager-url", "http://localhost:9093");
    this.alertClient = new HttpAlertmanagerClient(
        AlertmanagerClientConfig.of(alertmanagerUrl));
    // ...
}
```

### 9.3 HttpAlertmanagerClient 查询参数

拉取告警时的请求参数：
- `silenced=false` — 默认排除已静默告警
- `inhibited=false` — 默认排除已抑制告警
- `filter` — 可选的 label matcher 过滤

---

## 10. Demo 服务拓扑

### 10.1 服务架构

```
                    ┌─────────────────────┐
                    │   SRE Agent Server   │
                    │     (:8080)          │
                    │                      │
                    │  IncidentService     │
                    │  LiveScenarioService │
                    │  DemoController      │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              ↓               ↓               ↓
    ┌─────────────────┐ ┌──────────────┐ ┌──────────────────┐
    │  order-service   │ │payment-service│ │ inventory-service │
    │    (:8081)       │ │  (:8082)     │ │   (:8083)        │
    │                  │ │              │ │                   │
    │ POST /checkout   │ │ POST /pay    │ │ GET /inventory    │
    │ POST /fault      │ │ POST /fault  │ │ POST /fault       │
    │ GET /actuator/*  │ │              │ │                   │
    └────────┬─────────┘ └──────┬───────┘ └────────┬─────────┘
             │                  │                   │
             │   /actuator/prometheus              │
             └──────────────────┼───────────────────┘
                                ↓
                    Prometheus (:9090)
                    observability namespace
                                ↓
                    Alertmanager (:9093)
```

### 10.2 服务详情

| 服务 | 端口 | Namespace | 说明 | 关联告警 |
|------|------|-----------|------|---------|
| order-service | 8081 | demo | 订单服务，入口服务，调用 payment/inventory | HighErrorRate, HighMemoryUsage, PodCrashLoop |
| payment-service | 8082 | demo | 支付服务，order-service 下游 | DownstreamLatencyHigh |
| inventory-service | 8083 | demo | 库存服务，payment-service 下游 | DownstreamLatencyHigh |

### 10.3 故障注入与告警对应

```
order-service   → error 注入   → HighErrorRate (5xx > 10%)
payment-service → latency 注入 → DownstreamLatencyHigh (p95 > 2s)
inventory-service → crash 注入 → ServiceDown + 上游 DownstreamLatencyHigh
```

---

## 附录：关键源码文件索引

| 文件 | 职责 |
|------|------|
| `AlertRelevanceClassifier.java` | 告警 5 级分类引擎 |
| `AlertRelevance.java` | 分类枚举定义（含 isRcaEligible） |
| `AlertmanagerEvidenceTypes.java` | 9 种证据类型常量 |
| `AlertmanagerEvidenceMapper.java` | 告警 → Evidence 映射逻辑 |
| `AlertmanagerIncidentMapper.java` | 告警 → IncidentTask 映射 |
| `AlertmanagerProvider.java` | Alertmanager 数据收集编排器 |
| `AlertmanagerAlert.java` | 告警数据 record（含状态判断方法） |
| `IncidentService.java` | 告警驱动的事件管理 + RCA 触发 |
| `IncidentController.java` | Incident REST API 端点 |
| `AlertView.java` | 前端展示用告警视图 record |
| `AlertsResponse.java` | 告警列表响应（含分类统计） |
| `IncidentRcaTriggerRequest.java` | RCA 触发请求体 |
| `IncidentRcaResultView.java` | RCA 结果视图 |
