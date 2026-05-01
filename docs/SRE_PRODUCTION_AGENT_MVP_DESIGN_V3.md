# SRE Production Agent MVP Design V3

> Verification-first RCA Agent for Kubernetes Microservices  
> 面向 Kubernetes 微服务系统的可审计自动化判断系统

---

## 1. V3 核心变化

V3 在 V2 的基础上继续收窄和加深。

V2 已经完成了关键转向：

```text
从“完整 RCA Agent”
转向
“Hypothesis → Verification → Confidence → Decision 做深”
```

V3 继续补齐四个缺口：

1. **新增 competing hypotheses 场景**
   - 展示两个根因假设都成立一部分时，Agent 如何区分。

2. **补充 confidence calibration narrative**
   - 坦诚说明 MVP 权重来自 SRE 经验和 demo 调参，不伪装成数据学习。
   - 说明未来如何通过 Event Trace + human feedback 校准权重。

3. **补充 Event Trace Consumption Model**
   - 说明 Event Trace 被谁消费、如何消费、产生什么价值。

4. **补充 Interview Q&A**
   - 把 Phase 0 的“面试叙事冻结”落到具体问题和答案上。

V3 的目标不是再扩模块，而是让设计文档从“项目计划”升级为：

```text
面试策略文档 + 工程设计文档
```

---

## 2. Interview Goal

这个项目首先是一个**求职用 MVP**，不是完整商业产品，也不是完整复刻 Resolve.ai。

底层问题不是：

```text
如何做一个功能完整的 SRE Agent？
```

而是：

```text
一个有多年 SRE / 平台工程 / 架构经验的人，如何在 15 分钟面试里让面试官相信：
他不仅懂生产系统，也能设计和落地 AI Agent？
```

所以，本项目的信息效率优先级高于架构完整度。

面试中最希望对方记住的不是：

```text
我接了 Prometheus / Loki / Kubernetes / GitHub。
```

而是：

```text
我把生产事故调查拆成了 Evidence → Hypothesis → Verification → Confidence → Decision 的可审计链路。
LLM 不直接猜根因，而是被约束在 evidence 和 verification 之后做总结。
```

---

## 3. Higher-level Design Abstraction

V3 对项目的更高层抽象是：

> 这个项目的本质不是 RCA 工具，而是一个**可审计的自动化判断系统**。RCA 只是应用场景。

真正有区分度的设计思想是：

```text
把人的判断过程拆解为可执行的步骤；
每一步都有输入输出；
每一步都可以被检验；
不确定时不强行收敛；
最终输出可审计、可复盘、可学习的判断轨迹。
```

这个思想不仅适用于 RCA，也适用于：

- 安全分析；
- 合规审查；
- 财务异常检测；
- 变更风险评估；
- 生产发布准入；
- 运维事件分级；
- AI Agent 执行结果审查。

面试时可以这样讲：

> 我不是只在做一个 SRE side project，而是在用 RCA 这个场景验证一类更通用的架构思想：AI 参与判断时，不能只追求回答，而要把判断过程结构化、证据化、可审计化。  
> 这也是为什么我把重点放在 verification、confidence 和 event trace，而不是放在接入更多数据源上。

---

## 4. Product Positioning

### 4.1 一句话定位

英文：

> A verification-first AI SRE RCA Agent that takes an incident alert as input, collects evidence, generates competing root cause hypotheses, verifies them against supporting evidence, counter evidence, missing evidence, and contradictions, then produces an auditable RCA or escalation report.

中文：

> 一个以 Hypothesis Verification 为核心的 AI SRE RCA Agent：它不是让 LLM 直接猜根因，而是把告警调查过程拆成 evidence collection、hypothesis generation、verification、confidence scoring 和 escalation decision，并把全过程记录为可审计、可学习的 Event Trace。

---

## 5. Design Thesis

大多数 AI SRE Demo 的问题是：

```text
Alert / Logs → LLM → RCA Summary
```

这类方案的问题：

1. LLM 容易幻觉；
2. 根因判断无法审计；
3. 缺乏 counter evidence；
4. 不知道何时停止；
5. 不知道何时升级给人；
6. 不能处理多个 hypothesis 竞争；
7. 面试官容易认为这只是“日志总结工具”。

本项目的设计主张是：

```text
Alert
  → Evidence
  → Competing Hypotheses
  → Verification
  → Confidence
  → Decision
  → RCA / Escalation Report
  → Event Trace
```

核心差异化：

1. **Evidence-first, not LLM-first**
2. **Diagnostic patterns first, not free-form guessing**
3. **Competing hypotheses are first-class objects**
4. **Verification before conclusion**
5. **Confidence must be explainable and calibratable**
6. **The agent must know when not to conclude**
7. **Event Trace is the investigation data backbone**
8. **LLM is a synthesizer, not the primary root cause oracle**

---

## 6. What Makes This Different From a Log Chatbot

| 普通日志问答 / RAG Demo | 本项目 |
|---|---|
| 用户提问驱动 | 告警驱动 |
| LLM 直接总结日志 | 先收集 evidence，再验证 hypothesis |
| 通常只有一个猜测 | 支持多个 competing hypotheses |
| 很少有反证 | 明确建模 counter evidence |
| 输出一个看似确定的结论 | 输出 confidence 和 decision |
| 无法解释为什么相信这个根因 | 每个结论绑定 evidence |
| 没有调查轨迹 | 全流程进入 Event Trace |
| 每次调查不可复用 | Event Trace 可用于 skill learning |
| 不知道何时停止 | 低置信度时升级人工 |

本项目不是“chat with logs”，而是一个结构化事故调查 workflow。

---

## 7. Core Design Principles

```text
Alert-driven, not chat-driven.
Evidence-first, not LLM-first.
Diagnostic-pattern-first, not free-form reasoning.
Competing hypotheses must be explicitly compared.
Verification before conclusion.
Confidence must be explainable and calibratable.
Escalation is a valid outcome.
Event Trace is the data backbone.
LLM summarizes; it does not invent evidence.
Local-first and interview-ready.
```

---

## 8. MVP Scope

### 8.1 V3 第一版必须做

V3 第一版聚焦一个核心能力：

```text
Hypothesis → Verification → Confidence Scoring → Decision
```

必须包含：

1. Incident Task
2. Evidence
3. Event Trace Store
4. Diagnostic Pattern
5. Hypothesis
6. Verification Result
7. Confidence Scoring
8. Hypothesis Comparison
9. RCA / Escalation Report
10. CLI command: `investigate`
11. Five demo scenarios
12. Interview Q&A

---

### 8.2 V3 第一版降级处理的内容

| 模块 | V3 处理方式 |
|---|---|
| Prometheus Collector | 降级为 Metric Evidence Provider |
| Loki Collector | 降级为 Log Evidence Provider |
| Kubernetes Collector | 降级为 K8s Evidence Provider |
| Git Collector | 降级为 Change Evidence Provider |
| Signal Normalizer | 简化为 Evidence Classification |
| Rule Engine | 改名为 Diagnostic Pattern System |
| LLM Planner | 后置，不进入第一版核心 |
| Web UI | 不做 |
| Auto Remediation | 不做 |

---

### 8.3 V3 第一版不做

