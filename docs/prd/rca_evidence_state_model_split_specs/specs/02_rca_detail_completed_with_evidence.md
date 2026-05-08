# 02 RCA 详情页：有结果 / 有足够证据 Spec

## 页面目标

展示某个已完成 RCA Run 的分析结果。

该页面用于回答：

```text
本次 RCA 当前判断是什么？
候选假设有哪些？
置信度和证据量如何？
哪些证据支持该判断？
下一步建议是什么？
```

## 页面入口

从 RCA Runs 列表点击：

```text
查看
```

进入某个具体 runId 的详情页。

## 顶部区域

展示：

```text
Alert Name / Incident Name
Service
Status
StartedAt
Duration
Trigger Source
Action：重新分析 / 更多
```

示例：

```text
PaymentLatencySpike / payment-service
状态：已完成
```

## 告警上下文

展示：

```text
告警描述
影响路径
namespace
severity
startedAt
```

示例：

```text
告警描述：payment-service latency P95 持续升高，影响用户体验
影响路径：order-service → payment-service
```

## Tabs

```text
概览
候选假设
证据链
时间线
事件
AI 建议
元数据
```

本阶段可优先实现概览，其他 tab 可占位。

## 当前判断卡片

展示：

```text
最可能原因
置信度
证据数量
判断级别
```

示例：

```text
最可能原因：下游依赖延迟导致超时
置信度：0.88
级别：高置信
基于 186 条证据
```

注意：

```text
如果 decisionType = COMPETING_HYPOTHESES，不要把 selectedHypothesis 渲染成唯一根因。
```

## 候选假设 Top N

展示：

```text
排名
假设名称
score
条形分数
```

示例：

```text
1. 下游依赖延迟导致超时 0.88
2. 容器资源竞争 0.42
3. Pod 资源压力 0.28
4. 近期部署引入回归 0.16
```

## 证据概览

可使用 donut / source summary 展示：

```text
Prometheus
Loki
Jaeger
Kubernetes
Alertmanager
```

展示每个 source 的有效证据数和占比。

## 关键解释

列表展示主要 supporting evidence：

```text
payment-service P95 延迟显著升高
Jaeger 追踪显示 downstream 调用耗时集中
Prometheus 指标显示 http_client_duration_seconds 上升
未发现 Pod OOM 或 CPU 饱和
```

## 下一步建议

展示建议项：

```text
验证 downstream 服务健康状态
检查 downstream 依赖 / 第三方 API
扩大观测窗口后进一步分析
```

## AI 建议

仅作为参考，必须标记：

```text
AI 建议仅供参考，不影响上面的确定性 RCA 结论。
```

## 操作

```text
重新分析
查看证据明细
查看事件时间线
返回列表
```

## 禁止行为

```text
1. 不要把 LLM 建议当作 RCA 决策
2. 不要隐藏 competing hypotheses
3. 不要在无 evidence 时展示该有结果页面
4. 不要修改 score，只负责展示
```

## 验收标准

```text
1. 详情页绑定具体 runId
2. 能展示 decision / hypotheses / evidence summary
3. 支持返回 RCA Runs 列表
4. AI 建议与确定性 RCA 结论有明确边界
```