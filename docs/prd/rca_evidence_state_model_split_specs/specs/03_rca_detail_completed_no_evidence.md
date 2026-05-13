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