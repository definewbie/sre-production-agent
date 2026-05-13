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
