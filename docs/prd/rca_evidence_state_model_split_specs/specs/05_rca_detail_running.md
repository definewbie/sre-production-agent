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