# Architecture

## High-Level Design

The SRE Production Agent follows a **hexagonal architecture** pattern: the core RCA workflow is a pure Java library with zero framework dependencies, and external interfaces (CLI, REST API, Web UI) are thin adapters that delegate to the same workflow.

```
┌─────────────────────────────────────────────────────────┐
│                     Adapters                             │
│                                                          │
│   sre-agent-cli          sre-agent-server                │
│   (Picocli)              (Spring Boot 3.x)               │
│        │                        │                        │
│        └─────────┬──────────────┘                        │
│                  ↓                                       │
├──────────────────────────────────────────────────────────┤
│                  Core Workflow                           │
│                                                          │
│         InvestigationWorkflow                            │
│         (orchestrator, zero Spring dependency)            │
│                  ↓                                       │
│  ┌──────────────────────────────────────────────────┐    │
│  │                Domain Layer                       │    │
│  │                                                   │    │
│  │  IncidentTask  Evidence  DiagnosticPattern        │    │
│  │  Hypothesis    VerificationResult                 │    │
│  │  ConfidenceResult   HypothesisComparison          │    │
│  │  InvestigationDecision  EventTraceEntry           │    │
│  └──────────────────────────────────────────────────┘    │
│                  ↓                                       │
│  ┌──────────────────────────────────────────────────┐    │
│  │              Processing Pipeline                  │    │
│  │                                                   │    │
│  │  EvidenceLoader  →  PatternRegistry               │    │
│  │       ↓                                           │    │
│  │  HypothesisEngine                                 │    │
│  │       ↓                                           │    │
│  │  VerificationEngine                               │    │
│  │       ↓                                           │    │
│  │  ConfidenceScorer                                 │    │
│  │       ↓                                           │    │
│  │  HypothesisComparator → InvestigationDecision     │    │
│  │       ↓                                           │    │
│  │  MarkdownReporter + EventTraceStore               │    │
│  │       ↓ (optional, advisory only)                 │    │
│  │  ┌── LLM Synthesis Layer ──────────────────────┐  │    │
│  │  │  LlmPromptBuilder → LlmClient → LlmReport   │  │    │
│  │  │  Synthesizer → LlmEnhancedReport             │  │    │
│  │  │  (cannot change decision/scores/evidence)    │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

---

## Maven Module Architecture

```
sre-production-agent (parent POM)
├── sre-agent-core           ← Pure Java, zero Spring
├── sre-agent-llm            ← Depends on core, zero Spring (LLM synthesis)
├── sre-agent-k8s-provider   ← Depends on core, zero Spring, zero K8s client lib (fixture-based K8s evidence)
├── sre-agent-cli            ← Depends on core + k8s-provider, uses Picocli
└── sre-agent-server         ← Depends on core + llm + k8s-provider, uses Spring Boot
```

### Why Five Modules?

| Module | Responsibility | Key Dependency |
|---|---|---|
| `sre-agent-core` | Domain model, RCA workflow, scoring, reporting | Jackson only |
| `sre-agent-llm` | LLM-assisted synthesis (advisory-only narrative) | core + Jackson |
| `sre-agent-k8s-provider` | K8s fixture evidence provider | core + Jackson |
| `sre-agent-cli` | Command-line interface | Picocli + core + k8s-provider |
| `sre-agent-server` | REST API + Web UI + LLM endpoints | Spring Boot + core + llm + k8s-provider |

### Why Core Has Zero Spring Dependency

1. **Testability** — core classes can be unit tested without Spring context startup (seconds vs milliseconds)
2. **Reusability** — the same RCA engine can run in CLI, server, Lambda, or any future adapter
3. **Separation of concerns** — domain logic should not depend on a web framework
4. **Interview signal** — demonstrates understanding of clean architecture boundaries

### Dependency Flow

```
core ← llm
core ← k8s-provider
k8s-provider ← cli
k8s-provider ← server
llm  ← server
core ← cli (also via k8s-provider)
cli  ↗   ↖ server  (no dependency between adapters)
```

### LLM Module (`sre-agent-llm`)

Step G introduced `sre-agent-llm` — a pure Java module (zero Spring dependency) that adds **advisory-only** LLM-assisted synthesis on top of the deterministic RCA pipeline.

**Key architectural invariant: the LLM layer cannot change the decision, confidence scores, or evidence.** It only adds narrative context (executive summary, reasoning, uncertainty explanation) to help on-call engineers interpret the deterministic result.

#### Module Components

| Component | Responsibility |
|---|---|
| `LlmClient` | Interface for LLM completion. Single method: `complete(LlmRequest) → LlmResponse`. Implementations are pluggable. |
| `MockLlmClient` | Deterministic mock implementation. Returns predictable RCA-assisted text without network access. Used as default when no real LLM is configured. |
| `LlmPromptBuilder` | Constructs system + user prompts from `InvestigationResult`. Embeds strict guardrails (system prompt forbids overriding decision/scores/inventing evidence). |
| `LlmReportSynthesizer` | Orchestrates: build prompt → call `LlmClient` → parse markdown sections → build `LlmEnhancedReport`. Deterministic fields always come from `InvestigationResult`, never from LLM output. |
| `LlmEnhancedReport` | Output record: base decision fields (deterministic) + LLM narrative fields (advisory). `advisoryOnly` flag is always `true`. |
| `LlmRequest` / `LlmResponse` | Value objects for the LLM client interface. |

#### Server Integration

The server module wires LLM via `LlmSynthesisService` (Spring `@Service`):
- Default: uses `MockLlmClient` (deterministic, no network, no API key needed)
- Future: `resolveClient()` checks `LLM_PROVIDER` env var; falls back to mock for any incomplete config
- Exposes REST endpoint for LLM-enhanced synthesis

#### Guardrails (防护措施)

The LLM layer is designed so that **removing it entirely does not change any investigation outcome**:

1. **Prompt guardrails** — `LlmPromptBuilder` system prompt forbids the LLM from overriding decisions, changing scores, inventing evidence, or hiding counter-evidence
2. **Structural guardrails** — `LlmReportSynthesizer` always populates `base*` fields from the deterministic `InvestigationResult`, never from LLM output
3. **Output guardrails** — `LlmEnhancedReport.advisoryOnly` is always `true`; consumers must check this flag
4. **Scope guardrails** — prompt explicitly tells the LLM not to infer K8s, EC2, RDS, ElastiCache, ALB, CMDB, or topology facts

---

## Core Workflow Detail

`InvestigationWorkflow.run()` executes 10 steps in sequence:

```
Step 1:  Load alert JSON → IncidentTask
Step 2:  Load evidence JSON → List<Evidence>
Step 3:  Load diagnostic patterns → PatternRegistry
Step 4:  Generate hypotheses → List<Hypothesis> (one per pattern)
Step 5:  Verify each hypothesis → Map<hypothesisId, VerificationResult>
Step 6:  Score confidence → List<ConfidenceResult>
Step 7:  Compare hypotheses → HypothesisComparison
Step 8:  Generate decision → InvestigationDecision
Step 9:  Generate report → String (Markdown)
Step 10: Collect event trace → List<EventTraceEntry>
```

Every step appends an `EventTraceEntry` to the trace store, creating a full audit log.

---

## Domain Model

All domain objects are **Java 21 records** — immutable and concise.

### Core Records

| Record | Purpose |
|---|---|
| `IncidentTask` | The alert that triggers investigation |
| `Evidence` | A single piece of evidence (from logs, metrics, deploy events, git) |
| `DiagnosticPattern` | A known failure mode with evidence requirements and confidence weights |
| `Hypothesis` | A candidate root cause generated from a pattern |
| `VerificationResult` | Evidence classification for a hypothesis (supporting / counter / missing / contradiction) |
| `ConfidenceResult` | Numerical score + level + factors for a hypothesis |
| `HypothesisComparison` | Leading vs competing hypotheses, score gap, decisive evidence |
| `InvestigationDecision` | Final decision type, selected hypothesis, next probes |
| `EventTraceEntry` | A single audit log entry |
| `RcaReport` | Structured report (reserved for future use) |
| `LlmEnhancedReport` | Advisory-only LLM synthesis: deterministic base fields + narrative fields (executive summary, reasoning, uncertainty, next steps, limitations, unverified proposals) |

---

## Deterministic Scoring

The `ConfidenceScorer` uses a transparent, explainable formula:

```
rawScore = pattern.baseScore
         + Σ(weight for each matched supporting evidence type)
         - Σ(|weight| for each matched counter evidence type)
         - missingPenalty
         - contradictionPenalty

