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
