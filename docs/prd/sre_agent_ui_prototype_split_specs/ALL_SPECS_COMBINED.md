# SRE Agent UI Prototype Combined Spec


---

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


---

# 02 异常详情 / 关键指标页 Spec

## 页面目标

异常详情页用于展示某个异常服务或告警的关键指标、调用链路和异常上下文。它位于服务健康页和 RCA 分析页之间。

它回答：

```text
哪个服务异常？
异常表现是什么？
关键指标是否异常？
影响路径是什么？
是否需要运行 RCA？
```

## 页面入口

来源：

```text
1. 服务健康页点击异常服务
2. 服务健康页点击 service alert
3. RCA 列表页点击 incident / alert
```

## 顶部信息

展示：

```text
服务名
状态
异常检测时间
持续时间
告警名
severity
namespace
影响链路
```

示例：

```text
order-service 异常详情
当前异常：错误率高、checkout 延迟升高、存在 downstream timeout
影响链路：order-service → payment-service
```

## Tab 区域

建议 tabs：

```text
关键指标
调用链路
资源状态
事件时间线
```

本阶段可先实现关键指标 tab，其他 tab 可作为占位。

## 关键指标卡片

至少展示：

```text
错误率
P95 延迟
流量 RPS
饱和度
```

每个卡片包含：

```text
当前值
同比 / 环比变化
小趋势图
单位
状态颜色
```

如果数据来自 mock estimated，必须标记。

## 异常摘要

展示系统归纳的异常现象：

```text
错误率在 14:20 开始显著上升
P95 延迟在 14:21 开始上升
与 payment-service 调用延迟高度相关
未发现明显 OOM / 重启事件
```

注意：

```text
这里是 anomaly summary，不是 RCA final decision。
```

## 受影响端点

表格列：

```text
端点
错误率
P95 延迟
错误数
请求量
```

示例：

```text
POST /checkout
GET /order
POST /orders
```

## 操作

```text
运行 RCA 分析
返回服务健康
查看证据
```

## 空态

如果该服务没有异常数据：

```text
当前时间窗口内未发现该服务的异常指标
```

操作：

```text
扩大时间窗口
返回服务健康
检查数据源
```

## 禁止行为

```text
1. 不要在异常详情页直接宣称唯一根因
2. 不要把无数据渲染成正常
3. 不要自动运行 Lab Demo
4. 不要从 latest 404 fallback 到 simulate
```

## 验收标准

```text
1. 页面展示服务级异常上下文
2. 指标卡片能区分真实 / mock 数据
3. 点击“运行 RCA 分析”进入 RCA 流程
4. 无数据时显示空态，不展示模拟数据
```


---

# 03 RCA 分析页 Spec

## 页面目标

RCA 分析页用于展示 RCA Runs / Incidents 列表，并支持进入某个 RCA Run 详情。

该页面不应该只是 latest result 页面。未来告警和 RCA Run 会很多，因此必须支持列表态和详情态。

## 页面结构

```text
RCA 分析
├── RCA Runs / Incident 列表
└── RCA Run 详情
```

## RCA 列表态

默认进入列表态，而不是直接展示 latest result。

### 筛选器

```text
时间范围
服务
状态
严重程度
来源：Alertmanager / Lab Demo / Manual
```

### Summary Cards

```text
待分析
分析中
已完成
证据不足
高置信根因
```

### RCA Runs 表格

列：

```text
StartedAt
Alert Name / Incident
Service
Severity
Source
RCA Status
Decision
Confidence
Evidence Count
Actions
```

状态：

```text
NOT_STARTED → 尚未分析
RUNNING     → 分析中
COMPLETED   → 已完成
FAILED      → 失败
```

操作：

```text
NOT_STARTED → 运行 RCA
RUNNING     → 查看进度
COMPLETED   → 查看
FAILED      → 重试
```

## RCA 详情态：未分析

显示：

```text
该告警尚未运行 RCA
```

展示：

```text
Alert Name
Service
Severity
StartedAt
Trigger Source
```

