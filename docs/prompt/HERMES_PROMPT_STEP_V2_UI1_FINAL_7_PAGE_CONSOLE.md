# Hermes Prompt: Step V.2-UI-1 FINAL v2  
## 按 7 个语义化 SVG 实现 SRE Agent Console 前端骨架

## 0. 权威说明

本文件是 Step V.2-UI-1 的最终版提示词 v2。

---

# 1. 必须参考的设计材料

请按以下顺序读取：

```text
1. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/HERMES_PROMPT_STEP_V2_UI1_FINAL_7_PAGE_CONSOLE.md
2. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_5_SVG_SPEC.md
3. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_01_SERVICE_HEALTH.svg
4. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_02_INCIDENT_DETAIL.svg
5. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_03_RCA_ANALYSIS.svg
6. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_04_EVIDENCE_DRILLDOWN.svg
7. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_05_CHAOS_EXPERIMENT.svg
8. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_06_ENVIRONMENT_STATUS.svg
9. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/SRE_AGENT_UI_07_SETTINGS.svg
10. /Users/kriswang/work/projects/ai/sre-production-agent/docs/prompt/a_clean_flat_ui_design_mockup_dashboard_screens.jpg
```

7 个 SVG 对应 7 个页面/区域：

```text
1. 服务健康总览
2. 异常详情 / 关键指标
3. RCA 分析结果
4. 证据明细
5. Chaos 实验配置
6. 环境状态
7. 设置
```

这些 SVG 是语义化 UI 蓝图。请按组件、字段、布局、中文文案实现。

---

# 2. 当前目标

新建独立前端项目：

```text
sre-agent-ui/
```

第一版目标：

```text
实现 SRE Agent Console 前端骨架。
```

前端形态允许两种实现方式：

```text
方案 A：单页 Console + 左侧锚点导航
方案 B：轻量多页面/路由，每个 SVG 对应一个页面
```

推荐先实现方案 A，如果 Hermes 判断方案 B 更清晰，也可以选择方案 B，但必须保持：

```text
1. 7 个菜单都有对应页面/区域
2. 不引入复杂前端架构
3. 不破坏原型图信息结构
4. 构建产物仍是静态文件
```

---

# 3. 左侧菜单

菜单必须包含：

```text
服务健康
RCA 分析
证据明细
Chaos 实验
环境状态
设置
```

菜单对应：

| 菜单项 | 对应 SVG |
|---|---|
| 服务健康 | SRE_AGENT_UI_01_SERVICE_HEALTH.svg |
| RCA 分析 | SRE_AGENT_UI_03_RCA_ANALYSIS.svg |
| 证据明细 | SRE_AGENT_UI_04_EVIDENCE_DRILLDOWN.svg |
| Chaos 实验 | SRE_AGENT_UI_05_CHAOS_EXPERIMENT.svg |
| 环境状态 | SRE_AGENT_UI_06_ENVIRONMENT_STATUS.svg |
| 设置 | SRE_AGENT_UI_07_SETTINGS.svg |

异常详情页面：

```text
SRE_AGENT_UI_02_INCIDENT_DETAIL.svg
```

可以作为：

```text
1. 服务健康页的钻取页面
2. 工作台中的异常详情区域
3. 或独立页面 /incident
```

但不能缺失。

---

# 4. 技术栈

默认使用：

```text
React + TypeScript + Vite
```

样式优先：

```text
普通 CSS / global.css
```

可用依赖：

```text
lucide-react
recharts
```

不要使用：

```text
Next.js
Ant Design
Material UI
复杂状态管理库
复杂 SSR 框架
```

---

# 5. 推荐目录结构

```text
sre-agent-ui/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── api/client.ts
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   ├── TopBar.tsx
│   │   └── ConsoleShell.tsx
│   ├── sections/
│   │   ├── ServiceHealthOverview.tsx
│   │   ├── IncidentDetailPanel.tsx
│   │   ├── RcaAnalysisPanel.tsx
│   │   ├── EvidenceDrilldownPanel.tsx
│   │   ├── ChaosExperimentPanel.tsx
│   │   ├── EnvironmentStatusPanel.tsx
│   │   └── SettingsPanel.tsx
│   ├── components/
│   │   ├── MetricCard.tsx
│   │   ├── StatusBadge.tsx
│   │   ├── SectionCard.tsx
│   │   ├── ServiceTopology.tsx
│   │   ├── EvidenceMatrix.tsx
│   │   ├── HypothesisSummary.tsx
│   │   ├── EnvironmentStatusCard.tsx
│   │   └── SettingRow.tsx
│   ├── data/mockData.ts
│   └── styles/global.css
```

---

# 6. 页面/区域要求

## 6.1 服务健康

参考：

```text
SRE_AGENT_UI_01_SERVICE_HEALTH.svg
```

必须实现：

```text
服务总数
异常服务
健康服务
告警数
影响用户
关键服务健康状态表
服务依赖拓扑
最近告警
```

