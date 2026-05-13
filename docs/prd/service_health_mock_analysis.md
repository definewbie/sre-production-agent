# 服务健康总览 — Mock 数据清单 & 实现方案

**日期**: 2026-05-08  
**分析范围**: `ServiceHealthOverview.tsx` + `client.ts` + 后端 API  
**结论**: 7 项 Mock 数据，后端新增 1 个 Prometheus 查询端点即可消除

---

## 一、数据来源现状

```
                    ┌──────────────────────┐
                    │  ServiceHealthOverview│
                    │       (468 行)        │
                    └──────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                 ▼
    getServiceHealthSummary  getFiringAlerts    (无 API)
    /api/demo-services/status /api/incidents/alerts
         │                        │
    ┌────┴────┐              ┌────┴────┐
    │  REAL   │              │  REAL   │
    │ name    │              │ alerts  │
    │ url     │              │ summary │
    │ health  │              │ (Alertman│
    │ reach.  │              │ ger)    │
    │ fault   │              └─────────┘
    │ topology│
    └────┬────┘
         │ 补齐 mock
    ┌────┴──────────────────┐
    │  MOCK (硬编码)         │
    │  errorRate            │
    │  p95Latency           │
    │  rps (流量)           │
    │  saturation (饱和度)  │
    │  restarts (重启)      │
    │  affectedUsers        │
    │  alertCount (summary) │
    └───────────────────────┘
```

---

## 二、Mock 数据清单（7 项）

| # | 字段 | Mock 值 | 位置 | 备注 |
|---|------|---------|------|------|
| 1 | **错误率 (errorRate)** | `4.7%` / `0.2%` / `0.1%` 硬编码 | `client.ts:752-754` | 三个服务各有固定值 |
| 2 | **错误率趋势 (errorRateTrend)** | `350%` / `20%` / `50%` 硬编码 | 同上 | ↑↓ 方向也硬编码 |
| 3 | **P95 延迟 (p95Latency)** | `1.85s` / `2.42s` / `0.38s` | 同上 | |
| 4 | **P95 延迟趋势 (p95Trend)** | `280%` / `480%` / `10%` | 同上 | |
| 5 | **流量 rps** | `2.1` / `2.0` / `1.8` | 同上 | 后有 Sparkline (纯视觉，无数据) |
| 6 | **饱和度 (saturation)** | `45%` / `38%` / `32%` | 同上 | |
| 7 | **重启次数 (restarts)** | 全部 `0` | 同上 | |
| 8 | **影响用户 (affectedUsers)** | `128` ± `↑12%` | `client.ts:782-783` | 趋势也硬编码 |

**同时标记为 mock 但实际已真实:**
| 字段 | 标记 | 实际 |
|------|------|------|
| alerts 数量 | `alertsSource: 'mock'` | 实际从 `serviceAlerts.length` (Alertmanager) 获取 ✅ |
| alert summary | 代码里 `alerts: 2` 硬编码 | KPI 卡不读这个，已用真实数据 ✅ |

---

## 三、实现方案

### 整体架构

```
  前端                    后端                        Prometheus
  ────                    ────                        ──────────
  ServiceHealthOverview   /api/metrics/services       HTTP API
       │                      │                          │
       ├─ GET /api/demo-services/status (已有)          │
       │   → name, url, health, reachable, fault        │
       │                                                │
       └─ GET /api/metrics/services (新增) ──────────────┘
           → errorRate, p95Latency, rps,               
             saturation, restarts, affectedUsers
```

### Phase 1: 后端 — Prometheus 指标查询端点 (新增)

**新增 Controller**: `MetricsController.java`

```java
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @GetMapping("/services")
    public ServicesMetricsResponse getServicesMetrics(
        @RequestParam(defaultValue = "5m") String range,
        @RequestParam(defaultValue = "1m") String step) {
        // 为每个 demo service 查询 Prometheus
    }
}
```

**Response Model** (`ServicesMetricsResponse`):

```json
{
  "checkedAt": "2026-05-08T12:00:00Z",
  "services": {
    "order-service": {
      "errorRate": 4.7,
      "errorRateTrend": 350,
      "errorRateDirection": "up",
      "p95Latency": 1.85,
      "p95Trend": 280,
      "p95Direction": "up",
      "rps": 2.1,
      "saturation": 45,
      "restarts": 0
    },
    "payment-service": { ... },
    "inventory-service": { ... }
  },
  "affectedUsers": {
    "current": 128,
    "trend": 12,
    "direction": "up"
  }
}
```