操作：

```text
运行 RCA
返回列表
```

## RCA 详情态：分析中

展示：

```text
RCA 分析进行中...
```

步骤：

```text
收集证据
分析证据
评估假设
生成结论
```

不要提前展示最终结论。

## RCA 详情态：完成且有证据

展示：

```text
当前判断
候选假设 Top N
置信度
score gap
竞争假设说明
关键解释
证据概览
AI 建议
下一步 probe
```

关键要求：

```text
selectedHypothesis 不能渲染成唯一根因，除非 decision 明确支持。
competing_hypotheses 必须说明“候选根因未唯一收敛”。
```

## RCA 详情态：完成但无异常证据

当：

```text
effectiveEvidence = 0
或 decisionType = NO_ANOMALOUS_EVIDENCE
```

显示：

```text
RCA 已完成，但未收集到异常证据
```

可能原因：

```text
时间窗口过小
告警噪声
数据源不可用
服务未被采集
故障已经恢复
```

操作：

```text
扩大时间窗口
检查数据源
重新分析
查看环境状态
```

## Lab Demo 边界

保留 Lab Demo，但按钮必须明确：

```text
运行 Lab Demo RCA
```

只有用户主动点击时才触发。

禁止：

```text
latest 404 → 自动 simulate fallback
no evidence → 自动 simulate evidence
```

## 空态

无 RCA Runs 时：

```text
当前暂无 RCA 分析记录
请从告警或 Lab Demo 触发 RCA
```

## 禁止行为

```text
1. 不要把 simulate 数据当成真实 RCA
2. 不要在 latest 404 时自动运行 simulate
3. 不要修改 RCA scoring / weights
4. 不要在无证据时展示模拟证据
```

## 验收标准

```text
1. RCA 页面默认列表态
2. 支持 NOT_STARTED / RUNNING / COMPLETED / FAILED
3. 无 latest 显示空态
4. Lab Demo 只能手动触发
5. 有结果时展示 RCA 详情
6. 无异常证据时展示 no anomalous evidence 状态
```


---

# 04 证据明细页 Spec

## 页面目标

证据明细页用于查看某个 RCA Run 对应的 evidence，不应该默认展示 latest / simulate evidence。

它回答：

```text
本次 RCA 收集了哪些证据？
证据来自哪些数据源？
哪些证据是有效异常信号？
哪些 source 可用但没有异常信号？
```

## 页面结构

```text
证据明细
├── 选择 RCA Run
└── Evidence Run 详情
```

## 默认状态：未选择 RCA Run

默认不展示 evidence。

显示：

```text
请选择一个 RCA Run 查看证据
```

说明：

```text
当前还没有选择 RCA Run。
你可以从 RCA 分析页选择一个已完成的 RCA Run，
或者使用下拉框选择最近一次 RCA Run。
```

操作：

```text
前往 RCA 分析列表
选择 RCA Run
```

## 选择 RCA Run 后

展示：

```text
Source Matrix
Top Evidence
Raw Evidence Table
Filters
```

## Source Matrix

来源：

```text
Prometheus
Loki
Jaeger / Tracing
Kubernetes
Alertmanager
```

每个 source 展示：

```text
状态
总证据数
有效异常证据数
no_signal 数
主要信号
错误信息，如有
```

状态：

```text
available   → 正常 / 有证据
empty       → 正常 / 无异常信号
unavailable → 不可用
unknown     → 未知
```

重要规则：

```text
Kubernetes available + 0 evidence 是正常情况，不能显示失败。
应显示：Kubernetes 可用，本次未发现 Pod / Runtime 异常。
```

## Top Evidence

默认展示不超过 10 条。

排序：

```text
strong > moderate > weak
排除 no_signal
metadata 靠后或默认排除
source 多样性优先
```

字段：

```text
时间
来源
类型
内容摘要
服务
强度
```

## Raw Evidence Table

必须支持：

```text
source filter
evidenceType filter
service filter
show no_signal toggle
show metadata toggle
pagination
```