| 不做 | 原因 |
|---|---|
| 自动修复线上环境 | 风险高，求职 MVP 不需要 |
| 自动 rollback | 可以给建议，不实际执行 |
| 自动生成 PR | 后置 |
| 复杂 Web UI | Markdown 报告足够 |
| 多租户 SaaS | 无意义 |
| 企业权限系统 | 只做只读和审计叙事 |
| 全量 observability vendor | 不做接入炫技 |
| 大模型自主任意探索 | 不稳定，容易幻觉 |
| Istio / Service Mesh | 第一版过重 |
| OpenTelemetry 全链路 | 后置 |
| Kubernetes Controller | CLI 优先 |
| 复杂 ML confidence model | 用可解释 scoring policy |
| 自动学习权重 | 作为后续 calibration strategy，不在 MVP 内实现 |

---

## 9. Core Workflow

```text
┌─────────────────────┐
│ Incident Alert       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Incident Task        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Evidence Providers   │
│ - Metric Evidence    │
│ - Log Evidence       │
│ - Change Evidence    │
│ - K8s Evidence       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Event Trace Store    │
│ Investigation Ledger │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Hypothesis Engine    │
│ Diagnostic Patterns  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Verification Engine  │
│ - support evidence   │
│ - counter evidence   │
│ - missing evidence   │
│ - contradictions     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Confidence Scoring   │
│ - score              │
│ - explanation        │
│ - decision threshold │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Hypothesis Comparator│
│ - score difference   │
│ - decisive evidence  │
│ - tie / near-tie     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ RCA Decision         │
│ - likely root cause  │
│ - probable cause     │
│ - uncertain          │
│ - competing causes   │
│ - escalate           │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Report Generator     │
│ RCA / Escalation     │
└─────────────────────┘
```

---

## 10. Event Trace as Investigation Data Backbone

### 10.1 为什么 Event Trace 是核心

Event Trace 不是普通日志，也不是附属审计模块。

它是整个 RCA Agent 的调查账本：

```text
alert received
evidence collected
hypotheses generated
hypotheses verified
confidence calculated
hypotheses compared
decision made
report generated
human feedback
```

它的价值：

1. **可审计**
   - Agent 为什么得出这个结论？
   - 依据了哪些证据？
   - 排除了哪些可能？

2. **可复盘**
   - 哪些调查路径有效？
   - 哪些 evidence 对判断有帮助？
   - 哪些 hypothesis 被错误提升？

3. **可学习**
   - 人类最终确认了什么根因？
   - 哪些 pattern 可以沉淀为 skill？
   - 后续是否能从历史 incident 中学习权重？

4. **可面试展示**
   - 这能区分本项目与普通 LLM demo。
   - 面试时可以直接展示一次完整 incident investigation trace。

---

### 10.2 Event Trace 事件类型

```text
INCIDENT_CREATED
EVIDENCE_COLLECTED
HYPOTHESIS_GENERATED
HYPOTHESIS_VERIFIED
CONFIDENCE_SCORED
HYPOTHESIS_COMPARED
DECISION_MADE
REPORT_GENERATED
HUMAN_FEEDBACK_RECEIVED
```

---

### 10.3 Event Trace 示例

```json
{
  "event_id": "evt_001",
  "incident_id": "inc_20260428_1003",
  "event_type": "EVIDENCE_COLLECTED",
  "timestamp": "2026-04-28T10:06:00Z",
  "payload": {
    "evidence_id": "ev_003",
    "source": "log",
    "type": "timeout_error",
    "service": "order-service",
    "content": "payment timeout after 500ms",
    "strength": 0.8
  }
}
```

---

## 11. Event Trace Consumption Model

Event Trace 的价值取决于谁消费它、如何消费它。

V3 中定义三个消费侧场景。

---

### 11.1 面试展示消费

目标：

> 让面试官看到 Agent 不是直接输出答案，而是有完整调查轨迹。

方式：

```bash
python -m sre_agent.cli.investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --show-trace
```

输出形式：

```text
[10:03:00] INCIDENT_CREATED: HighErrorRate on order-service
[10:03:05] EVIDENCE_COLLECTED: deploy_event_near_alert_window
[10:03:06] EVIDENCE_COLLECTED: dependency_timeout_logs
[10:03:07] HYPOTHESIS_GENERATED: deployment_regression
[10:03:07] HYPOTHESIS_GENERATED: downstream_dependency_latency
[10:03:08] HYPOTHESIS_VERIFIED: deployment_regression support=3 counter=1 missing=1
[10:03:08] HYPOTHESIS_VERIFIED: downstream_dependency_latency support=3 counter=2 missing=0
[10:03:09] CONFIDENCE_SCORED: deployment_regression score=0.64
[10:03:09] CONFIDENCE_SCORED: downstream_dependency_latency score=0.58
[10:03:10] HYPOTHESIS_COMPARED: deployment_regression wins by decisive change evidence
[10:03:11] DECISION_MADE: probable_root_cause with competing hypothesis noted
```

---

### 11.2 复盘消费

目标：

> 让 on-call engineer / SRE lead 能在 RCA review 时看到关键判断节点。

方式：

RCA report 自动引用 trace 关键节点：

```markdown
## Investigation Trace Summary

1. 4 evidence items collected from metric/log/deploy/topology sources.
2. 3 hypotheses generated.
3. 2 hypotheses survived initial verification.
4. deployment_regression scored 0.64.
5. downstream_dependency_latency scored 0.58.
6. deployment_regression selected due to stronger change correlation.
7. downstream_dependency_latency retained as competing hypothesis.
```

价值：

1. 让 RCA 审阅者知道结论怎么来的；
2. 让错误判断可以被追溯；
3. 让后续规则调整有依据。

---

### 11.3 学习消费

目标：

> 让 Event Trace 成为后续权重校准和 skill governance 的输入。

方式：

人类复盘后写入 feedback event：

```json
{
  "event_type": "HUMAN_FEEDBACK_RECEIVED",
  "incident_id": "inc_20260428_1003",
  "payload": {
    "final_root_cause": "downstream_dependency_latency",
    "agent_selected": "deployment_regression",
    "agent_was_correct": false,
    "missed_signal": "payment-service p99 latency spike in a narrow endpoint",
    "feedback": "deployment was correlated but not causal"
  }
}
```

后续用途：

1. 调整 pattern 权重；
2. 增加 missing evidence requirement；
3. 沉淀新的 diagnostic pattern；
4. 生成 regression test；
5. 更新面试中关于 calibration 的叙事。

---

## 12. Diagnostic Pattern System

### 12.1 为什么不用“Rule Engine”作为主叙事

如果 MVP 只有几个场景，强行称为 Rule Engine 容易显得像硬编码 if-else。

因此 V3 继续使用更诚实、更可扩展的概念：

```text
Diagnostic Pattern
```

Diagnostic Pattern 不是完整智能规则系统，而是：

> 对高频生产故障模式的结构化描述，包括 evidence requirements、verification strategy 和 confidence policy。

---

### 12.2 Diagnostic Pattern 三层结构

```text
Pattern
  → Evidence Requirements
  → Verification Strategy
  → Confidence Policy
```

---

### 12.3 Pattern 示例：Deployment Regression