score = clamp(rawScore, 0.0, 1.0)
```

**This is not machine learning.** Weights are manually assigned based on SRE diagnostic experience. The value is that every score is traceable to specific evidence and weights — you can explain *why* a hypothesis scored 0.64 instead of 0.80.

### Decision Policy

| Decision | Condition |
|---|---|
| `likely_root_cause` | top1 ≥ 0.80 and gap ≥ 0.15 |
| `probable_root_cause` | top1 ≥ 0.60 and gap ≥ 0.10 |
| `competing_hypotheses` | top1 ≥ 0.50, top2 ≥ 0.50, gap < 0.10 |
| `uncertain_requires_more` | top1 ≥ 0.40 |
| `insufficient_evidence` | top1 < 0.40 |

---

## CLI / Server as Adapters

Both adapters call the same `InvestigationWorkflow.run(alertFile, evidenceFile)` method.

### CLI Flow

```
Main.java (Picocli entry point)
  → InvestigateCommand.run()
    → InvestigationWorkflow.run(alert, evidence)
    → Write Markdown report to file
    → Optionally print event trace
```

### Server Flow

```
InvestigationController (REST)
  → InvestigationService.runScenarioE()
    → InvestigationWorkflow.run(alert, evidence)
    → InMemoryInvestigationStore.save(result)
    → Return InvestigationResponse DTO

