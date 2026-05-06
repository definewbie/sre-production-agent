# Hermes Prompt: Step V.2-UI-1  
## 新建独立前端项目，搭建 SRE Agent 控制台框架

## 0. 当前判断

当前 `sre-agent-server/src/main/resources/static/index.html` 已经承载过多功能：

```text
1. 旧 Scenario E/F demo
2. Live RCA
3. Evidence detail
4. Chaos fault injection
5. Lab status
```

继续在单个 HTML 文件中堆 UI 会导致产品结构腐化。

现在需要新建独立前端项目：

```text
sre-agent-ui/
```

目标不是马上完成全部功能，而是先把产品信息架构和前端工程框架搭起来。

---

## 1. 重要参考：UI 设计图

请参考我之前确认的 UI 设计图实现页面框架。

设计图文件：

```text
docs/a_clean_flat_ui_design_mockup_dashboard_screens.png
```

如果该图片在当前会话或项目目录中可见，请优先参考它的布局和视觉方向。

设计图的核心结构包括：

```text
1. 左侧深色导航栏
2. 服务健康总览
3. 异常详情 / 关键指标
4. RCA 分析结果
5. 证据明细 Evidence Drill-down
6. Chaos 实验配置
7. 实验环境 / Lab Status
```

注意：不要机械复刻图片里的所有细节。要保留它的产品结构和信息层级。

---

## 2. 产品定位

这个 UI 不是消费级 demo 页面，而是面向 SRE / 平台工程师的运维分析工作台。

核心原则：

```text
1. SRE 第一视角是关键服务和关键指标是否异常。
2. RCA 是从异常服务钻取后的分析能力。
3. Evidence 是辅助判断证据，不应该作为第一屏主内容。
4. Chaos / 故障注入属于实验配置，不应该和 RCA 主分析混在一起。
5. AI 建议必须 advisory-only，不改变 RCA 结论。
6. 时间窗口必须显式展示。
```

---

## 3. 产品信息架构

一级导航固定为：

```text
1. 服务健康
2. RCA 分析
3. 证据明细
4. Chaos 实验
5. 实验环境
6. 设置
```

对应页面：

```text
/health
/rca
/evidence
/chaos
/lab
/settings
```

如果第一阶段不引入路由库，也可以用前端 state 切换页面，但 URL 路由更好。

---

## 4. 技术栈要求

以简单、稳定、可维护为主。

默认技术栈：

```text
React + TypeScript + Vite
```

推荐依赖：

```text
react
react-dom
typescript
vite
lucide-react
recharts
```

样式方案二选一：

```text
方案 A：Tailwind CSS
方案 B：普通 CSS Modules / 单独 CSS 文件
```

优先选择简单可控的方案。不要引入过重 UI 框架。

除非有明确理由，不要使用：

```text
Next.js
Ant Design
Material UI
复杂状态管理库
复杂图表库
服务端渲染框架
```

### 是否允许你自己选择技术栈？

可以提出替代方案，但必须先说明理由，并且满足：

```text
1. 比 React + Vite 更简单
2. 不增加后端复杂度
3. 不影响静态构建
4. 适合 dashboard / table / topology / card 组件
5. 能通过 npm run build 产出静态文件
```

如果不能证明更优，就使用默认方案：

```text
React + TypeScript + Vite
```

---

## 5. 项目结构

新增目录：

```text
sre-agent-ui/
```

推荐结构：

```text
sre-agent-ui/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── api/
│   │   └── client.ts
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   └── TopBar.tsx
│   ├── pages/
│   │   ├── ServiceHealthPage.tsx
│   │   ├── RcaAnalysisPage.tsx
│   │   ├── EvidencePage.tsx
│   │   ├── ChaosExperimentPage.tsx
│   │   ├── LabStatusPage.tsx
│   │   └── SettingsPage.tsx
│   ├── components/
│   │   ├── MetricCard.tsx
│   │   ├── ServiceHealthTable.tsx
│   │   ├── ServiceTopology.tsx
│   │   ├── EvidenceMatrix.tsx
│   │   ├── HypothesisTable.tsx
│   │   ├── StatusBadge.tsx
│   │   └── SectionCard.tsx
│   ├── data/
│   │   └── mockData.ts
│   └── styles/
│       └── global.css
```

---

## 6. 页面设计要求

## 6.1 服务健康页 `/health`

这是 SRE 主入口。

展示：

```text
1. 服务总数
2. 异常服务数
3. 健康服务数
4. 告警数
5. 影响用户数
6. 关键服务健康状态表
7. 服务依赖拓扑
8. 最近告警
```

服务健康表字段：

```text
服务名称
状态
错误率 5m
P95 延迟 5m
流量 rps
饱和度
最近重启
操作 / 钻取
```

示例服务：

```text
order-service
payment-service
inventory-service
```

页面第一屏必须回答：

```text
哪些关键服务异常？
异常指标是什么？
哪个服务可能影响用户？
```

---

## 6.2 异常详情 / RCA 分析页 `/rca`

展示：

```text
1. 当前异常摘要
2. 关键指标
3. 受影响链路
4. RCA 分析结果
5. 候选假设对比
6. 关键证据摘要
7. AI 假设建议
8. 动态报告入口
```