**PromQL 查询映射:**

| 字段 | PromQL | 说明 |
|------|--------|------|
| `errorRate` | `sum(rate(http_requests_total{status=~"5..", service="..."}[5m])) / sum(rate(http_requests_total{service="..."}[5m])) * 100` | 5xx / 总量 |
| `errorRateTrend` | 同上但用 `[10m]` vs `[5m]` 对比 | 变化百分比 |
| `p95Latency` | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{service="..."}[5m]))` | P95 延迟 |
| `p95Trend` | 同上，两窗口对比 | |
| `rps` | `rate(http_requests_total{service="..."}[5m])` | 每秒请求 |
| `saturation` | `sum(rate(http_requests_total{service="..."}[1m])) / sum(kube_deployment_spec_replicas{deployment="..."}) / 100` | 粗略估算，或用 thread pool 指标 |
| `restarts` | `kube_pod_container_status_restarts_total{namespace="demo", pod=~"...-*"}` | K8s 重启计数 |
| `affectedUsers` | `sort_desc(rate(demo_affected_users[5m])) + rate(http_requests_total{status=~"5.."}[5m]) * 0.1` | 如有 exposed metric；否则保留为 estimate |

**Prometheus 连接**: 复用现有的 `PrometheusQueryService`（如果已有）或直接用 `RestTemplate` + `PROMETHEUS_URL` 环境变量。

**容错策略**: 
- Prometheus 不可用时返回 `error: "prometheus_unavailable"`，前端降级到 mock
- 单个服务指标缺失时对应字段设为 `null`，前端显示 `-`

### Phase 2: 前端 — client.ts 改造

**修改函数**: `getServiceHealthSummary()`（`client.ts:739-791`）

**改造前**:
```
1. getDemoServicesStatus() → 获取服务列表
2. 硬编码 mockDefaults 补充指标
3. 返回 mixed 数据
```

**改造后**:
```
1. getDemoServicesStatus() → 获取服务列表
2. getServicesMetrics()     → 获取真实指标 (新增)
3. merge: 服务列表 + 真实指标
4. 如果 `/api/metrics/services` 失败，fallback 回 mockDefaults
5. 返回 real 数据 (或降级为 mixed)
```

**新增 API 函数**:
```typescript
// client.ts 新增
export interface ServiceMetricsResponse {
  checkedAt: string
  services: Record<string, ServiceMetricView>
  affectedUsers: { current: number; trend: number; direction: 'up' | 'down' }
}

export interface ServiceMetricView {
  errorRate?: number
  errorRateTrend?: number
  errorRateDirection?: 'up' | 'down'
  p95Latency?: number
  p95Trend?: number
  p95Direction?: 'up' | 'down'
  rps?: number
  saturation?: number
  restarts?: number
}

export async function getServicesMetrics(
  range = '5m', step = '1m'
): Promise<{ data: ServiceMetricsResponse | null; error: string | null }> {
  return request<ServiceMetricsResponse>(
    `/api/metrics/services?range=${range}&step=${step}`
  )
}
```

**合并逻辑** (改写 `getServiceHealthSummary`):

```typescript
export async function getServiceHealthSummary() {
  const demo = await getDemoServicesStatus()
  if (!demo.data) return { data: null, error: demo.error }

  const rawServices = demo.data.services.map(mapDemoService)
  const metricsResult = await getServicesMetrics()

  let source: 'real' | 'mixed' = 'mixed'
  let services: ServiceHealthView[] = rawServices

  if (metricsResult.data) {
    // 成功：合并真实指标
    source = 'real'
    services = rawServices.map(svc => {
      const m = metricsResult.data!.services[svc.name]
      if (!m) return svc
      return {
        ...svc,
        errorRate: m.errorRate !== undefined ? formatPercent(m.errorRate) : undefined,
        errorRateTrend: m.errorRateTrend !== undefined ? formatPercent(m.errorRateTrend) : undefined,
        errorRateDirection: m.errorRateDirection,
        p95Latency: m.p95Latency !== undefined ? formatDuration(m.p95Latency) : undefined,
        p95Trend: m.p95Trend !== undefined ? formatPercent(m.p95Trend) : undefined,
        p95Direction: m.p95Direction,
        rps: m.rps !== undefined ? roundTo1(m.rps) : undefined,
        saturation: m.saturation !== undefined ? Math.round(m.saturation) : undefined,
        restarts: m.restarts ?? 0,
        source: 'real',
      }
    })
  } else {
    // 失败：降级到 mock
    services = rawServices.map(svc => {
      const mock = mockDefaults[svc.name]
      return mock ? { ...svc, ...mock, source: 'mixed' as const } : svc
    })
  }

  // affectedUsers
  const affectedUsers = metricsResult.data?.affectedUsers ?? { current: 128, trend: 12, direction: 'up' }

  // ... 其余逻辑不变
}
```

**辅助函数**:
```typescript
function formatPercent(v: number): string { return v.toFixed(1) + '%' }
function formatDuration(s: number): string { return s < 1 ? (s * 1000).toFixed(0) + 'ms' : s.toFixed(2) + 's' }
function roundTo1(v: number): number { return Math.round(v * 10) / 10 }
```

### Phase 3: 前端 — ServiceHealthOverview.tsx 改造 (最小)

**几乎不需要改动。** 当前组件已正确从 `ServiceHealthView` 取值——

```tsx
// 现有代码 (L266-278) 已处理 errorRate 为 undefined 的情况
{s.errorRate ? ( ... ) : ( <span>-</span> )}