默认：

```text
show no_signal = false
show metadata = false
page size = 25
```

不要一次渲染 200+ 条 evidence 到首屏。

## 无异常证据状态

如果 RCA Run 完成但没有有效异常 evidence：

```text
本次 RCA 未收集到异常证据
```

仍然可以展示 Source Matrix，用于说明：

```text
哪些 source 可用
哪些 source 不可用
哪些 source 返回 empty
```

## no_signal 规则

以下不计入有效异常 evidence：

```text
*_no_signal
metric_no_signal
log_no_signal
trace_no_signal
k8s_no_signal
alert_no_signal
```

UI 文案：

```text
未收集到有效异常信号
```

## metadata 规则

以下属于上下文 evidence：

```text
deployment_metadata
pod_metadata
replicaset_metadata
k8s_workload_metadata
```

默认不进入 Top Evidence，但可以在 Raw Evidence 中按开关展示。

## 禁止行为

```text
1. 未选择 RCA Run 时不要展示 latest evidence
2. latest 404 不要 fallback 到 simulate
3. no evidence 不要生成模拟 evidence
4. 不要把 no_signal 计入有效异常证据
5. 不要把 source empty 显示成 source failure
```

## 验收标准

```text
1. 默认显示“请选择一个 RCA Run 查看证据”
2. 选择 RCA Run 后展示真实 evidence
3. 无异常 evidence 时显示 no anomalous evidence 状态
4. Source Matrix 能区分 available / empty / unavailable
5. Raw Evidence 支持 filter / pagination
6. no_signal 和 metadata 处理正确
```


---

# 05 Chaos 实验配置页 Spec

## 页面目标

Chaos 实验配置页用于本地 demo / lab 场景下主动注入故障，验证 RCA 链路。

它不是生产 RCA 的默认入口。

生产路径是：

```text
Alertmanager firing alert → IncidentTask → RCA
```

Chaos 页面是：

```text
Lab Demo / Experiment → 故障注入 → 告警 / evidence → RCA
```

## 页面结构

```text
Chaos 实验配置
├── 故障注入配置
├── 实验预览
├── 操作按钮
└── 安全提示
```

## 故障注入配置

字段：

```text
目标服务
故障类型
延迟毫秒
持续时间
流量目标
RPS
观测窗口
实验名
实验描述
```

目标服务：

```text
order-service
payment-service
inventory-service
```

故障类型：

```text
延迟 latency
错误 error
超时 timeout
资源压力 resource pressure
```

## 实验预览

展示：

```text
目标服务
故障类型
延迟强度
持续时间
流量目标
RPS
观测窗口
```

## 操作

```text
保存配置
启动实验
停止实验
恢复正常
```

按钮语义：

```text
启动实验 → 仅用于 Lab Demo
停止实验 → 停止注入
恢复正常 → 清理故障状态
```

## 安全边界

页面必须明确标记：

```text
仅用于演示环境，请谨慎使用故障注入功能
```

不要让用户误以为这是生产自动修复或生产变更工具。

## 与 RCA 的关系

Chaos 页面可以触发：

```text
故障注入
流量模拟
等待告警触发
```

但 RCA 应优先通过：

```text
Alertmanager alert → IncidentTask
```

或者用户显式点击：

```text
运行 Lab Demo RCA
```

禁止：

```text
1. 页面加载时自动注入故障
2. 页面加载时自动运行 RCA
3. 把 Chaos 实验当成生产 RCA 入口
```

## 空态 / 错误态

如果 demo services 不可用：

```text
无法加载 demo services，请检查本地 kind 环境
```

如果 observability stack 不可用：

```text
观测系统不可用，实验可启动但无法验证 RCA 链路
```

## 验收标准

```text
1. Chaos 页面明确标记 Lab Demo
2. 故障注入必须用户主动触发
3. 配置字段清晰
4. 实验预览与配置一致
5. 与 Alert-driven RCA 的生产路径边界清楚
```