InvestigationController (REST)
  → InvestigationService.runScenarioF()    // NEW — K8s CrashLoopBackOff
    → InvestigationWorkflow.run(k8s_alert, k8s_evidence)
    → InMemoryInvestigationStore.save(result)
    → Return InvestigationResponse DTO
```

The server adds an `InMemoryInvestigationStore` to cache results for subsequent GET requests (report, trace, summary).

---

## Event Trace Model

Every workflow step records an `EventTraceEntry`:

```
EventTraceEntry {
  eventId      → "evt_001", "evt_002", ...
  incidentId   → "inc_20260428T100800Z"
  eventType    → INCIDENT_CREATED | EVIDENCE_LOADED | HYPOTHESES_GENERATED |
                 HYPOTHESIS_VERIFIED | CONFIDENCE_SCORED | HYPOTHESES_COMPARED |
                 DECISION_MADE | REPORT_GENERATED
  timestamp    → Instant
  payload      → Map<String, Object> (step-specific data)
}
```

The event trace provides:
- **Auditability** — every step is recorded with timestamps
- **Debuggability** — trace shows exactly where scoring diverged
- **Explainability** — trace can be shown to on-call engineers

---

## Extension Points

The architecture is designed for controlled extension:

### Evidence Providers

| Extension | What to Add | Where |
|---|---|---|
| Real Prometheus metrics | `PrometheusEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| Real Loki logs | `LokiEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| K8s events (fixtures) | `K8sFixtureEvidenceProvider` | `sre-agent-k8s-provider/` |
| Real K8s API | `KubernetesEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| EC2 instance metrics | `Ec2EvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| AWS managed services (RDS, ElastiCache, ALB) | `AwsManagedServiceEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| CMDB / service topology | `CmdbTopologyProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |

### Other Extensions

| Extension | What to Add | Where |
|---|---|---|
| OpenAI-compatible LLM provider | `OpenAiCompatibleClient implements LlmClient` — HTTP client calling any OpenAI-compatible API (OpenAI, Azure OpenAI, Ollama, vLLM, etc.) | `sre-agent-llm/client/` |
| Custom LLM prompt strategies | Alternative `LlmPromptBuilder` implementations for different prompt formats or models | `sre-agent-llm/prompt/` |
| New diagnostic patterns | Add to `BuiltinPatterns` or create `CustomPatternLoader` | `sre-agent-core/patterns/` |
| Persistent store | Replace `InMemoryInvestigationStore` with JDBC/Redis | `sre-agent-server/service/` |
| More scenarios | Add alert + evidence JSON files | `examples/` |

All extensions follow the same pattern: **add new implementations of existing interfaces, don't modify core workflow logic.**

### Multi-Platform Design Note

The current MVP is K8s-centric in naming and scenarios. The core pipeline (verification → scoring → comparison) is **platform-agnostic** — it only matches on `evidenceType` strings, not on deployment platform. However, the domain model contains K8s-specific assumptions that need addressing before real multi-platform support:

**Model changes required (planned for a future step):**

| Current Field | Issue | Planned Change |
|---|---|---|
| `IncidentTask.namespace` | K8s-only concept | → `scope` (namespace / AZ / VPC / region) |
| `IncidentTask` (missing `platform`) | No way to distinguish K8s vs EC2 vs managed service | → Add `platform` field (`"kubernetes"` / `"ec2"` / `"managed_service"`) |
| `Evidence.service` | Semantically wrong for RDS, ElastiCache, ALB, etc. | → `entity` (generic resource identifier) |
| `Evidence` (missing `entityType`) | No way to classify what kind of resource this evidence belongs to | → Add `entityType` (`"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`) |

