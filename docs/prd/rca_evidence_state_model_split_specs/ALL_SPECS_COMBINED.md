# RCA & Evidence State Model Combined Spec


---

# 01 RCA 分析列表页（主入口）Spec

## 页面目标

RCA 分析列表页是 RCA 模块的主入口，用于展示所有 Incident / RCA Run，并支持筛选、查看、运行 RCA。

该页面用于解决：

```text
1. 告警很多时如何筛选和定位
2. RCA Run 很多时如何查看历史分析
3. 哪些事件尚未分析
4. 哪些事件分析中
5. 哪些事件已完成、有无证据、置信度如何
```

## 页面结构

```text
RCA 分析
├── Tabs
│   ├── RCA Runs
│   └── 告警 / Incident
├── Filters
├── Summary Cards
└── RCA Runs Table
```

## Tabs

```text
RCA Runs
告警 / Incident
```

说明：

```text
RCA Runs：展示已经创建或运行过的 RCA 任务
告警 / Incident：展示从 Alertmanager 读取的 eligible incidents，可运行 RCA
```

## Filters

至少包含：

```text
时间范围
服务
状态
严重程度
来源
```

筛选项建议：

```text
时间范围：最近 1 小时 / 最近 24 小时 / 最近 7 天 / 自定义
服务：全部服务 / order-service / payment-service / inventory-service
状态：全部 / 待分析 / 分析中 / 已完成 / 证据不足 / 失败
严重程度：全部 / 高 / 中 / 低
来源：全部 / Alertmanager / Lab Demo / Manual
```

## Summary Cards

展示：

```text
待分析
分析中
已完成
证据不足
高置信根因
```

这些是当前筛选条件下的统计，不是全局固定值。

## RCA Runs Table

列：

```text
开始时间
Alert Name / Incident
服务
严重程度
来源
RCA 状态
决策结果
置信度
证据数
操作
```

## 状态映射

```text
NOT_STARTED → 待分析
RUNNING     → 分析中
COMPLETED   → 已完成
FAILED      → 失败
```

## 决策结果显示

```text
LIKELY_ROOT_CAUSE        → 已定位
PROBABLE_ROOT_CAUSE      → 大概率
COMPETING_HYPOTHESES     → 竞争假设
INSUFFICIENT_EVIDENCE    → 证据不足
NO_ANOMALOUS_EVIDENCE    → 无异常证据
UNKNOWN                  → -
```

## 操作

```text
NOT_STARTED → 运行 RCA
RUNNING     → 查看
COMPLETED   → 查看
FAILED      → 重试
```

## 空态

当没有 RCA Runs：

```text
当前暂无 RCA 分析记录
请从告警 / Incident 触发 RCA，或手动运行 Lab Demo RCA
```

## Lab Demo 边界

Lab Demo 可以作为按钮存在，但必须明确标记：

```text
运行 Lab Demo RCA
```

不能在列表为空或 latest 404 时自动触发。

## 禁止行为

```text
1. 不要在页面加载时自动 simulate
2. 不要用 mock 数据伪装成真实 RCA Runs
3. 不要将 ineligible Alertmanager alerts 展示为可运行 RCA
4. 不要把 RCA 列表和 RCA 详情混在同一主状态中
```

## 验收标准

```text
1. RCA 分析页默认进入列表态
2. 支持筛选和状态统计
3. 表格能区分待分析 / 分析中 / 已完成 / 失败
4. 操作按钮与状态匹配
5. 无数据时显示空态，不显示模拟数据


---

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

---

# 03 RCA 详情页：分析完成但无异常证据 Spec

## 页面目标

当 RCA Run 已完成，但没有收集到有效异常 evidence 时，展示明确的“无异常证据”状态，而不是伪造结果或展示模拟证据。

## 触发条件

满足任一条件：

```text
RcaRunStatus = COMPLETED
effectiveEvidence = 0

或

decisionType = NO_ANOMALOUS_EVIDENCE
```

## 顶部区域

展示：

```text
Alert / Incident
Service
Status = 证据不足 / 无异常证据
StartedAt
Duration
Trigger Source
```

## 主提示

文案：

```text
RCA 已完成，但未收集到异常证据
```

说明：

```text
系统完成了本次分析流程，但没有发现足够的异常信号支撑明确 RCA 结论。
```

## 可能原因

展示：

```text
时间窗口过小
告警噪声或误报
数据源不可用
服务未被采集
故障已经恢复
```

## 建议操作

```text
扩大时间窗口
检查数据源
重新分析
查看环境状态
```

## 证据区域

可以展示 Source Matrix，但必须明确：

```text
source available
source empty
source unavailable
```

不要展示模拟证据。

## 与 insufficient_evidence 的区别

```text
NO_ANOMALOUS_EVIDENCE：
  没有发现有效异常证据

