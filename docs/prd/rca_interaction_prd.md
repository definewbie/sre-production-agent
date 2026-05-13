# RCA Product Interaction PRD

**Status:** product interaction design, no implementation changes  
**Last updated:** 2026-05-12  
**Scope:** RCA product shape and interaction model for SRE and service developers.

Companion prototype:

- [RCA Product Prototype HTML](./rca_interaction_prototype.html)
- [RCA Incident Cockpit Prototype HTML](./rca_interaction_prototype_cockpit.html)
- [RCA Topology Impact Prototype HTML](./rca_topology_impact_prototype.html)

## Executive Summary

上一版原型的问题是：它把 RCA 引擎的内部结构直接摆到了 UI 上。`claim`、`guard`、`score breakdown`、`provider trust` 这些概念对算法设计有用，但不是 on-call SRE 和服务研发打开页面时最先需要的信息。

更合理的产品形态应该像 Dynatrace / BigPanda / Datadog 这类工具一样，以 **incident / problem** 为中心，而不是以一次 RCA run 为中心：

```text
事故发生了什么
影响多大
系统认为最可能从哪里开始
为什么这么判断
现在应该谁处理
先止血还是先补证据
哪些现象只是被影响，不是根因
```

因此，目标交互不是“RCA 算法调试面板”，而是一个 **事故调查和处置工作台**：

```text
Incident Feed
  -> Incident Overview
  -> Impact and Ownership
  -> Most likely starting point
  -> Why this / why not others
  -> Timeline of symptoms, changes, and actions
  -> Suggested mitigation / next check
  -> Evidence details for drilldown
```

## Industry Product Shape Review

### Dynatrace-Like Shape

Dynatrace 的公开文档强调 `Problem`，而不是单条告警或单次 RCA。一个 problem 可以聚合同一 topology 中共享根因和影响的多个事件，并在详情页突出 root cause entity、affected entities、impact、relevant logs、visual resolution path。

Design implication:

```text
产品首页应该是 Problem / Incident Feed。
详情页第一屏应该是 root cause entity + impact + resolution path。
证据和日志是下钻，不是第一视觉层级。
```

What this means for us:

1. 列表列名不要叫 RCA Runs 为主，应以 Incident / Problem 为主。
2. 详情页第一屏不应该首先展示算法表格。
3. Topology 应该以 resolution path / impact path 出现，而不是一个孤立拓扑图。
4. Logs / metrics / traces 应该是“事故相关证据”，由详情页下钻。

### BigPanda-Like Shape

BigPanda 的核心产品形态是 incident intelligence：把高质量 alert 关联成 incident，自动生成动态 incident title，突出 probable root cause、timeline、changes、topology、runbook 和 incident enrichment。

Design implication:

```text
产品应该让 operator 一眼看到 probable root cause 和事件如何演化。
changes / deploy / manual operation 应有独立区域。
timeline 是核心视图，不是附属 tab。
```

What this means for us:

1. Incident title 应该可读，例如“Checkout latency caused by payment-service slowdown”，而不是“混沌实验: payment-service unknown”。
2. 时间线需要串起告警、症状、变更、拓扑传播和人工动作。
3. 变更上下文应独立成 Changes lane，不能靠 k8s event 猜 deployment regression。
4. 归一化后的 incident 应该持续更新，而不是同一链路触发多次 RCA。

### Datadog-Like Shape

Datadog Watchdog RCA 明确区分：

```text
Root cause      — 引发问题的状态变化
Critical failure — 第一个直接导致应用性能退化的位置
Impact          — 被间接影响的服务、路径、用户
```

它还强调不要把 latency/error 本身当作 root cause；这些通常是 critical failure 或 impact。

Design implication:

```text
页面必须区分“原因”、“故障表现”和“影响范围”。
```

What this means for us:

1. 如果 payment-service latency 导致 order-service checkout timeout：
   - root cause candidate: payment-service slowdown or state change before it
   - critical failure: order-service checkout timeout
   - impact: checkout 5xx / affected endpoint / users
2. 如果只看到 pod restart，但没有 exit code / OOMKilled / crash log，不应展示“pod crash loop 高置信根因”。
3. 分数可以展示，但不能成为用户理解 RCA 的主语言。

### Resolve AI / AI-SRE-Like Shape