```yaml
id: deployment_regression
description: Recent deployment correlated with error spike.

evidence_requirements:
  - deploy_event_near_alert_window
  - error_rate_spike_after_deploy
  - new_log_failure_signature
  - optional_code_or_config_diff

verification_strategy:
  supporting:
    - temporal_correlation
    - failure_signature_match
    - change_correlation
  counter:
    - downstream_service_error_absent
    - resource_pressure_absent
    - no_deploy_near_alert_window

confidence_policy:
  base: 0.30
  weights:
    deploy_event_near_alert_window: 0.20
    error_rate_spike_after_deploy: 0.20
    new_log_failure_signature: 0.15
    code_or_config_diff_match: 0.20
    strong_counter_evidence: -0.25
    missing_critical_evidence: -0.15
```

---

### 12.4 Pattern 示例：OOMKilled

```yaml
id: pod_oom_killed
description: Service errors caused by pod memory pressure or OOMKilled events.

evidence_requirements:
  - pod_restart_count_increased
  - kubernetes_event_oomkilled
  - memory_usage_near_limit
  - error_spike_near_restart_window

verification_strategy:
  supporting:
    - infra_event_match
    - resource_pressure_match
    - temporal_correlation
  counter:
    - no_restart_observed
    - memory_usage_normal
    - error_spike_before_restart

confidence_policy:
  base: 0.35
  weights:
    kubernetes_event_oomkilled: 0.30
    pod_restart_count_increased: 0.15
    memory_usage_near_limit: 0.15
    error_spike_near_restart_window: 0.15
    no_restart_observed: -0.30
```

---

### 12.5 Pattern 示例：Downstream Dependency Latency

```yaml
id: downstream_dependency_latency
description: Upstream service latency caused by downstream dependency degradation.

evidence_requirements:
  - upstream_latency_spike
  - upstream_resource_normal
  - dependency_timeout_logs
  - downstream_latency_spike
  - optional_service_topology_match

verification_strategy:
  supporting:
    - dependency_failure_signature_match
    - downstream_metric_correlation
    - upstream_resource_counter_check
  counter:
    - downstream_metrics_normal
    - upstream_resource_pressure_present
    - no_dependency_error_logs

confidence_policy:
  base: 0.25
  weights:
    upstream_latency_spike: 0.15
    dependency_timeout_logs: 0.20
    downstream_latency_spike: 0.25
    service_topology_match: 0.10
    upstream_resource_normal: 0.10
    downstream_metrics_normal: -0.25
    missing_trace_data: -0.10
```

---

## 13. Domain Model Design

### 13.1 IncidentTask

```python
class IncidentTask:
    id: str
    alert_name: str
    service: str
    namespace: str
    severity: str
    started_at: datetime
    labels: dict
    annotations: dict
```

职责：

> 表示一次事故调查任务，是整个 workflow 的输入和目标对象。

---

### 13.2 Evidence

```python
class Evidence:
    id: str
    incident_id: str
    source: str
    evidence_type: str
    service: str
    timestamp: datetime
    content: str
    attributes: dict
    strength: float
```

字段说明：

| 字段 | 说明 |
|---|---|
| source | metric / log / k8s / deploy / git / topology |
| evidence_type | error_spike / timeout_log / oom_event / deploy_event 等 |
| service | 关联服务 |
| content | 人可读的证据描述 |
| attributes | 结构化字段 |
| strength | 证据强度，0 到 1 |

---

### 13.3 DiagnosticPattern

```python
class DiagnosticPattern:
    id: str
    description: str
    evidence_requirements: list[str]
    verification_strategy: dict
    confidence_policy: dict
```

职责：

> 描述一种高频生产故障模式及其验证策略。

---

### 13.4 Hypothesis

```python
class Hypothesis:
    id: str
    incident_id: str
    pattern_id: str
    title: str
    root_cause_type: str
    affected_service: str
    candidate_cause: str
```

职责：

> 表示一个候选根因，不等于最终结论。

---

### 13.5 VerificationResult

```python
class VerificationResult:
    hypothesis_id: str
    supporting_evidence_ids: list[str]
    counter_evidence_ids: list[str]
    missing_evidence: list[str]
    contradictions: list[str]
    explanation: str
```

职责：

> 记录一个 hypothesis 被验证的结果，包括支持证据、反证、缺失证据和矛盾信号。

---

### 13.6 ConfidenceResult

```python
class ConfidenceResult:
    hypothesis_id: str
    score: float
    level: str
    supporting_factors: list[str]
    counter_factors: list[str]
    missing_factors: list[str]
    contradictions: list[str]
    decision: str
```

职责：

> 对 hypothesis 的可信程度进行可解释评分，并给出决策。

---

### 13.7 HypothesisComparison

```python
class HypothesisComparison:
    incident_id: str
    leading_hypothesis_id: str | None
    competing_hypothesis_ids: list[str]
    score_gap: float
    decisive_evidence_ids: list[str]
    comparison_summary: str
    is_near_tie: bool
```

职责：

> 当多个 hypothesis 都有一定支持证据时，记录它们之间的比较结果。

---

### 13.8 InvestigationDecision

```python
class InvestigationDecision:
    incident_id: str
    selected_hypothesis_id: str | None
    decision_type: str
    confidence_score: float
    rationale: str
    next_probes: list[str]
    competing_hypotheses: list[str]
```

decision_type 可选：

```text
likely_root_cause
probable_root_cause
competing_hypotheses
uncertain_requires_more_evidence
insufficient_evidence
escalate_to_human
```

---

### 13.9 RCAReport

```python
class RCAReport:
    incident_id: str
    title: str
    summary: str
    impact: str
    timeline: list
    decision: InvestigationDecision
    hypotheses: list[Hypothesis]
    verification_results: list[VerificationResult]
    confidence_results: list[ConfidenceResult]
    hypothesis_comparison: HypothesisComparison | None
    evidence_summary: list[Evidence]
    recommended_actions: list[str]
    next_probes: list[str]
```

职责：

> 生成面向人的 RCA、Escalation Report 或 Competing Hypotheses Report。

---

## 14. Verification Model Design

### 14.1 Verification 四类检查

每个 hypothesis 都必须经过四类检查：

```text
supporting evidence
counter evidence
missing evidence
contradictions
```

---

### 14.2 Supporting Evidence

支持 hypothesis 的证据。

例子：

```text
deployment occurred 2 minutes before alert
error rate increased immediately after deployment
new timeout log signature appeared after deployment
git diff shows timeout changed from 2000ms to 500ms
```

---

### 14.3 Counter Evidence

削弱 hypothesis 的证据。

例子：

```text
payment-service did not show elevated 5xx
no deploy happened near alert window
memory usage remained normal
```

---

### 14.4 Missing Evidence

应该存在但没查到的关键证据。

例子：

```text
dependency-level latency breakdown is unavailable
trace samples are missing
feature flag change history is unavailable
```

---

### 14.5 Contradictions

证据之间互相冲突。

例子：

```text
checkout logs suggest payment timeout, but payment-service metrics are normal
deployment happened 45 minutes before alert, outside correlation window
redis latency increased slightly, but not enough to explain p95 spike
```

---

## 15. Confidence Scoring and Calibration Strategy

### 15.1 不做复杂 ML 模型

MVP 不假装做了复杂的 ML confidence model。

第一版采用：

```text
Explainable scoring policy
```

原因：