**Implemented K8s diagnostic pattern (Step J):**

| Pattern | baseScore | Supporting Evidence Types | Counter Evidence Types | Root Cause Type |
|---|---|---|---|---|
| `pod_crash_loop` | 0.25 | `container_crash_loop_backoff` (0.30), `pod_restart_count_increased` (0.20), `pod_not_ready` (0.15), `deployment_metadata` (0.05) | `no_restart_observed` (0.30), `pod_ready` (0.20), `container_running_normal` (0.20) | `container_crash_loop` |

Scenario F (`recommend-service` CrashLoopBackOff in `demo` namespace) validates this pattern, producing a `likely_root_cause` decision with score 0.95 (gap ≥ 0.15).

**New diagnostic patterns for non-K8s scenarios:**

| Pattern | Evidence Types |
|---|---|
| `ec2_instance_degradation` | CPU steal, EBS IOPS saturation, impaired status check |
| `rds_connection_exhaustion` | max_connections reached, connection timeout, replica lag |
| `elasticache_memory_pressure` | evictions spike, swap usage, replication lag |
| `managed_service_failover` | multi-AZ failover event, DNS change, endpoint shift |

**Why this is safe to defer:** The scoring and comparison engines never inspect `service` or `namespace` fields directly — they operate exclusively on `evidenceType` string matching. Adding multi-platform support requires model changes and new evidence providers, but **zero changes to the core pipeline**.

---
## 中文版

# 架构

## 高层设计

SRE Production Agent 采用**六边形架构**模式：核心 RCA（根因分析）工作流是一个零框架依赖的纯 Java 库，外部接口（CLI、REST API、Web UI）作为薄适配器，将请求委托给同一个工作流。