INSUFFICIENT_EVIDENCE：
  有一些证据，但不足以收敛根因
```

UI 要尽量区分这两种状态。

## 禁止行为

```text
1. 不要 fallback 到 simulate
2. 不要展示 mock evidence
3. 不要强行选出最可能根因
4. 不要显示“已定位根因”
```

## 验收标准

```text
1. completed + zero effective evidence 显示无异常证据状态
2. 页面提供合理下一步操作
3. 不展示模拟结果
4. 可返回列表或重新分析
```

---

# 04 RCA 详情页：尚未分析 Spec

## 页面目标

展示某个 Alert / Incident 已存在，但尚未运行 RCA 的状态。

## 触发条件

```text
RcaRunStatus = NOT_STARTED
```

或者：

```text
Incident 存在，但没有关联 RCA Run
```

## 主提示

文案：

```text
尚未运行 RCA 分析
```

说明：

```text
当前告警未进行根因分析，请点击按钮开始分析。
```

## 展示信息

```text
Incident / Alert Name
Service
Severity
StartedAt
Trigger Source
Namespace
```

## 操作

```text
运行 RCA
返回列表
```

## 运行 RCA 的入口

如果 alert rcaEligible = true：

```text
显示“运行 RCA”
```

如果 alert rcaEligible = false：

```text
禁用按钮
显示原因：该告警不在当前服务范围内，不能触发业务 RCA
```

## 禁止行为

```text
1. 不要在进入页面时自动运行 RCA
2. 不要展示模拟结果
3. 不要把 NOT_STARTED 显示成失败
4. 不要默认选 latest
```

## 验收标准

```text
1. 未分析状态清晰
2. 用户可主动运行 RCA
3. ineligible alert 不能运行 RCA
4. 不出现任何模拟 RCA 结果
```

---

# 05 RCA 详情页：分析中 Spec

## 页面目标

展示 RCA Run 正在运行中的状态，给用户可理解的进度反馈。

## 触发条件

```text
RcaRunStatus = RUNNING
```

## 顶部区域

展示：

```text
Incident / Alert Name
Service
Status = 分析中
StartedAt
Elapsed Time
Trigger Source
```

## 进度步骤

展示：

```text
收集证据
分析证据
评估假设
生成结论
```

状态：

```text
done
running
pending
failed
```

## 运行日志

展示最近几条运行日志：

```text
正在从 Prometheus 收集指标...
正在从 Loki 收集日志...
正在从 Jaeger 收集 traces...
正在评估候选假设...
```

## 操作

```text
取消分析（可选）
刷新状态
查看日志
返回列表
```

## 禁止行为

```text
1. 不要提前展示最终 RCA 结论
2. 不要在 running 时显示 completed evidence summary
3. 不要自动 fallback simulate
4. 不要阻塞整个 UI
```

## 验收标准

```text
1. RUNNING 状态有明确进度
2. 页面可刷新
3. 完成后能切换到 completed detail
4. 失败后能切换到 failed 状态
```

---

# 06 证据明细页：选择 RCA Run 空态 Spec

## 页面目标

证据明细页必须绑定某个 RCA Run。未选择 RCA Run 时，不应该展示 latest evidence，也不应该展示 simulate evidence。

## 触发条件

```text
未选择 RCA Run
或
没有可用 RCA Run
或
latest 返回 404
```

## 主提示

文案：

```text
请选择一个 RCA Run 查看证据
```

说明：

```text
当前还没有选择任何 RCA 分析结果。
请选择一个 RCA Run 后查看对应的证据明细。
```

## 操作

```text
前往 RCA 分析列表
选择 RCA Run
```

## 可选下拉

如果已有 RCA Runs，可以提供：

```text
选择 RCA Run
```

下拉展示：

```text
runId / alertName / service / status / startedAt
```

## 无 RCA Runs 时

显示：

```text
当前暂无 RCA 分析记录
请先从告警或 RCA 分析页运行一次 RCA
```

## 禁止行为

```text
1. 不要默认拉 latest 并展示
2. latest 404 不要自动 simulate
3. 不要展示 mock evidence
4. 不要自动运行 Lab Demo
5. 不要把无选择状态显示成错误
```

## 选择 RCA Run 后

进入 evidence detail：

```text
Source Matrix
Top Evidence
Raw Evidence Table
Filters
```

## 验收标准

```text
1. 未选择 run 时显示空态
2. 空态不展示 evidence 表格
3. 用户可以跳转 RCA 分析列表
4. latest 404 不触发 simulate fallback
```