## 6.2 异常详情 / 关键指标

参考：

```text
SRE_AGENT_UI_02_INCIDENT_DETAIL.svg
```

必须实现：

```text
order-service 异常详情
异常摘要
影响链路
错误率 / P95 延迟 / 流量 / 饱和度
受影响端点 Top
进行 RCA 分析按钮
```

## 6.3 RCA 分析

参考：

```text
SRE_AGENT_UI_03_RCA_ANALYSIS.svg
```

必须实现：

```text
当前判断
竞争假设
候选根因
关键解释
证据概览
AI 假设建议
```

不要使用 near-tie 英文词，使用：

```text
竞争假设
候选根因未唯一收敛
分数接近
```

AI 建议必须标记：

```text
未验证
不改变 RCA 结论
```

## 6.4 证据明细

参考：

```text
SRE_AGENT_UI_04_EVIDENCE_DRILLDOWN.svg
```

必须实现：

```text
Prometheus / Loki / Jaeger / Kubernetes / Alertmanager source cards
Top 证据
Evidence table
导出证据按钮
```

`*_no_signal` 必须显示为：

```text
未收集到有效异常信号
```

不能计入有效异常证据。

## 6.5 Chaos 实验

参考：

```text
SRE_AGENT_UI_05_CHAOS_EXPERIMENT.svg
```

必须实现：

```text
目标服务
故障类型
延迟强度
错误率
持续时间
流量目标
RPS
观测窗口
实验名称
实验描述
实验预览
保存配置
启动实验
停止实验
恢复正常
```

目标服务必须可配置，不要写死 payment-service。

## 6.6 环境状态

参考：

```text
SRE_AGENT_UI_06_ENVIRONMENT_STATUS.svg
```

必须实现：

```text
Prometheus
Loki
Jaeger
Kubernetes
Demo Services
SRE Agent API
```

每个组件显示：

```text
状态
Endpoint / Cluster
响应时间
最近检查
操作按钮
错误信息，如有
```

环境状态的产品作用：

```text
1. 判断 no_signal 是否由环境故障导致
2. 支撑 RCA 可信度
3. 快速确认 observability stack 是否可用
```

## 6.7 设置

参考：

```text
SRE_AGENT_UI_07_SETTINGS.svg
```

必须实现：

```text
waitSeconds
lookbackSeconds
stepSeconds
API Base URL
环境名称
刷新间隔
自动刷新
显示 Raw Evidence
启用 Mock Data
错误率异常阈值
P95 延迟异常阈值
RCA 分数差阈值
```

默认值：

```text
waitSeconds = 30
lookbackSeconds = 300
stepSeconds = 15
apiBaseUrl = http://localhost:8080
environmentName = local-kind-demo
refreshIntervalSeconds = 30
```

---

# 7. API Client

创建：

```text
src/api/client.ts
```

封装：

```text
GET  /api/observability/status
GET  /api/demo-services/status
POST /api/live-scenario/run
GET  /api/live-scenario/latest
POST /api/live-scenario/reset
```

第一版可以使用 mock data，但必须明确标记 mock data，不能伪装真实 API。

---

# 8. 构建集成

新增 Makefile targets：

```text
ui-install
ui-dev
ui-build
ui-copy
```

`ui-copy` 将：

```text
sre-agent-ui/dist/*
```

复制到：

```text
sre-agent-server/src/main/resources/static/
```

旧页面保留为：

```text
index-legacy.html
```

---

# 9. 本阶段不做

不要做：

```text
1. 不重构后端 RCA 逻辑
2. 不进入 Step W
3. 不做 post-probe RCA re-run
4. 不引入复杂 UI 框架
5. 不做权限 / 多租户
6. 不把 Scenario G 写死成唯一能力
7. 不隐藏环境状态和设置页面
```

---

# 10. 验收标准

执行：

```bash
cd sre-agent-ui
npm install
npm run dev
npm run build
```

根目录执行：

```bash
make ui-build
make ui-copy
```

浏览器打开后必须满足：

```text
1. 有左侧深色导航
2. 菜单包含：服务健康、RCA 分析、证据明细、Chaos 实验、环境状态、设置
3. 服务健康页面/区域完整
4. 异常详情页面/区域完整
5. RCA 分析页面/区域完整
6. 证据明细页面/区域完整
7. Chaos 实验页面/区域完整
8. 环境状态页面/区域完整
9. 设置页面/区域完整
10. 页面文案为中文
11. 时间窗口显式展示
12. Chaos 目标服务可配置
13. Evidence 默认摘要展示，raw evidence 可展开
14. 旧 index.html 已保留或可回滚
```

---

# 11. 完成报告

完成后输出：

```text
1. 技术栈选择和理由
2. 新增文件
3. 修改文件
4. 页面/区域结构
5. 与 7 个 SVG 的对应关系
6. API client 说明
7. mock data / real API 接入情况
8. Makefile target
9. 构建结果
10. 当前限制
11. 下一步建议
```