```
┌─────────────────────────────────────────────────────────┐
│                     Adapters                             │
│                                                          │
│   sre-agent-cli          sre-agent-server                │
│   (Picocli)              (Spring Boot 3.x)               │
│        │                        │                        │
│        └─────────┬──────────────┘                        │
│                  ↓                                       │
├──────────────────────────────────────────────────────────┤
│                  Core Workflow                           │
│                                                          │
│         InvestigationWorkflow                            │
│         (orchestrator, zero Spring dependency)            │
│                  ↓                                       │
│  ┌──────────────────────────────────────────────────┐    │
│  │                Domain Layer                       │    │
│  │                                                   │    │
│  │  IncidentTask  Evidence  DiagnosticPattern        │    │
│  │  Hypothesis    VerificationResult                 │    │
│  │  ConfidenceResult   HypothesisComparison          │    │
│  │  InvestigationDecision  EventTraceEntry           │    │
│  └──────────────────────────────────────────────────┘    │
│                  ↓                                       │
│  ┌──────────────────────────────────────────────────┐    │
│  │              Processing Pipeline                  │    │
│  │                                                   │    │
│  │  EvidenceLoader  →  PatternRegistry               │    │
│  │       ↓                                           │    │
│  │  HypothesisEngine                                 │    │
│  │       ↓                                           │    │
│  │  VerificationEngine                               │    │
│  │       ↓                                           │    │
│  │  ConfidenceScorer                                 │    │
│  │       ↓                                           │    │
│  │  HypothesisComparator → InvestigationDecision     │    │
│  │       ↓                                           │    │
│  │  MarkdownReporter + EventTraceStore               │    │
│  │       ↓ (optional, advisory only)                 │    │
│  │  ┌── LLM Synthesis Layer ──────────────────────┐  │    │
│  │  │  LlmPromptBuilder → LlmClient → LlmReport   │  │    │
│  │  │  Synthesizer → LlmEnhancedReport             │  │    │
│  │  │  (cannot change decision/scores/evidence)    │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

---

## Maven 模块架构

```
sre-production-agent (parent POM)
├── sre-agent-core           ← 纯 Java，零 Spring 依赖
├── sre-agent-llm            ← 依赖 core，零 Spring 依赖（LLM 综合分析）
├── sre-agent-k8s-provider   ← 依赖 core，零 Spring 依赖，零 K8s 客户端库（基于 fixture 的 K8s 证据）
├── sre-agent-cli            ← 依赖 core + k8s-provider，使用 Picocli
└── sre-agent-server         ← 依赖 core + llm + k8s-provider，使用 Spring Boot
```

### 为什么是五个模块？

| 模块 | 职责 | 关键依赖 |
|---|---|---|
| `sre-agent-core` | 领域模型、RCA 工作流、评分、报告 | 仅 Jackson |
| `sre-agent-llm` | LLM 辅助综合分析（仅限咨询性叙述） | core + Jackson |
| `sre-agent-k8s-provider` | K8s fixture 证据提供者 | core + Jackson |
| `sre-agent-cli` | 命令行界面 | Picocli + core + k8s-provider |
| `sre-agent-server` | REST API + Web UI + LLM 端点 | Spring Boot + core + llm + k8s-provider |

### 为什么 Core 零 Spring 依赖

1. **可测试性** — core 类可以脱离 Spring 上下文进行单元测试（毫秒级 vs 秒级）
2. **可复用性** — 同一个 RCA 引擎可以运行在 CLI、Server、Lambda 或任何未来的适配器中
3. **关注点分离** — 领域逻辑不应依赖 Web 框架
4. **面试加分** — 展示了对整洁架构边界的理解

### 依赖流向

```
core ← llm
core ← k8s-provider
k8s-provider ← cli
k8s-provider ← server
llm  ← server
core ← cli (also via k8s-provider)
cli  ↗   ↖ server  (适配器之间无依赖)
```

### LLM 模块（`sre-agent-llm`）

Step G 引入了 `sre-agent-llm` — 一个纯 Java 模块（零 Spring 依赖），在确定性 RCA 管道之上增加了**仅限咨询性**的 LLM 辅助综合分析。

**关键架构不变量：LLM 层不能更改决策、置信度分数或证据。** 它只添加叙述性上下文（执行摘要、推理过程、不确定性说明）来帮助值班工程师解读确定性结果。

#### 模块组件

| 组件 | 职责 |
|---|---|
| `LlmClient` | LLM 补全接口。单一方法：`complete(LlmRequest) → LlmResponse`。实现可插拔。 |
| `MockLlmClient` | 确定性模拟实现。返回可预测的 RCA 辅助文本，无需网络访问。未配置真实 LLM 时作为默认值使用。 |
| `LlmPromptBuilder` | 从 `InvestigationResult` 构建 system + user 提示词。嵌入严格防护措施（系统提示词禁止覆盖决策/分数/编造证据）。 |
| `LlmReportSynthesizer` | 编排流程：构建提示词 → 调用 `LlmClient` → 解析 markdown 段落 → 构建 `LlmEnhancedReport`。确定性字段始终来自 `InvestigationResult`，绝不来自 LLM 输出。 |
| `LlmEnhancedReport` | 输出记录：基础决策字段（确定性）+ LLM 叙述字段（咨询性）。`advisoryOnly` 标志始终为 `true`。 |
| `LlmRequest` / `LlmResponse` | LLM 客户端接口的值对象。 |

#### Server 集成

Server 模块通过 `LlmSynthesisService`（Spring `@Service`）接入 LLM：
- 默认：使用 `MockLlmClient`（确定性，无网络，无需 API 密钥）
- 未来：`resolveClient()` 检查 `LLM_PROVIDER` 环境变量；配置不完整时回退到 mock
- 暴露 REST 端点用于 LLM 增强综合分析

#### 防护措施（Guardrails）

LLM 层的设计确保**完全移除它不会改变任何调查结果**：

1. **提示词防护** — `LlmPromptBuilder` 系统提示词禁止 LLM 覆盖决策、更改分数、编造证据或隐藏反面证据
2. **结构防护** — `LlmReportSynthesizer` 始终从确定性 `InvestigationResult` 填充 `base*` 字段，绝不使用 LLM 输出
3. **输出防护** — `LlmEnhancedReport.advisoryOnly` 始终为 `true`；消费者必须检查此标志
4. **范围防护** — 提示词明确告知 LLM 不要推断 K8s、EC2、RDS、ElastiCache、ALB、CMDB 或拓扑事实

---

## Core 工作流详情

`InvestigationWorkflow.run()` 按顺序执行 10 个步骤：

```
Step 1:  Load alert JSON → IncidentTask
Step 2:  Load evidence JSON → List<Evidence>
Step 3:  Load diagnostic patterns → PatternRegistry
Step 4:  Generate hypotheses → List<Hypothesis> (one per pattern)
Step 5:  Verify each hypothesis → Map<hypothesisId, VerificationResult>
Step 6:  Score confidence → List<ConfidenceResult>
Step 7:  Compare hypotheses → HypothesisComparison
Step 8:  Generate decision → InvestigationDecision
Step 9:  Generate report → String (Markdown)
Step 10: Collect event trace → List<EventTraceEntry>
```

每个步骤都会向追踪存储追加一条 `EventTraceEntry`，形成完整的审计日志。

---

## 领域模型

所有领域对象均为 **Java 21 records** — 不可变且简洁。

### 核心 Records

| Record | 用途 |
|---|---|
| `IncidentTask` | 触发调查的告警 |
| `Evidence` | 单条证据（来自日志、指标、部署事件、git） |
| `DiagnosticPattern` | 已知故障模式，包含证据需求和置信度权重 |
| `Hypothesis` | 由模式生成的候选根因 |
| `VerificationResult` | 对假设的证据分类（支持 / 反对 / 缺失 / 矛盾） |
| `ConfidenceResult` | 假设的数值分数 + 等级 + 因素 |
| `HypothesisComparison` | 领先假设 vs 竞争假设、分数差距、决定性证据 |
| `InvestigationDecision` | 最终决策类型、选定假设、下一步探测 |
| `EventTraceEntry` | 单条审计日志记录 |
| `RcaReport` | 结构化报告（保留供未来使用） |
| `LlmEnhancedReport` | 仅限咨询性的 LLM 综合分析：确定性基础字段 + 叙述字段（执行摘要、推理、不确定性、下一步、局限性、未验证建议） |

---

## 确定性评分

`ConfidenceScorer` 使用透明、可解释的公式：

```
rawScore = pattern.baseScore
         + Σ(weight for each matched supporting evidence type)
         - Σ(|weight| for each matched counter evidence type)
         - missingPenalty
         - contradictionPenalty

