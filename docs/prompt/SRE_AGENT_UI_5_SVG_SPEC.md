# SRE Agent Console 5 页面语义化 SVG 说明

## 目的

原始原型图中包含 5 个主要页面/区域。为了便于 Hermes/LLM 识别和实现，已经拆分为 5 个独立的语义化 SVG：

1. `SRE_AGENT_UI_01_SERVICE_HEALTH.svg` — 服务健康总览
2. `SRE_AGENT_UI_02_INCIDENT_DETAIL.svg` — 异常详情 / 关键指标
3. `SRE_AGENT_UI_03_RCA_ANALYSIS.svg` — RCA 分析结果
4. `SRE_AGENT_UI_04_EVIDENCE_DRILLDOWN.svg` — 证据明细
5. `SRE_AGENT_UI_05_CHAOS_EXPERIMENT.svg` — Chaos 实验配置

这些 SVG 不是自动矢量化，而是语义化重建：
- 页面标题是可读文本
- 表格字段是可读文本
- 卡片、状态、badge、按钮、拓扑、证据表都用结构化 SVG 表达
- 更适合 Hermes 按组件实现

## 推荐给 Hermes 的使用顺序

1. 先读 `HERMES_PROMPT_STEP_V2_UI1_SINGLE_PAGE_CONSOLE_FROM_PROTOTYPE.md`
2. 再读本说明文档
3. 再逐个打开 5 个 SVG
4. 最后参考原始 PNG/JPEG 的视觉风格

## 页面关系

第一阶段可以实现为单页 Console 中的五个 section：

- 服务健康总览
- 异常详情 / 关键指标
- RCA 分析结果
- 证据明细
- Chaos 实验配置

左侧导航可以作为锚点导航，后续再拆成路由页面。

## 关键产品原则

1. 服务健康是 SRE 第一视角。
2. 异常详情用于从服务健康钻取关键指标。
3. RCA 分析展示判断、候选假设和 AI 建议。
4. Evidence 是 drill-down，不默认展开全部 raw evidence。
5. Chaos 是独立配置区域，不嵌入 RCA 面板。
6. 时间窗口必须在 UI 中显式展示。
7. 不要把 Scenario G 写死成唯一能力。