RCA 页面不要放故障注入主控制。  
故障注入属于 Chaos 实验页。

必须显示：

```text
当前判断：竞争假设 / 已定位根因 / 证据不足
候选根因
置信度
score gap
证据时间窗口
```

不要使用 `near-tie` 这个词。中文使用：

```text
竞争假设
候选根因未唯一收敛
分数接近
```

如果 deployment_regression 和 downstream_dependency_latency 同分，显示：

```text
当前为竞争假设：部署回归 与 下游依赖延迟 得分接近。
系统不会仅因排序顺序将某一个假设判定为唯一根因。
```

---

## 6.3 证据明细页 `/evidence`

Evidence 是 drill-down 页面，不是第一屏主视图。

展示：

```text
1. Source Matrix
2. Top Evidence
3. Raw Evidence Table
4. Source Filter
5. Evidence Type Filter
6. Service Filter
7. 时间窗口
```

证据来源：

```text
Prometheus 指标
Loki 日志
Jaeger 调用链
Kubernetes 运行时
Alertmanager 告警
```

默认只展示 source summary + top evidence，不要默认展开全部 raw evidence。

`*_no_signal` 的处理：

```text
1. 显示为“未收集到有效异常信号”
2. 不计入有效异常证据
3. 不渲染为成功异常证据
4. 默认折叠
```

---

## 6.4 Chaos 实验页 `/chaos`

Chaos / 故障注入 / 流量模拟必须独立页面。

展示：

```text
1. 目标服务
2. 故障类型
3. 延迟强度
4. 错误率
5. 持续时间
6. 流量目标
7. RPS
8. 观测窗口
9. 启动实验
10. 停止实验
11. 恢复正常
```

预设场景只是配置模板，不是写死逻辑。

预设示例：

```text
支付服务延迟
支付服务错误率升高
库存服务超时
订单服务 CrashLoop
```

配置应允许切换目标服务，例如：

```text
order-service
payment-service
inventory-service
```

不要把故障注入写死到 payment-service。

---

## 6.5 实验环境页 `/lab`

展示：

```text
Prometheus
Loki
Jaeger
Kubernetes
Demo Services
```

每个组件显示：

```text
状态
Endpoint
最近检查时间
错误信息
```

---

## 6.6 设置页 `/settings`

第一阶段可以是占位页。

展示：

```text
时间窗口默认值
刷新间隔
环境名称
API Base URL
```

---

## 7. API Client

创建：

```text
src/api/client.ts
```

封装现有 API：

```text
GET  /api/observability/status
GET  /api/demo-services/status
POST /api/live-scenario/run
GET  /api/live-scenario/latest
POST /api/live-scenario/reset
```

如果当前 API 不存在或字段不完整，第一阶段允许使用 mock data，但必须明确标记：

```text
mock data
```

不要伪装成真实 API 数据。

---

## 8. 时间窗口要求

UI 中必须显式展示：

```text
waitSeconds
lookbackSeconds
stepSeconds
evidenceWindowStart
evidenceWindowEnd
```

推荐默认值：

```text
waitSeconds = 30
lookbackSeconds = 300
stepSeconds = 15
```

含义：

```text
等待 30 秒让 scrape / ingestion 完成
查询最近 5 分钟数据
15 秒粒度
```

不要继续默认使用过短窗口，例如 waitSeconds=8。

---

## 9. 视觉与交互要求

参考设计图方向：

```text
1. 左侧深色导航
2. 主区域浅色 dashboard
3. 卡片式布局
4. 关键状态用红 / 绿 / 橙区分
5. 表格信息密度适中
6. 第一屏优先展示服务健康和异常指标
7. drill-down 内容折叠展示
```

避免：

```text
1. 一屏堆满 raw evidence
2. 所有内容塞到一个页面
3. 中英文混杂
4. 把 Chaos 控制放到 RCA 主视图
5. 用 demo scenario 文案冒充产品能力
```

---

## 10. 构建集成

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

新 UI 构建产物成为新的：

```text
index.html
```

---

## 11. 本阶段不做

不要做：

```text
1. 不重构后端 RCA 逻辑
2. 不进入 Step W
3. 不做 post-probe RCA re-run
4. 不删除旧 index.html，先改名保留
5. 不强行完整接通所有 API
6. 不做复杂权限 / 多租户
7. 不引入过重 UI 框架
8. 不把 Scenario G 写死成唯一能力
```

---

## 12. 验收标准

完成后执行：

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

浏览器打开新 UI 后必须满足：

```text
1. 有左侧导航
2. 有服务健康页
3. 有 RCA 分析页
4. 有证据明细页
5. 有 Chaos 实验页
6. 有实验环境页
7. 有设置页
8. 页面文案为中文
9. 时间窗口显式展示
10. Chaos 故障目标服务可配置，不写死 payment-service
11. Evidence 默认摘要展示，raw evidence 可展开
12. 旧 index.html 已保留为 index-legacy.html
```

---

## 13. 完成报告格式

完成后输出：

```text
1. 技术栈选择和理由
2. 新增文件
3. 修改文件
4. 页面结构
5. 路由说明
6. API client 说明
7. mock data / real API 接入情况
8. Makefile target
9. 构建结果
10. 与参考设计图的对应关系
11. 当前限制
12. 下一步建议
```