score = clamp(rawScore, 0.0, 1.0)
```

**这不是机器学习。** 权重基于 SRE 诊断经验手动设定。其价值在于每个分数都可以追溯到具体的证据和权重 — 你可以解释*为什么*一个假设得了 0.64 而不是 0.80。

### 决策策略

| 决策 | 条件 |
|---|---|
| `likely_root_cause` | top1 ≥ 0.80 且差距 ≥ 0.15 |
| `probable_root_cause` | top1 ≥ 0.60 且差距 ≥ 0.10 |
| `competing_hypotheses` | top1 ≥ 0.50, top2 ≥ 0.50, 差距 < 0.10 |
| `uncertain_requires_more` | top1 ≥ 0.40 |
| `insufficient_evidence` | top1 < 0.40 |

---

## CLI / Server 作为适配器

两个适配器都调用同一个 `InvestigationWorkflow.run(alertFile, evidenceFile)` 方法。

### CLI 流程

```
Main.java (Picocli entry point)
  → InvestigateCommand.run()
    → InvestigationWorkflow.run(alert, evidence)
    → Write Markdown report to file
    → Optionally print event trace
```

### Server 流程

```
InvestigationController (REST)
  → InvestigationService.runScenarioE()
    → InvestigationWorkflow.run(alert, evidence)
    → InMemoryInvestigationStore.save(result)
    → Return InvestigationResponse DTO

