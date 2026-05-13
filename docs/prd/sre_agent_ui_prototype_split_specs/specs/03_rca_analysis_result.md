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