1. 可解释；
2. 适合面试；
3. 易于调试；
4. 符合生产系统审慎原则；
5. 未来可以用 Event Trace + human feedback 校准权重。

---

### 15.2 权重从哪里来

V3 必须坦诚说明：

> MVP 阶段的 confidence weights 来自 SRE 经验、故障模式常识和 demo 场景调参。它们不是从历史 incident 数据自动学习出来的。

这是一个刻意 trade-off：

| 选择 | 优点 | 缺点 |
|---|---|---|
| 手工可解释权重 | 透明、可调试、实现快、适合 MVP | 不具备数据校准能力 |
| 历史数据学习权重 | 更有统计依据 | 需要大量真实 incident、成本高、MVP 不现实 |

面试表述：

> 我没有在 MVP 阶段伪装成做了复杂 ML。第一版权重是经验驱动的可解释 policy。优势是可审计、可调试，缺点是不来自真实数据。这个缺口我通过 Event Trace + human feedback 设计了后续 calibration path。

---

### 15.3 Calibration Strategy

未来权重校准路径：

```text
Event Trace
  → Human Feedback
  → Compare agent decision with final RCA
  → Identify over-weighted / under-weighted evidence
  → Adjust pattern confidence weights
  → Add regression test
  → Re-run historical incident set
```

示例：

```json
{
  "incident_id": "inc_20260428_1003",
  "agent_selected": "deployment_regression",
  "final_root_cause": "downstream_dependency_latency",
  "agent_was_correct": false,
  "overweighted_evidence": [
    "deploy_event_near_alert_window"
  ],
  "underweighted_evidence": [
    "payment-service endpoint-level p99 latency"
  ],
  "action": "reduce deploy correlation weight when dependency metrics show endpoint-level latency spike"
}
```

---

### 15.4 Confidence Result 必须包含

```text
confidence_score
confidence_level
supporting_factors
counter_factors
missing_evidence
contradictions
decision
calibration_notes
```

不是只输出一个分数。

---

### 15.5 Decision Threshold

```text
score >= 0.80
  → likely_root_cause

0.60 <= score < 0.80
  → probable_root_cause

0.40 <= score < 0.60
  → uncertain_requires_more_evidence

score < 0.40
  → insufficient_evidence
```

如果存在强 contradiction，即使分数较高，也应触发人工升级或降低等级。

如果 top two hypotheses 分数接近，比如：

```text
score_gap < 0.10
```

则进入：

```text
competing_hypotheses
```

而不是强行选择唯一根因。

---

### 15.6 High Confidence 示例

```json
{
  "hypothesis": "deployment_regression",
  "confidence_score": 0.86,
  "confidence_level": "high",
  "supporting_factors": [
    "deployment occurred 2 minutes before alert",
    "error rate increased immediately after deployment",
    "new log signature appeared after deployment",
    "code diff changed payment timeout from 2000ms to 500ms"
  ],
  "counter_factors": [
    "payment-service did not show elevated 5xx"
  ],
  "missing_evidence": [],
  "contradictions": [],
  "decision": "likely_root_cause",
  "calibration_notes": "MVP weight is manually assigned based on SRE diagnostic experience."
}
```

---

### 15.7 Competing Hypotheses 示例

```json
{
  "top_hypotheses": [
    {
      "hypothesis": "deployment_regression",
      "confidence_score": 0.64,
      "supporting_factors": [
        "deployment occurred 8 minutes before alert",
        "error rate increased after deployment",
        "new timeout logs appeared"
      ],
      "counter_factors": [
        "same timeout logs also appeared in previous version",
        "downstream payment latency also increased"
      ]
    },
    {
      "hypothesis": "downstream_dependency_latency",
      "confidence_score": 0.58,
      "supporting_factors": [
        "payment timeout logs observed",
        "payment-service p95 latency increased",
        "checkout depends on payment-service"
      ],
      "counter_factors": [
        "payment-service 5xx did not increase",
        "latency increase is moderate"
      ]
    }
  ],
  "score_gap": 0.06,
  "decision": "competing_hypotheses",
  "rationale": "Both hypotheses are plausible. Deployment regression is slightly stronger due to change correlation, but downstream latency remains a material competing cause."
}
```

---

## 16. Hypothesis Comparison Policy

V3 新增 Hypothesis Comparison，专门处理多个 hypothesis 竞争。

### 16.1 什么时候进入比较模式

满足任一条件：

```text
top_1_score >= 0.60
top_2_score >= 0.50
score_gap < 0.10
```

或：

```text
top hypothesis has material contradictions
second hypothesis has strong supporting evidence
```

---

### 16.2 比较时看什么

1. 哪个 hypothesis 有更强的 decisive evidence；
2. 哪个 hypothesis 有更少的 material contradictions；
3. 哪个 hypothesis 缺失的 critical evidence 更少；
4. 哪个 hypothesis 更符合时间线；
5. 哪个 hypothesis 更能解释全部 symptoms；
6. 是否应该输出 single RCA，还是 competing hypotheses report。

---

### 16.3 输出策略

| 情况 | 输出 |
|---|---|
| top score 高且 gap 大 | likely_root_cause |
| top score 中等但 gap 明显 | probable_root_cause |
| top two 分数接近 | competing_hypotheses |
| evidence 矛盾明显 | escalate_to_human |
| evidence 不足 | insufficient_evidence |

---

## 17. Demo Scenarios

V3 第一版包含五个场景。

---

### 17.1 Scenario A：High Confidence Deployment Regression

#### 故障描述

`order-service` 发布新版本后，错误率从 `0.2%` 升到 `8.7%`。

#### Evidence

```text
deploy event: order-service v1.2.3 deployed at 10:01
alert fired at 10:03
error rate increased after deployment
logs show "payment timeout after 500ms"
git diff shows timeout changed from 2000ms to 500ms
payment-service itself did not show significant 5xx
```

#### Expected Result

```text
Root cause: deployment regression
Confidence: 0.86
Decision: likely_root_cause
```

#### 展示重点

- 多证据一致；
- 时间线清晰；
- Git diff 与日志签名一致；
- 可输出明确建议。

---

### 17.2 Scenario B：High Confidence OOMKilled Infrastructure Issue

#### 故障描述

`recommend-service` 错误率升高，Pod 持续重启。

#### Evidence

```text
Kubernetes event shows OOMKilled
restart count increased
memory usage near limit
error spike near restart window
no recent deploy
```

#### Expected Result

```text
Root cause: memory pressure / OOMKilled
Confidence: 0.91
Decision: likely_root_cause
```

#### 展示重点

- K8s event 是强证据；
- restart 与 error spike 强相关；
- 资源类故障可以高置信判断。

---

### 17.3 Scenario C：Medium Confidence Downstream Dependency Latency

#### 故障描述

`checkout-service` P95 latency 升高，真正问题可能在 `payment-service` 或 Redis。

#### Evidence

```text
checkout latency increased
checkout CPU and memory normal
checkout logs show payment timeout
payment-service latency increased
redis latency slightly increased
service topology shows checkout depends on payment-service
```

#### Expected Result

```text
Root cause: downstream dependency latency
Confidence: 0.74
Decision: probable_root_cause
```

#### 展示重点

- 根因不在告警服务本身；
- 需要拓扑上下文；
- 有一定不确定性；
- 不是所有 RCA 都应该 0.9+ confidence。

---