InvestigationController (REST)
  → InvestigationService.runScenarioF()    // 新增 — K8s CrashLoopBackOff
    → InvestigationWorkflow.run(k8s_alert, k8s_evidence)
    → InMemoryInvestigationStore.save(result)
    → Return InvestigationResponse DTO
```

Server 额外添加了 `InMemoryInvestigationStore` 来缓存结果，供后续 GET 请求使用（报告、追踪、摘要）。

---

## 事件追踪模型（Event Trace）

每个工作流步骤都会记录一条 `EventTraceEntry`：

```
EventTraceEntry {
  eventId      → "evt_001", "evt_002", ...
  incidentId   → "inc_20260428T100800Z"
  eventType    → INCIDENT_CREATED | EVIDENCE_LOADED | HYPOTHESES_GENERATED |
                 HYPOTHESIS_VERIFIED | CONFIDENCE_SCORED | HYPOTHESES_COMPARED |
                 DECISION_MADE | REPORT_GENERATED
  timestamp    → Instant
  payload      → Map<String, Object> (step-specific data)
}
```

事件追踪提供：
- **可审计性** — 每个步骤都带时间戳记录
- **可调试性** — 追踪精确显示评分分歧发生的位置
- **可解释性** — 追踪结果可以展示给值班工程师

---

## 扩展点

架构设计支持受控扩展：

### 证据提供者

| 扩展 | 添加内容 | 位置 |
|---|---|---|
| 真实 Prometheus 指标 | `PrometheusEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| 真实 Loki 日志 | `LokiEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| K8s 事件（fixtures） | `K8sFixtureEvidenceProvider` | `sre-agent-k8s-provider/` |
| 真实 K8s API | `KubernetesEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| EC2 实例指标 | `Ec2EvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| AWS 托管服务（RDS、ElastiCache、ALB） | `AwsManagedServiceEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| CMDB / 服务拓扑 | `CmdbTopologyProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |

### 其他扩展

| 扩展 | 添加内容 | 位置 |
|---|---|---|
| OpenAI 兼容 LLM 提供者 | `OpenAiCompatibleClient implements LlmClient` — HTTP 客户端调用任意 OpenAI 兼容 API（OpenAI、Azure OpenAI、Ollama、vLLM 等） | `sre-agent-llm/client/` |
| 自定义 LLM 提示词策略 | 针对不同提示词格式或模型的替代 `LlmPromptBuilder` 实现 | `sre-agent-llm/prompt/` |
| 新的诊断模式 | 添加到 `BuiltinPatterns` 或创建 `CustomPatternLoader` | `sre-agent-core/patterns/` |
| 持久化存储 | 用 JDBC/Redis 替换 `InMemoryInvestigationStore` | `sre-agent-server/service/` |
| 更多场景 | 添加告警 + 证据 JSON 文件 | `examples/` |

所有扩展遵循同一模式：**添加现有接口的新实现，不修改核心工作流逻辑。**

### 多平台设计说明

当前 MVP 在命名和场景方面以 K8s 为中心。核心管道（验证 → 评分 → 比较）是**平台无关的** — 它只匹配 `evidenceType` 字符串，不依赖部署平台。但是，领域模型中包含 K8s 特定的假设，在实现真正的多平台支持之前需要解决：

**需要修改的模型（计划在未来步骤中完成）：**

| 当前字段 | 问题 | 计划变更 |
|---|---|---|
| `IncidentTask.namespace` | 仅 K8s 概念 | → `scope`（namespace / AZ / VPC / region） |
| `IncidentTask`（缺少 `platform`） | 无法区分 K8s vs EC2 vs 托管服务 | → 添加 `platform` 字段（`"kubernetes"` / `"ec2"` / `"managed_service"`） |
| `Evidence.service` | 对 RDS、ElastiCache、ALB 等语义不正确 | → `entity`（通用资源标识符） |
| `Evidence`（缺少 `entityType`） | 无法分类证据所属的资源类型 | → 添加 `entityType`（`"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`） |

