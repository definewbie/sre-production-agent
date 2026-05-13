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