// 现有代码 (L293) 已处理 rps undefined
// 现有代码 (L304) 已处理 saturation undefined
// 现有代码 (L321) 已处理 restarts undefined
```

**唯一需要改的两处:**

1. **Mock 提示条** (L194-198): 当所有指标来自真实 API 时不显示

```tsx
{summary.source === 'mixed' && (  // 改为条件渲染
  <div className="mock-indicator">
    ℹ 部分指标为 Mock Estimated...
  </div>
)}
```

2. **KPI 卡 "影响用户" 标签** (L229): 去掉硬编码 Mock 标记

```tsx
{summary.affectedUsersSource !== 'mock' ? null : (
  <span className="mock-tag">Mock</span>
)}
```

3. **表头 `M` 标记** (L246-250): 去掉 Mock 标记

```tsx
// 改前
<th>错误率 (5m)<sup className="mock-mark">M</sup></th>
// 改后
<th>错误率 (5m)</th>
```

4. **Sparkline** (L297): 当前是写死的 SVG path。如果 Prometheus 有历史数据，可改为基于真实 `rate()` 时序。

### Phase 4: 验收标准

| 验收点 | 验证方式 |
|--------|----------|
| 错误率 与 Prometheus `rate(5xx)/rate(all)` 匹配 | `curl /api/metrics/services` 对比 Prometheus UI |
| P95 延迟 与 `histogram_quantile` 匹配 | 同上 |
| 流量 rps 与 `rate(http_requests_total[5m])` 匹配 | 同上 |
| Prometheus 不可用时降级为 mock | 关掉 Port-forward，刷新页面看 Mock 提示条是否出现 |
| 指标缺失时显示 `-` | 删除一个 demo 服务的 metrics label |
| 自动化测试覆盖 | `npm test` 验证 mock/real 合并逻辑 |

---

## 四、工作量估算

| 阶段 | 工作项 | 估时 |
|------|--------|------|
| Phase 1 | 后端 MetricsController + PromQL | 4h |
| Phase 1 | 容错 + 单元测试 | 2h |
| Phase 2 | client.ts 新增 getServicesMetrics | 1h |
| Phase 2 | 重写 getServiceHealthSummary 合并逻辑 | 2h |
| Phase 3 | ServiceHealthOverview Mock 标记清理 | 0.5h |
| Phase 4 | E2E 验证 + 降级测试 | 1.5h |
| **合计** | | **~11h** |

---

## 五、风险 & 注意事项

1. **Prometheus 指标延迟**: `rate([5m])` 需要至少 5 分钟数据。如果 demo 服务刚重启，指标可能为空 → 降级为 mock 或用 `[1m]` 窗口
2. **饱和度指标**: 没有标准 PromQL。需确认 demo 服务是否暴露了线程池/队列指标，否则改用 CPU throttling 或保留为 mock
3. **重启次数**: 需确认 `kube-state-metrics` 是否在集群中运行。简化为用 `/health` 连续失败检测推算
4. **时间窗口不一致**: demo 服务检查间隔 vs Prometheus scrape interval 可能不同步。前端「最近 5 分钟」按钮需传 `range` 参数
5. **受影响的用户数**: 没有标准指标。可保留为 Estimated（标记 source 不是 mock 而是 estimated），或从 error rate × 假定用户基数计算