**已实现的 K8s 诊断模式（Step J）：**

| 模式 | baseScore | 支持证据类型 | 反对证据类型 | 根因类型 |
|---|---|---|---|---|
| `pod_crash_loop` | 0.25 | `container_crash_loop_backoff` (0.30)、`pod_restart_count_increased` (0.20)、`pod_not_ready` (0.15)、`deployment_metadata` (0.05) | `no_restart_observed` (0.30)、`pod_ready` (0.20)、`container_running_normal` (0.20) | `container_crash_loop` |

场景 F（`demo` 命名空间中 `recommend-service` 的 CrashLoopBackOff）验证了此模式，产生 `likely_root_cause` 决策，分数 0.95（差距 ≥ 0.15）。

**非 K8s 场景的新诊断模式：**

| 模式 | 证据类型 |
|---|---|
| `ec2_instance_degradation` | CPU steal、EBS IOPS 饱和、状态检查异常 |
| `rds_connection_exhaustion` | 达到 max_connections、连接超时、副本延迟 |
| `elasticache_memory_pressure` | 驱逐激增、swap 使用、复制延迟 |
| `managed_service_failover` | 多可用区故障切换事件、DNS 变更、端点迁移 |

**为什么推迟是安全的：** 评分和比较引擎从不直接检查 `service` 或 `namespace` 字段 — 它们只通过 `evidenceType` 字符串匹配进行操作。添加多平台支持需要模型变更和新的证据提供者，但**无需对核心管道做任何改动**。