AI-SRE 产品形态通常像协作式调查助手：它会提出调查计划、并行检查假设、总结证据、建议下一步，但最终输出需要和可验证证据绑定。

Design implication:

```text
AI 应该像调查副驾，不像最终裁判。
```

What this means for us:

1. AI panel 可以解释、建议 probe、总结 incident update。
2. AI 不能覆盖确定性 RCA decision。
3. AI 产出的内容要标记 verified / unverified。
4. 对 SRE 最有价值的是“下一步查什么”和“如何写 incident update”。

## Current UI Review

Current implementation references:

1. `sre-agent-ui/src/sections/RcaRunsList.tsx`
2. `sre-agent-ui/src/sections/RcaAnalysisPanel.tsx`
3. Existing specs under `docs/prd/rca_evidence_state_model_split_specs`
4. Existing prototype specs under `docs/prd/sre_agent_ui_prototype_split_specs`

### What Works

1. 已有 RCA 列表和详情页，不是只能看 latest result。
2. 有 alert / RCA run tab，说明已经意识到告警和分析结果是不同概念。
3. 详情页有 overview、hypotheses、evidence、timeline、events、AI suggestion、metadata，具备演进基础。
4. Lab Demo 是手动触发的，边界清楚。

### What Does Not Work Yet

| Current UI behavior | Why it feels wrong to SRE / developers |
|---|---|
| 决策结果显示“竞争假设 / 置信度 85%” | 用户不知道能不能行动，也不知道为什么不是明确根因。 |
| Alert name 类似“混沌实验: payment-service unknown” | 事故标题没有表达用户影响、根因候选或关键失败点。 |
| RCA runs 列表按单次分析组织 | 真实事故处理中用户关心 incident，而不是系统跑了几次 RCA。 |
| Hypothesis 表达偏分数 | 研发更关心“为什么找我”和“证据够不够”。 |
| Topology 不在事故叙事中 | 用户需要看到“从哪里开始，影响到哪里”，不是单独看图。 |
| Timeline 弱 | SRE 需要从时间线上判断变更、症状、传播、缓解是否一致。 |
| AI suggestion 是 tab | 事故中 AI 更像右侧协作助手或 incident update 生成器。 |
| 缺 mitigation / owner / escalation | RCA 不能只诊断，还要帮助推进处置。 |

## Product Users

### On-Call SRE

Primary questions:

```text
现在影响多大？
是否需要升级事故等级？
哪个 team 先看？
能不能先止血？
系统判断可信吗？
还缺什么证据？
```

Best UI:

```text
impact summary
diagnostic state
owner / escalation
recommended mitigation
timeline
```

### Service Developer

Primary questions:

```text
为什么找我的服务？
是我服务自身问题，还是被下游影响？
证据在哪里？
有没有最近 deploy / config / feature flag？
我应该看哪条 trace / log / metric？
```

Best UI:

```text
starting point
dependency path
why this service
evidence snippets
related changes
next debugging links
```

### Incident Commander

Primary questions:

```text
现在状态是什么？
谁在处理？
下一步动作是什么？
什么时候更新？
是否需要通知业务方？
```

Best UI:

```text
status strip
ownership
impact
action log
AI-generated update draft
```

## Target Information Architecture

```text
RCA / Incidents
  ├── Active Incidents
  ├── Recently Resolved
  ├── Needs Evidence
  └── Observability Degraded

Incident Detail
  ├── Header: title, status, severity, owner, age
  ├── Impact: user journey, endpoint, SLO, affected services
  ├── RCA Summary: starting point, confidence language, diagnostic quality
  ├── Recommended Action: mitigate / investigate / repair observability
  ├── Visual Path: root candidate -> failure -> impact
  ├── Timeline: symptoms, changes, topology propagation, actions
  ├── Evidence Drawer: metrics, logs, traces, k8s, changes
  ├── Competing Explanations
  └── AI Assistant: incident update, next probes, summary
```

## Target Screen Design

### 1. Incident Feed

The feed should answer:

```text
Which incident should I open first?
What is affected?
Who owns the likely starting point?
Is RCA actionable or still uncertain?
```

Recommended columns/cards:

```text
Incident
Impact
Likely starting point
Owner
Diagnostic state
Age
Next action
```

Example:

