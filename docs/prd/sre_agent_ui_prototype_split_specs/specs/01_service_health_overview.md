# 01 服务健康总览页 Spec

## 页面目标

服务健康总览页是 SRE Agent 的第一入口，用于回答：

```text
当前关键服务是否健康？
哪些服务异常？
是否有当前服务范围内的活跃告警？
是否需要进入 RCA 分析？
```

该页面不直接做 RCA 结论，只做服务状态、拓扑和告警入口。

## 主要区域

### 1. 顶部概览指标

展示：

```text
服务总数
异常服务数
健康服务数
告警数
影响用户数
```

要求：

```text
1. 真实字段来自后端 API 的 reachable / health / faultConfig / alert summary
2. 如果指标是 mock estimated，必须明确标记 Mock / Estimated
3. 不能把估算值伪装成真实监控值
```

### 2. 关键服务健康状态表

列：

```text
服务名称
状态
错误率
P95 延迟
流量 RPS
饱和度
最近重启
操作
```

状态规则：

```text
healthy     → 正常
degraded    → 异常 / 降级
down        → 不可达
unknown     → 未知
```

注意：

```text
错误率、P95、RPS、饱和度如果仍为估算值，要带 Mock / Estimated 标记。
```

### 3. 服务依赖拓扑

展示当前链路：

```text
order-service → payment-service → inventory-service
```

节点展示：

```text
服务名
状态
关键指标摘要，例如错误率 / 延迟
```

边或节点颜色应反映状态：

```text
green  → normal
orange → degraded
red    → abnormal/down
gray   → unknown
```

### 4. 活跃告警区域

只展示当前服务范围内的 service alerts：

```text
relevance = SERVICE_ALERT
rcaEligible = true
```

不要展示：

```text
Watchdog
NodeClockNotSynchronising
etcdInsufficientMembers
etcdMembersDown
非 demo service 的 TargetDown
```

这些应该转移到环境状态页，或在本页显示“已隐藏 N 条平台 / 监控系统告警”。

每条 service alert 展示：

```text
alertName
service
severity
status
startedAt
操作：触发 RCA 分析
```

## 空态

如果没有 service-scoped active alert：

```text
当前服务范围内无活跃告警
```

如果 Alertmanager 不可用：

```text
Alertmanager 不可用，无法读取当前告警
```

## 操作

```text
刷新
查看全部告警
触发 RCA 分析
进入异常详情
```

## 禁止行为

```text
1. 不要把平台告警显示成业务服务告警
2. 不要对 ineligible alert 显示“触发 RCA 分析”
3. 不要把 mock 指标当成真实指标
4. 不要在本页直接展示完整 RCA 结果
```

## 验收标准

```text
1. 服务健康页只展示当前服务范围内的告警
2. 平台 / 监控系统告警不会污染主告警列表
3. Mock Estimated 指标有明确标记
4. 点击 eligible alert 可以进入 RCA 触发流程
5. 页面能区分 healthy / degraded / down / unknown
```
