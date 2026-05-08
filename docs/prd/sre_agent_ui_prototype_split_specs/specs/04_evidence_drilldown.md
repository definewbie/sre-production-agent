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