### 17.4 Scenario D：Low Confidence Uncertain Incident

#### 故障描述

`checkout-service` latency 升高，但 evidence 矛盾或不足。

#### Evidence

```text
checkout latency increased
some payment timeout logs observed
payment-service latency remained normal
redis latency increase was minor
recent deployment was 45 minutes before alert
no clear new log signature
trace data unavailable
feature flag history unavailable
```

#### Expected Result

```text
Root cause: unknown
Confidence: 0.42
Decision: escalate_to_human
```

#### Agent 应输出

```text
Evidence is insufficient for an automated RCA.
Escalate to human on-call.

Suggested next probes:
1. Check Redis slowlog.
2. Compare checkout latency by dependency.
3. Inspect recent traffic shift or feature flag changes.
4. Verify whether payment timeout is symptom or cause.
```

#### 展示重点

- Agent 不胡说；
- 知道何时停止；
- 能给下一步 probe；
- 有生产系统边界意识。

---

### 17.5 Scenario E：Competing Hypotheses

#### 故障描述

`order-service` 错误率升高，存在两个接近的候选根因：

1. 最近发布引入 regression；
2. 下游 payment-service 延迟升高导致 timeout。

#### Evidence

```text
order-service deployed v1.2.3 at 10:00
alert fired at 10:08
error rate increased after deployment
logs show payment timeout errors
git diff shows retry timeout config changed
payment-service p95 latency increased moderately
payment-service 5xx remained normal
same timeout logs appeared at low frequency before deploy
checkout/payment topology confirms dependency
```

#### Hypothesis Scores

```text
deployment_regression: 0.64
downstream_dependency_latency: 0.58
score_gap: 0.06
```

#### Expected Result

```text
Decision: competing_hypotheses

Leading hypothesis:
- deployment_regression

Competing hypothesis:
- downstream_dependency_latency

Rationale:
- deployment regression is slightly stronger due to change correlation.
- downstream dependency latency remains plausible due to payment latency increase and timeout logs.
- confidence gap is too small for a definitive RCA.
```

#### Recommended Output

```text
Probable cause: deployment_regression
Competing cause: downstream_dependency_latency
Decision: competing_hypotheses
Next probes:
1. Compare timeout error rate before and after deployment.
2. Check payment-service latency by endpoint.
3. Roll back order-service in staging or canary and compare error rate.
4. Inspect retry timeout config effect on payment calls.
```

#### 展示重点

- 真实生产中常见多个根因竞争；
- Agent 不只是高/低置信；
- Verification Engine 能比较两个接近 hypothesis；
- 决策不是硬选，而是保留 competing hypothesis；
- 这是比 Scenario A-D 更强的面试展示场景。

---

## 18. Minimal Module Structure

V3 的代码结构仍然保持瘦身，但增加 `comparison`。

```text
sre-production-agent/
├── README.md
├── docs/
│   ├── architecture.md
│   ├── demo-scenarios.md
│   ├── verification-model.md
│   ├── confidence-policy.md
│   ├── calibration-strategy.md
│   ├── hypothesis-comparison.md
│   ├── event-trace.md
│   ├── event-trace-consumption.md
│   ├── local-k8s-setup.md
│   ├── interview-walkthrough.md
│   └── interview-qa.md
├── src/
│   └── sre_agent/
│       ├── __init__.py
│       ├── domain/
│       │   ├── incident_task.py
│       │   ├── evidence.py
│       │   ├── diagnostic_pattern.py
│       │   ├── hypothesis.py
│       │   ├── verification_result.py
│       │   ├── confidence_result.py
│       │   ├── hypothesis_comparison.py
│       │   ├── investigation_decision.py
│       │   └── rca_report.py
│       ├── evidence/
│       │   ├── provider.py
│       │   ├── static_evidence_provider.py
│       │   └── evidence_loader.py
│       ├── patterns/
│       │   ├── pattern_registry.py
│       │   └── builtin_patterns.py
│       ├── hypothesis/
│       │   └── hypothesis_engine.py
│       ├── verification/
│       │   ├── verification_engine.py
│       │   ├── confidence_scorer.py
│       │   └── hypothesis_comparator.py
│       ├── event_trace/
│       │   ├── event_trace_store.py
│       │   └── sqlite_event_trace_store.py
│       ├── report/
│       │   └── markdown_reporter.py
│       └── cli/
│           └── investigate.py
├── examples/
│   ├── alerts/
│   │   ├── deployment_regression.json
│   │   ├── oom_killed.json
│   │   ├── downstream_latency.json
│   │   ├── uncertain_incident.json
│   │   └── competing_hypotheses.json
│   ├── evidence/
│   │   ├── deployment_regression.json
│   │   ├── oom_killed.json
│   │   ├── downstream_latency.json
│   │   ├── uncertain_incident.json
│   │   └── competing_hypotheses.json
│   └── reports/
├── k8s/
│   └── kind-sre-agent.yaml
├── tests/
└── Makefile
```

---

## 19. CLI Design

### 19.1 基础调用

```bash
python -m sre_agent.cli.investigate \
  --alert examples/alerts/deployment_regression.json \
  --evidence examples/evidence/deployment_regression.json \
  --output examples/reports/deployment_regression_rca.md
```

---

### 19.2 展示 Event Trace

```bash
python -m sre_agent.cli.investigate \
  --alert examples/alerts/uncertain_incident.json \
  --evidence examples/evidence/uncertain_incident.json \
  --output examples/reports/uncertain_incident_report.md \
  --show-trace
```

---

### 19.3 展示 Competing Hypotheses

```bash
python -m sre_agent.cli.investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output examples/reports/competing_hypotheses_report.md \
  --show-trace
```

---

### 19.4 预期终端输出

```text
Incident created: inc_20260428_1008

Evidence loaded:
- metric: error_rate_spike
- deploy: deploy_event_near_alert_window
- log: timeout_error
- git: retry_timeout_config_change
- metric: downstream_latency_spike
- topology: service_dependency

Hypotheses generated:
- deployment_regression
- downstream_dependency_latency
- pod_oom_killed

Verifying hypotheses:
- deployment_regression: support=3, counter=2, missing=1
- downstream_dependency_latency: support=3, counter=2, missing=0
- pod_oom_killed: support=0, counter=2, missing=1

Confidence scored:
- deployment_regression: score=0.64
- downstream_dependency_latency: score=0.58
- pod_oom_killed: score=0.18

Hypothesis comparison:
- score_gap=0.06
- decision=competing_hypotheses
- leading=deployment_regression
- competing=downstream_dependency_latency

Report generated:
examples/reports/competing_hypotheses_report.md
```

---

## 20. Report Format

### 20.1 High Confidence RCA Report

