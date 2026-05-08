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