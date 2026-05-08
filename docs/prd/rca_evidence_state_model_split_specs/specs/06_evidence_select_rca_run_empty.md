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