```markdown
# RCA Draft: HighErrorRate on order-service

## Decision

Decision: likely_root_cause  
Confidence: 0.86  
Selected hypothesis: deployment_regression

## Summary

order-service triggered a HighErrorRate alert at 10:03 UTC.
The most likely root cause is a deployment regression introduced at 10:01 UTC, where the payment client timeout was reduced from 2000ms to 500ms.

## Why This Is Likely

Supporting evidence:

1. Deployment occurred 2 minutes before the alert.
2. Error rate increased immediately after deployment.
3. Logs show repeated payment timeout after 500ms.
4. Git diff confirms timeout changed from 2000ms to 500ms.

Counter evidence:

1. payment-service itself did not show elevated 5xx.

Missing evidence:

None.

Contradictions:

None.

## Timeline

| Time | Event |
|---|---|
| 10:01 | order-service v1.2.3 deployed |
| 10:03 | error rate increased |
| 10:04 | payment timeout logs appeared |
| 10:05 | HighErrorRate alert fired |

## Recommended Actions

1. Roll back order-service to v1.2.2.
2. Restore payment timeout to 2000ms.
3. Add canary validation for timeout-sensitive configuration.
4. Add dependency timeout error ratio alert.

## Event Trace

See: event_trace/inc_20260428_1003.json
```

---

### 20.2 Low Confidence Escalation Report

```markdown
# Escalation Report: LatencySpike on checkout-service

## Decision

Decision: escalate_to_human  
Confidence: 0.42  
Selected hypothesis: none

## Summary

checkout-service latency increased, but available evidence is insufficient to determine a reliable root cause.

The agent found weak support for downstream dependency latency, but multiple counter signals and missing data prevent an automated RCA.

## Supporting Evidence

1. checkout-service latency increased.
2. Some payment timeout logs were observed.

## Counter Evidence

1. payment-service latency remained normal.
2. Redis latency increase was minor.
3. Recent deployment occurred 45 minutes before the alert, outside the main correlation window.

## Missing Evidence

1. Dependency-level latency breakdown.
2. Trace samples.
3. Feature flag change history.
4. Traffic shift history.

## Contradictions

1. checkout logs suggest payment timeout, but payment-service metrics are normal.

## Suggested Next Probes

1. Check Redis slowlog.
2. Compare checkout latency by dependency.
3. Inspect recent feature flag changes.
4. Check traffic split or routing changes.
5. Collect trace samples for slow requests.

## Reason For Escalation

The confidence score is below the probable RCA threshold and there is at least one material contradiction.
Automated conclusion would be unsafe.
```

---

### 20.3 Competing Hypotheses Report

```markdown
# Competing Hypotheses Report: HighErrorRate on order-service

## Decision

Decision: competing_hypotheses  
Leading hypothesis: deployment_regression  
Competing hypothesis: downstream_dependency_latency  
Score gap: 0.06

## Summary

order-service error rate increased after a recent deployment, while downstream payment-service latency also increased moderately.
The agent identified two plausible root cause hypotheses. The deployment regression hypothesis is slightly stronger due to change correlation, but the confidence gap is too small for a definitive RCA.

## Hypothesis Comparison

| Hypothesis | Score | Supporting Evidence | Counter Evidence | Missing Evidence |
|---|---:|---:|---:|---:|
| deployment_regression | 0.64 | 3 | 2 | 1 |
| downstream_dependency_latency | 0.58 | 3 | 2 | 0 |

## Why Deployment Regression Leads

1. Deployment occurred 8 minutes before the alert.
2. Error rate increased after deployment.
3. Git diff shows retry timeout config changed.

## Why Downstream Dependency Remains Plausible

1. Logs show payment timeout errors.
2. payment-service p95 latency increased.
3. order-service depends on payment-service.

## Counter Evidence

Against deployment regression:

1. Similar timeout logs appeared at low frequency before deploy.
2. payment-service latency also increased.

Against downstream dependency latency:

1. payment-service 5xx did not increase.
2. Latency increase is moderate.

## Suggested Next Probes

1. Compare timeout error rate before and after deployment.
2. Check payment-service latency by endpoint.
3. Roll back order-service in staging or canary and compare error rate.
4. Inspect retry timeout config effect on payment calls.

## Reason For Non-final RCA

The top two hypotheses have a score gap of only 0.06.
Selecting a single root cause would hide a material competing explanation.
```

---

## 21. Local Kubernetes Environment

虽然 V3 第一阶段先用 static evidence，但本地 K8s 仍然要准备，因为后续要从 mock 走到半真实环境。

### 21.1 推荐方案

```text
Docker Desktop + kind
```

原因：

1. 轻量；
2. 可脚本化；
3. 适合 demo；
4. 适合 CI；
5. 比 Docker Desktop 内置 Kubernetes 更可控。

---

### 21.2 工具清单

```text
Docker Desktop
Homebrew
kubectl
kind
helm
```

后续再加：

```text
Prometheus
Grafana
Loki
Alertmanager
```

---

### 21.3 kind 配置

```yaml
# k8s/kind-sre-agent.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: sre-agent
nodes:
  - role: control-plane
  - role: worker
  - role: worker
```

创建集群：

```bash
kind create cluster --config k8s/kind-sre-agent.yaml
```

验证：

```bash
kubectl get nodes -o wide
kubectl get pods -A
```

---

### 21.4 Docker Desktop 资源建议

M3 Max 64G 推荐：

```text
CPU: 6-8 cores
Memory: 12-16 GB
Disk: 80-120 GB
```

第一阶段 demo：

```text
CPU: 4 cores
Memory: 8 GB
Disk: 40 GB
```

也足够。

---

## 22. Implementation Plan

### Phase 0：Interview Narrative Freeze

目标：

> 先冻结面试叙事，再反推实现优先级。

产物：

```text
docs/interview-walkthrough.md
docs/interview-qa.md
docs/verification-model.md
docs/confidence-policy.md
docs/calibration-strategy.md
docs/hypothesis-comparison.md
docs/event-trace.md
docs/event-trace-consumption.md
```

必须回答：

1. 15 分钟面试怎么讲？
2. 面试官应该记住什么？
3. 哪条链路做深？
4. 哪些模块只要存在即可？
5. 如何证明这不是 log chatbot？
6. confidence 权重怎么来的？
7. 如果两个 hypothesis 接近怎么办？
8. Event Trace 谁消费？
9. LLM 在系统里到底做什么？
10. 真实 K8s / Prometheus / Loki 接入后是否还能 work？

---

### Phase 1：Verification Chain Mock Demo

目标：

> 不接真实 K8s / Prometheus / Loki，先跑通 Hypothesis → Verification → Confidence → Decision。

产物：

```text
IncidentTask
Evidence
DiagnosticPattern
Hypothesis
VerificationResult
ConfidenceResult
HypothesisComparison
InvestigationDecision
MarkdownReporter
CLI
```

完成标准：

```bash
python -m sre_agent.cli.investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --show-trace
```

能输出：

```text
deployment_regression score=0.64
downstream_dependency_latency score=0.58
decision=competing_hypotheses
```

---

### Phase 2：Event Trace + Report

目标：

> 让调查过程可审计，而不是只输出结果。

产物：

```text
SQLiteEventTraceStore
event trace export
RCA report
Escalation report
Competing hypotheses report
```

完成标准：

1. 每次调查生成 event trace；
2. report 中引用 trace；
3. 能展示 high confidence、low confidence、competing hypotheses 三种结果；
4. 能解释 score 来自哪里；
5. 能展示权重 calibration 的未来路径。

---

### Phase 3：Local K8s Evidence Provider

目标：

> 从 static evidence 走向半真实环境。

产物：

```text
kind cluster
demo services
simple fault injection
metric evidence provider
log evidence provider
k8s evidence provider
```

注意：

Prometheus / Loki / K8s 只是 evidence provider，不是主角。

---