```text
Checkout latency and 5xx
Impact: checkout API, 12% error rate, SLO burning
Likely starting point: payment-service latency
Owner: payments
Diagnostic state: Probable, trace + metric evidence
Next action: mitigate payment latency, verify order-service recovery
```

### 2. Incident Header

The first line should be human-readable:

```text
Checkout requests are timing out after payment-service latency spike
```

Not:

```text
混沌实验: payment-service unknown
```

Header fields:

```text
status: Active / Mitigating / Monitoring / Resolved
severity: SEV2
age: 18m
owner: payments
affected journey: checkout
RCA state: Probable root cause
diagnostic quality: Normal / Degraded
```

### 3. First Screen Summary

The first screen should have four operator cards:

```text
Impact
  What users or services are affected?

Likely starting point
  Where should investigation begin?

Why we think so
  2-4 plain-language evidence points

Recommended next action
  Mitigate, probe, or repair observability
```

### 4. Cause / Failure / Impact

Use product language, not algorithm language:

```text
Likely starting point
  payment-service latency spike

First visible failure
  order-service checkout timeout

Customer / service impact
  checkout 5xx and SLO burn
```

This avoids the common RCA mistake of calling every symptom a root cause.

### 5. Visual Path

The topology panel should not be a generic service map. It should answer:

```text
What path connects the likely starting point to the user-visible impact?
```

Example:

```text
payment-service
  latency spike at 23:18
    -> order-service
       checkout timeout at 23:19
         -> checkout API
            5xx and SLO burn at 23:20
```

### 6. Timeline

Timeline should be the primary investigation object.

Recommended lanes:

```text
Impact      user-facing symptoms / SLO burn
Services    service latency, error rate, traces
Changes     deploy, config, feature flag, manual operation
Infra       k8s / node / EC2 / process events
Actions     mitigation, rollback, scale, notes
AI          proposed checks and generated updates
```

This aligns better with how SREs reason during incidents: sequence first, details second.

### 7. Evidence Drilldown

Evidence should be organized for service owners:

```text
Why payment-service?
  - p95 latency increased from 120ms to 2.4s before checkout 5xx
  - traces show order-service waiting on payment-service
  - no matching deploy on order-service

Why not order-service crash?
  - pod restart observed after timeout started
  - no OOMKilled / exit code / startup failure log
  - restart is more likely impact or mitigation side effect
```

Avoid showing raw scores unless the user expands "RCA internals".

### 8. Recommended Action

Decision-aware actions:

| RCA state | UI action |
|---|---|
| Likely / probable root cause | Recommend mitigation with validation check. |
| Competing explanations | Recommend differentiating probes before risky mitigation. |
| Insufficient evidence | Recommend concrete evidence collection. |
| Observability degraded | Recommend fixing telemetry first or using fallback evidence. |
| Explained by action | Recommend owner confirmation and audit trail. |

### 9. AI Assistant

AI should be a right-side assistant:

```text
What AI can do:
  draft incident update
  explain why a service was selected
  suggest the next query or probe
  summarize recent timeline changes
  prepare handoff notes

What AI must not do:
  silently change final RCA decision
  hide missing evidence
  present unverified proposal as root cause
```

### 10. Topology Impact Map

专业 RCA 产品不能只有 service-to-service 线图。为了做到一眼定位问题和表达故障传播路径，Topology view 应该支持 full-stack entity hierarchy：

```text
business journey / endpoint
  -> service
  -> process / container / workload
  -> pod / vm / bare metal host / node
  -> cluster / az / region / data center
  -> shared dependencies
     db / cache / queue / alb / nlb / dns / network / external provider
```

The topology map should support several lenses:

| Lens | Purpose |
|---|---|
| Business impact | 从用户旅程和入口流量看影响。 |
| Service dependency | 看 service-to-service 调用和传播路径。 |
| Runtime | 下钻到 process / container / workload。 |
| Infrastructure | 下钻到 pod / VM / node / bare metal / region。 |
| Dependency | 显示 DB、queue、cache、LB、network、external provider。 |
| RCA overlay | 用颜色和路径标记 starting point、failure、impact、counter-signal、unknown。 |

Design rules:

1. 默认只高亮 critical path 和受影响邻居，避免全量拓扑淹没用户。
2. 健康节点不是噪声，应该作为 counter-signal 可见但弱化。
3. 每条边要表达来源：trace observed、configured、service catalog、network flow、cloud inventory。
4. 每个节点要能 drill down 到 owner、evidence、metrics、logs、traces、runbook。
5. Topology map 不替代 incident narrative；它是定位和传播解释视图。
6. Dependency direction 和 impact propagation direction 必须分开表达：例如运行时调用是 `ALB -> order-service -> payment-service`，但故障影响传播是 `payment-service -> order-service -> checkout impact`。
7. Service / process / pod / region 之间是 entity stack / containment，不应画成因果传播箭头。

Prototype:

- [RCA Topology Impact Prototype HTML](./rca_topology_impact_prototype.html)

## Product Copy Guidelines

Prefer:

```text
Likely starting point
First visible failure
Affected services
Why this service
What changed
What to check next
Diagnostic quality degraded
```

Avoid in primary UI:

```text
claim
guard
decision cap
topology_causality_score
provider trust vector
score breakdown
```

These can exist under "RCA internals" for debugging.

## Revised Prototype Direction

The companion HTML prototype was rewritten to demonstrate this product shape:

1. Incident feed with impact and next action.
2. Detail page first screen with impact, likely starting point, evidence summary, action.
3. Cause / failure / impact separation.
4. Visual resolution path.
5. Timeline as the main investigation surface.
6. Service-owner-friendly evidence explanations.
7. Right-side incident assistant.
8. RCA internals collapsed into a lower-priority section.

An alternative cockpit prototype was added for comparison:

- [RCA Incident Cockpit Prototype HTML](./rca_interaction_prototype_cockpit.html)

The cockpit version is intentionally calmer and closer to professional incident tools:

1. Narrow product rail instead of a large navigation sidebar.
2. Left-side active problem queue.
3. Center incident narrative with impact, owner, confidence language, and timeline.
4. Right-side action, assistant, and escalation panel.
5. Fewer cards on the first screen.
6. Algorithm internals hidden below the incident narrative.

A topology impact prototype was added for the visual RCA view:

- [RCA Topology Impact Prototype HTML](./rca_topology_impact_prototype.html)

This version focuses on full-stack visualization:

1. Region / zone / cluster layer.
2. Compute layer: pod / VM / bare metal / node.
3. Runtime layer: process / container / workload.
4. Service and entry-point layer.
5. Shared dependency layer: DB, queue, cache, LB, network, external provider.
6. RCA overlay showing likely causal path, impacted nodes, healthy counter-signals, and external partial visibility.

## Product Acceptance Criteria

1. An SRE can decide within five seconds whether to mitigate, investigate, or repair observability.
2. A service developer can understand why their service is implicated without reading score internals.
3. The page separates root cause candidate, first visible failure, and impact.
4. Related alerts in the same topology chain appear as one incident, not many RCA runs.
5. The page shows timeline and changes before raw evidence tables.
6. The UI explains uncertainty as next actions, not as a dead-end score.
7. AI output is useful for investigation and communication, but visibly separate from validated RCA.
8. Technical scoring details remain available for debugging but are not the primary UX.
9. The topology view can show full-stack dependency and propagation path without overwhelming the incident narrative.

## Source Notes

This PRD was informed by public product documentation:

1. Dynatrace RCA concepts: <https://docs.dynatrace.com/docs/dynatrace-intelligence/root-cause-analysis/concepts>
2. Dynatrace Problems app: <https://docs.dynatrace.com/docs/dynatrace-intelligence/problems-app>
3. Dynatrace Smartscape Classic: <https://docs.dynatrace.com/docs/analyze-explore-automate/smartscape-classic>
4. Datadog Watchdog RCA: <https://docs.datadoghq.com/watchdog/rca/>
5. Datadog Incident AI: <https://docs.datadoghq.com/incident_response/incident_management/investigate/incident_ai/>
6. BigPanda AIOps / Incident Intelligence: <https://docs.bigpanda.io/docs/bigpanda-aiops>
7. BigPanda incident details: <https://docs.bigpanda.io/en/incidents-in-bigpanda.html>
8. Resolve AI docs: <https://docs.resolve.ai/>
9. Resolve AI SRE product page: <https://resolve.ai/product/ai-sre>