### Phase 4：LLM Synthesis

目标：

> LLM 只基于结构化 evidence 和 verification result 生成更自然的报告。

约束：

```text
LLM cannot invent evidence.
LLM cannot override verification decision.
LLM can only summarize structured investigation result.
```

---

### Phase 5：Interview Packaging

目标：

> 把项目包装成求职资产。

产物：

```text
README.md
architecture diagram
demo script
interview walkthrough
interview Q&A
resume bullets
2-minute pitch
10-minute deep dive
```

---

## 23. Interview Q&A

### Q1: 这和普通 log chatbot 有什么区别？

回答：

> 普通 log chatbot 是用户问问题，LLM 从日志里总结答案。我的系统是告警驱动的 RCA workflow。它先收集 evidence，再生成 hypothesis，然后对每个 hypothesis 做 supporting evidence、counter evidence、missing evidence 和 contradictions 检查，最后才给 confidence 和 decision。LLM 不直接猜根因，只做结构化结果的总结。

---

### Q2: 这和 PagerDuty / AIOps 有什么区别？

回答：

> AIOps 通常更偏告警降噪、事件聚合和自动化响应。我这个 MVP 聚焦在 RCA 判断链路，尤其是 hypothesis verification 和 confidence decision。重点不是替代 AIOps，而是验证一个更细的能力：当一个 incident 发生时，Agent 如何基于证据判断 root cause，以及如何知道自己不确定。

---

### Q3: confidence 权重怎么来的？

回答：

> MVP 阶段权重来自 SRE 经验、故障模式常识和 demo 场景调参。我没有伪装成它是从历史 incident 自动学习出来的。这个选择的优点是可解释、可调试、适合 MVP；缺点是不具备数据校准能力。后续会通过 Event Trace 和 human feedback 做 calibration：比较 Agent 判断和人工最终 RCA，识别被高估或低估的 evidence，再调整权重并加入 regression tests。

---

### Q4: 如果换成真实 Prometheus / Loki / K8s 数据，还能 work 吗？

回答：

> 核心链路不依赖 mock 数据。mock 只是第一阶段的 evidence provider。真实接入后，Prometheus、Loki、K8s、GitHub 都只是替换 evidence provider。只要输出统一的 Evidence model，后面的 hypothesis、verification、confidence 和 decision 逻辑不变。这也是我先做 evidence abstraction 的原因。

---

### Q5: LLM 在 RCA 里到底扮演什么角色？

回答：

> LLM 不应该直接做 root cause oracle。它更适合做 synthesis：把结构化 evidence、verification result、confidence decision 总结成人能读懂的 RCA。它不能发明 evidence，不能绕过 verification，不能覆盖 confidence decision。这样可以利用 LLM 的表达能力，同时控制幻觉风险。

---

### Q6: 如果 evidence 之间矛盾怎么办？

回答：

> 矛盾本身是 first-class signal。VerificationResult 里有 contradictions 字段。如果存在 material contradiction，系统会降低 confidence，或者直接进入 escalate_to_human。Scenario D 就是这个设计：checkout 日志显示 payment timeout，但 payment-service 指标正常，系统不会强行下结论，而是输出 escalation report 和 next probes。

---

### Q7: 如果两个 hypothesis 分数接近怎么办？

回答：

> V3 新增了 competing hypotheses 模式。如果 top two hypotheses 都有支持证据，且 score gap 小于阈值，比如 0.10，系统不会强行选择唯一根因，而是输出 competing hypotheses report。Scenario E 展示 deployment regression 和 downstream dependency latency 两个假设同时成立一部分，最终保留主假设和竞争假设，并给出下一步 probe。

---

### Q8: Event Trace 谁消费？

回答：

> 三类消费。第一，面试展示时 CLI `--show-trace` 直接展示调查 timeline。第二，复盘时 RCA report 自动引用关键 trace 节点，让 on-call engineer 或 SRE lead 审查判断过程。第三，学习时 human feedback 写入 trace，用于后续校准 confidence weights 和沉淀 diagnostic patterns。

---

### Q9: 这个系统能处理多深的调用链？

回答：

> MVP 不追求任意深调用链。第一版只处理一到两层 dependency，因为目标是验证 verification chain，而不是做完整 topology engine。未来接入 service map 或 traces 后，可以把 dependency evidence 扩展为多跳，但核心仍然是 evidence → hypothesis → verification。

---

### Q10: 为什么不直接用 LLM agent 自主查询所有工具？

回答：

> 生产系统里，完全自由的 agent 容易不可控：查询路径不可预测、结论不可审计、容易把相关性当因果。我的设计是让 agent 的判断过程结构化：哪些 evidence 要收集，哪些 hypothesis 要验证，哪些 counter evidence 要检查，都有明确模型。LLM 可以参与总结和辅助推理，但不应该绕过 workflow。

---

### Q11: 你的系统怎么避免硬编码 demo？

回答：

> MVP 的 diagnostic patterns 确实是人工定义的，这一点我会明确承认。但它不是简单 if-else，而是把故障模式拆成 evidence requirements、verification strategy 和 confidence policy。后续通过 Event Trace + human feedback 可以把人工复盘结果沉淀为新 pattern 或调整现有权重。第一阶段的重点是验证判断链路，不是声称已经自动学习所有故障模式。

---

### Q12: 为什么先做 mock，不直接接真实 K8s？

回答：

> 因为项目的核心风险不是 K8s API 能不能调通，而是 RCA 判断链路是否成立。先用 mock evidence 可以快速验证 hypothesis、verification、confidence 和 decision 的设计。等核心链路稳定后，再把 static evidence provider 替换成 Prometheus、Loki、K8s provider。这样实现顺序更符合风险优先级。

---

## 24. Hermes Execution Plan

### 24.1 Hermes 角色

Hermes 是本项目的 SRE 和架构助理。

它承担四类职责：

1. 架构审查员；
2. 代码实现助手；
3. Demo scenario 生成器；
4. 面试材料生成器。

---

### 24.2 Hermes 执行原则

不要让 Hermes 一次性写完整系统。

采用：

```text
contract → implementation → validation
```

每一步必须有：

1. 输入输出契约；
2. 测试；
3. 可运行命令；
4. 明确验收标准。

---

### 24.3 给 Hermes 的 Step A Prompt

```markdown
# Role

You are my senior SRE architecture and implementation assistant.

We are building a job-search MVP named **SRE Production Agent MVP**.

The goal is not to build a full Resolve.ai clone. The goal is to build an interview-ready RCA Agent that demonstrates one deep capability:

**Hypothesis Verification for production incidents.**

# Core Thesis

Most AI SRE demos fail because they let LLMs guess root causes from logs.

This MVP takes a different approach:

1. Alert-driven, not chat-driven.
2. Evidence-first, not LLM-first.
3. Diagnostic patterns first, not free-form reasoning.
4. Competing hypotheses must be explicitly compared.
5. Every root cause hypothesis must be verified against supporting evidence, counter evidence, missing evidence, and contradictions.
6. The agent must know when not to conclude and when to escalate to a human.
7. Every investigation step is recorded in an Event Trace Store, which becomes the data backbone for audit, review, and future skill learning.

# MVP Scope

Implement a local-first mock workflow first.

Do not connect to real Kubernetes, Prometheus, Loki, or GitHub in Step A.
Use static evidence providers.

Focus on:

1. Incident Task
2. Evidence
3. Event Trace Store
4. Diagnostic Pattern
5. Hypothesis
6. Verification Result
7. Confidence Scoring
8. Hypothesis Comparison
9. RCA / Escalation / Competing Hypotheses Report

# Required Demo Scenarios

1. High-confidence deployment regression
2. High-confidence OOMKilled infrastructure issue
3. Medium-confidence downstream dependency latency
4. Low-confidence uncertain incident requiring human escalation
5. Competing hypotheses: deployment regression vs downstream dependency latency

# Key Design Requirement

The most important part is the chain:

Hypothesis → Verification → Confidence Scoring → Comparison → Decision

This chain must be explicit, inspectable, and explainable.

# Your Task: Step A

Do not write code yet.

Produce a design review and implementation plan with:

1. Scope confirmation
2. Updated architecture
3. Minimal module structure
4. Domain model design
5. Diagnostic pattern design
6. Verification model design
7. Confidence scoring policy
8. Calibration strategy
9. Hypothesis comparison policy
10. Event trace design
11. Event trace consumption model
12. Five demo scenarios
13. Interview Q&A
14. Overengineering risks
15. Step B implementation plan
```

---

## 25. Interview Walkthrough

### 25.1 2 分钟版本

> 我做了一个面向 Kubernetes 微服务系统的 AI SRE RCA Agent。它不是一个日志聊天机器人，而是一个 verification-first 的事故调查 workflow。  
> 输入是一条告警，系统会收集 metric、log、deploy、K8s event 等 evidence，然后根据 diagnostic patterns 生成 root cause hypotheses。每个 hypothesis 都必须经过 supporting evidence、counter evidence、missing evidence 和 contradictions 的验证，再通过可解释 scoring policy 给出 confidence 和 decision。  
> 如果两个 hypothesis 分数接近，它不会强行选择唯一根因，而是输出 competing hypotheses report。如果证据不足或矛盾，它不会编 root cause，而是输出 escalation report 和下一步 probe。整个调查过程会写入 Event Trace，后续可用于审计、复盘和 weight calibration。  
> 这个项目展示的重点不是接了多少工具，而是把生产事故判断过程结构化、可审计，并用 LLM 做受约束的总结，而不是让 LLM 直接猜根因。

---

### 25.2 10 分钟深讲结构

```text
1. 为什么不是 log chatbot
2. 为什么选择 verification-first
3. Evidence model
4. Diagnostic pattern system
5. Verification model
6. Confidence scoring and calibration strategy
7. Competing hypotheses handling
8. Event Trace consumption model
9. Five demo scenarios
10. What LLM does and does not do
11. How this extends to real K8s / Prometheus / Loki
```

---

## 26. Resume Bullets

### 26.1 完整版

> 设计并实现一个面向 Kubernetes 微服务系统的 verification-first AI SRE RCA Agent：以告警为入口，自动收集 metrics/logs/K8s events/deploy/code evidence，基于 diagnostic patterns 生成多个根因假设，并通过 supporting evidence、counter evidence、missing evidence 与 contradictions 进行验证，输出可解释 confidence、competing hypotheses / RCA / escalation decision 和可审计 Event Trace；探索规则优先、LLM 受约束总结的生产级 Agent 工作流。

---

### 26.2 简洁版

> 构建 Kubernetes 场景下的 AI SRE RCA Agent：以告警为入口，基于 evidence 和 diagnostic patterns 生成并验证根因假设，支持 competing hypotheses、可解释 confidence、RCA / escalation report，并通过 Event Trace 记录调查轨迹，探索 verification-first 的生产级 Agent 工作流。

---

### 26.3 面试口播版

> 我这个项目的重点不是做一个日志问答机器人，而是把 SRE RCA 拆成 evidence、hypothesis、verification、confidence、comparison 和 decision。Agent 必须证明为什么相信某个根因，也必须知道什么时候证据不足，或者什么时候存在多个竞争假设，不能强行收敛。

---

## 27. Key Trade-offs

### 27.1 为什么少做 Collector

Collector 多不等于项目有深度。

V3 中 Collector 被降级为 Evidence Provider，原因：

1. 面试官不会因为你多接一个数据源就相信你能做 Agent；
2. 真实差异化在 RCA 判断过程；
3. 证据验证比数据接入更能体现 SRE 经验；
4. Mock evidence 足够先验证核心链路。

---

### 27.2 为什么前置失败案例

三个成功案例容易显得像 scripted demo。

失败 / 不确定案例能证明：

1. Agent 有边界；
2. 不会强行胡说；
3. 能识别 evidence insufficient；
4. 能输出 next probes；
5. 设计者理解真实生产环境的不确定性。

---

### 27.3 为什么新增 competing hypotheses

真实生产事故中，经常不是“一个根因明显胜出”，而是多个 hypothesis 各有证据。

Competing hypotheses 场景能证明：

1. 系统不是单纯高低置信；
2. Verification Engine 能比较候选解释；
3. Agent 不把相关性轻易当因果；
4. 决策可以保留不确定性；
5. 后续 probe 可以围绕区分两个 hypothesis 设计。

---

### 27.4 为什么 Event Trace 前置

没有 Event Trace，“可审计”就是一句空话。

Event Trace 让系统从一次性 demo 变成：

```text
auditable workflow
reviewable decision process
future skill learning dataset
```

---

### 27.5 为什么 LLM 后置

LLM 最大价值不是猜根因，而是：

1. 总结结构化结果；
2. 生成易读 RCA；
3. 补充解释；
4. 改善面向人的输出。

LLM 不应该：

1. 发明 evidence；
2. 绕过 verification；
3. 覆盖 confidence decision；
4. 直接决定 root cause。

---

### 27.6 为什么手工权重是合理 MVP 选择

手工权重不是终点，但在 MVP 阶段合理。

原因：

1. 真实 incident 数据难拿；
2. 求职 MVP 需要快速证明判断链路；
3. 可解释权重比黑盒分数更适合面试；
4. Event Trace 已经预留后续 calibration 路径。

面试时必须坦诚：

```text
MVP 的权重是经验驱动的，不是历史数据学习出来的。
```

这不是弱点，而是成熟的边界意识。

---

## 28. Current Conclusion

V3 的核心调整是：

```text
少做 collector
多做 verification

少讲接入
多讲判断

少讲完整度
多讲不确定性处理

少讲 LLM
多讲 evidence + event trace

少讲单一根因
多讲 competing hypotheses

少讲神奇 confidence
多讲 calibration strategy
```

本项目最值得做深的一条链路是：

```text
Hypothesis → Verification → Confidence Scoring → Comparison → Decision
```

只要这条链路做扎实，这个项目就不是普通 AI Demo，而是一个能体现 SRE 生产经验、架构判断力和 AI Agent 工程化能力的求职资产。

第一阶段目标：

```text
一个本地可跑 mock demo
五个故障场景
一套 diagnostic pattern system
一条完整 verification chain
一个可解释 confidence policy
一个 competing hypotheses comparison
一份 RCA report
一份 escalation report
一份 competing hypotheses report
一次完整 event trace
一套 interview Q&A
```

完成后，它可以作为 AI Infra / SRE Agent / 平台工程方向求职中的核心展示项目。
