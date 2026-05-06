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
│  │  ┌── LLM Proposal Layer ──────────────────────┐   │    │
│  │  │  LlmHypothesisProposerImpl (real LLM)       │   │    │
│  │  │  → MockLlmHypothesisProposer (fallback)     │   │    │
│  │  │  (advisory-only, never mutates decision)    │   │    │
│  │  └─────────────────────────────────────────────┘   │    │
│  │       ↓ (optional, advisory only)                 │    │
│  │  ┌── LLM Synthesis Layer ──────────────────────┐  │    │
│  │  │  LlmPromptBuilder → LlmClient → LlmReport   │  │    │
│  │  │  Synthesizer → LlmEnhancedReport             │  │    │
│  │  │  OpenAiCompatibleLlmClient (real LLM)        │  │    │
│  │  │  MockLlmClient (fallback)                    │  │    │
│  │  │  (cannot change decision/scores/evidence)    │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

---

## Maven Module Architecture

```
sre-production-agent (parent POM)
├── sre-agent-core                  ← Pure Java, zero Spring
├── sre-agent-llm                   ← Depends on core, zero Spring (LLM synthesis + LLM Hypothesis Proposer + OpenAI-compatible client)
├── sre-agent-k8s-provider          ← Depends on core, zero Spring, zero K8s client lib (fixture-based K8s evidence)
├── sre-agent-prometheus-provider   ← Depends on core, zero Spring (Prometheus metric evidence)
├── sre-agent-loki-provider         ← Depends on core, zero Spring (Loki log evidence)
├── sre-agent-alertmanager-provider ← Depends on core, zero Spring (Alertmanager alert evidence)
├── sre-agent-trace-provider        ← Depends on core, zero Spring (Distributed trace evidence)
├── sre-agent-probe-executor        ← Depends on core + llm, zero Spring (Probe execution framework)
├── sre-agent-cli                   ← Depends on core + llm + k8s-provider + prometheus-provider + loki-provider + alertmanager-provider + trace-provider + probe-executor, uses Picocli
├── sre-agent-server                ← Depends on core + llm + k8s-provider + prometheus-provider + probe-executor, uses Spring Boot (Phase 4: Live Scenario + real LLM integration)
└── demo-services                   ← Standalone Spring Boot microservices (order-service, payment-service, inventory-service) for end-to-end RCA validation + synthetic traffic generation (Phase 4)
```

### Why Eleven Modules?

| Module | Responsibility | Key Dependency |
|---|---|---|
| `sre-agent-core` | Domain model, RCA workflow, scoring, reporting, evidence taxonomy (Step Q) | Jackson only |
| `sre-agent-llm` | LLM-assisted synthesis (advisory-only narrative) + LLM Hypothesis Proposer (advisory-only proposals) + `OpenAiCompatibleLlmClient` (real LLM via OpenAI-compatible APIs) | core + Jackson |
| `sre-agent-k8s-provider` | K8s fixture evidence provider | core + Jackson |
| `sre-agent-prometheus-provider` | Prometheus metric evidence provider (fixture + HTTP) | core + Jackson |
| `sre-agent-loki-provider` | Loki log evidence provider (fixture + HTTP) | core + Jackson |
| `sre-agent-alertmanager-provider` | Alertmanager alert evidence provider (fixture + HTTP) — alert lifecycle, incident mapping, severity evidence | core + Jackson |
| `sre-agent-trace-provider` | Distributed trace evidence provider (fixture + HTTP) — span latency, error spans, service dependency graph | core + Jackson |
| `sre-agent-probe-executor` | Probe execution framework — routes LLM-generated ProbeIntents to evidence providers, collects informational Evidence | core + llm + Jackson |
| `sre-agent-cli` | Command-line interface | Picocli + core + llm + k8s-provider + prometheus-provider + loki-provider + alertmanager-provider + trace-provider + probe-executor |
| `sre-agent-server` | REST API + Web UI + LLM endpoints + Live Scenario orchestration (Phase 4) | Spring Boot + core + llm + k8s-provider + prometheus-provider + probe-executor |
| `demo-services` | Instrumented Spring Boot microservices for end-to-end RCA validation (Step U) + synthetic traffic generation (Phase 4) | Spring Boot + Micrometer |

### Why Core Has Zero Spring Dependency

1. **Testability** — core classes can be unit tested without Spring context startup (seconds vs milliseconds)
2. **Reusability** — the same RCA engine can run in CLI, server, Lambda, or any future adapter
3. **Separation of concerns** — domain logic should not depend on a web framework
4. **Interview signal** — demonstrates understanding of clean architecture boundaries

### Dependency Flow

```
core ← llm
core ← k8s-provider
core ← prometheus-provider
core ← loki-provider
core ← trace-provider
core ← probe-executor
llm ← probe-executor
llm ← cli
llm ← server
k8s-provider ← cli
k8s-provider ← server
prometheus-provider ← cli
prometheus-provider ← server
trace-provider ← cli
probe-executor ← cli
probe-executor ← server
core ← cli (also via llm + k8s-provider + prometheus-provider + trace-provider + probe-executor)
cli  ↗   ↖ server  (no dependency between adapters)
```

### Demo Services Topology (`demo-services`)

Step U introduced `demo-services` — a standalone Maven module containing three instrumented Spring Boot microservices that provide a realistic target topology for end-to-end RCA validation.

```
┌──────────────────────────────────────────────────────────┐
│                   Demo Service Mesh                       │
│                                                          │
│  Traffic Generator ──→ order-service ──→ payment-service  │
│                                   └──→ inventory-service │
│         (all services expose /actuator/prometheus)        │
└──────────────┬───────────────────────────────────────────┘
               ↓ (Prometheus scrapes all services)
┌──────────────────────────────────────────────────────────┐
│              Observability Stack (kind cluster)           │
│  Prometheus → Grafana → Alertmanager → SRE Agent          │
└──────────────────────────────────────────────────────────┘
```

**Key design decisions:**
- `demo-services` has **no dependency** on any `sre-agent-*` module — it is purely validation infrastructure
- Fault injection is runtime-controlled via REST API (`POST /api/demo-services/fault/*`), no code changes needed
- All services expose Micrometer metrics at `/actuator/prometheus` for real-time evidence collection
- Service topology is explicit: `order-service` calls both `payment-service` and `inventory-service`

### LLM Module (`sre-agent-llm`)

Step G introduced `sre-agent-llm` — a pure Java module (zero Spring dependency) that adds **advisory-only** LLM-assisted synthesis on top of the deterministic RCA pipeline. Step R extended this module with the LLM Hypothesis Proposer (`ai.sreagent.llm.proposer`).

**Key architectural invariant: the LLM layer cannot change the decision, confidence scores, or evidence.** It only adds narrative context (executive summary, reasoning, uncertainty explanation) and advisory hypothesis proposals to help on-call engineers interpret the deterministic result.

#### Module Components

| Component | Responsibility |
|---|---|
| `LlmClient` | Interface for LLM completion. Single method: `complete(LlmRequest) → LlmResponse`. Implementations are pluggable. |
| `MockLlmClient` | Deterministic mock implementation. Returns predictable RCA-assisted text without network access. Used as default when no real LLM is configured. |
| `LlmPromptBuilder` | Constructs system + user prompts from `InvestigationResult`. Embeds strict guardrails (system prompt forbids overriding decision/scores/inventing evidence). |
| `LlmReportSynthesizer` | Orchestrates: build prompt → call `LlmClient` → parse markdown sections → build `LlmEnhancedReport`. Deterministic fields always come from `InvestigationResult`, never from LLM output. |
| `LlmEnhancedReport` | Output record: base decision fields (deterministic) + LLM narrative fields (advisory). `advisoryOnly` flag is always `true`. |
| `LlmRequest` / `LlmResponse` | Value objects for the LLM client interface. |
| `LlmHypothesisProposer` | Interface (SPI) for LLM hypothesis proposal. Step R. |
| `MockLlmHypothesisProposer` | Deterministic mock for proposal. Step R. |
| `LlmHypothesisProposalPromptBuilder` | Constructs evidence-aware proposal prompts. Step R. |
| `LlmProposalTriggerPolicy` | Trigger policy: proposes only when inconclusive. Step R. |
| `ProposalGuardrail` | Enforces advisory-only constraints on proposals. Step R. |

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

### Prometheus Provider Module (`sre-agent-prometheus-provider`)

Step M introduced `sre-agent-prometheus-provider` — a pure Java module (zero Spring dependency) that collects metric evidence from Prometheus and maps it to the generic `Evidence` objects consumed by the core RCA pipeline.

**Key architectural invariant: `sre-agent-core` has zero Prometheus dependency.** The Prometheus provider is an adapter that translates Prometheus query results into the domain model's `Evidence` record. The core pipeline is unaware that the evidence came from Prometheus.

#### Module Components

| Component | Responsibility |
|---|---|
| `PrometheusQueryClient` | Interface (SPI) for executing PromQL queries. Two implementations: `FixturePrometheusQueryClient` (deterministic, for tests/CI) and `HttpPrometheusQueryClient` (production HTTP client). |
| `PrometheusResponseParser` | Parses Prometheus HTTP API JSON responses. Handles instant/range vectors, NaN/+Inf values, empty results, and missing fields. |
| `PrometheusQueryTemplateRegistry` | Maps semantic query types (e.g., `LATENCY_P95`, `ERROR_RATE`) to PromQL templates with `{{service}}`/`{{namespace}}` placeholders. |
| `PrometheusEvidenceMapper` | Maps parsed Prometheus results to semantic `Evidence` types using threshold-based classification (e.g., `metric_latency_p95_spike`, `metric_error_rate_spike`, `metric_no_signal`). |
| `PrometheusEvidenceProvider` | Orchestrator: resolve template → execute query → parse response → map to evidence. |

#### Evidence Types Produced

| Evidence Type | Condition |
|---|---|
| `metric_error_rate_spike` | Error rate exceeds threshold |
| `metric_latency_p95_spike` | P95 latency exceeds threshold |
| `metric_latency_p99_spike` | P99 latency exceeds threshold |
| `metric_downstream_latency_spike` | Downstream dependency latency exceeds threshold |
| `metric_memory_usage_high` | Memory usage exceeds threshold |
| `metric_cpu_usage_high` | CPU usage exceeds threshold |
| `metric_restart_rate_increased` | Pod restart rate exceeds threshold |
| `metric_request_rate_drop` | Request rate drops below threshold |
| `metric_no_signal` | Prometheus returns no data for the query |

All evidence carries `source = "prometheus"`, the actual metric value, the threshold applied, and relevant Prometheus labels.

#### Client Modes

| Mode | Implementation | Use Case |
|---|---|---|
| Fixture | `FixturePrometheusQueryClient` | Unit tests, CI, deterministic demos |
| HTTP | `HttpPrometheusQueryClient` | Production Prometheus instances |

Fixture mode requires no running Prometheus. HTTP mode requires `--prometheus-url`.

### Evidence Taxonomy (Step Q)

Step Q added provider-agnostic taxonomy classes to `sre-agent-core` — **not** a new module. The module count at that point remained 9 (now 10 after Step S). These classes normalize evidence across all providers:

| Class | Purpose |
|---|---|
| `EvidenceCategory` | metric, log, trace, alert, k8s_resource, deploy, topology |
| `EvidenceSignal` | spike, drop, saturation, anomaly, recovery, no_signal |
| `EvidenceSourceKind` | prometheus, loki, jaeger, alertmanager, kubernetes, git, cmdb, manual |
| `EvidenceSeverity` | critical, high, medium, low, info |
| `CausalRole` | supporting, counter, contextual, trigger, consequence |
| `EvidenceTaxonomy` | Composite record combining all classifications |
| `EvidenceNormalizer` | Maps raw `Evidence` → `EvidenceTaxonomy` |

While evidence types from each provider (K8s, Prometheus, Loki, Alertmanager, Trace) are provider-specific strings, Step Q adds a **provider-agnostic normalization layer** on top, enabling cross-provider evidence correlation. This taxonomy is the foundation for Step R (LLM Hypothesis Proposer), which consumes normalized evidence to propose hypotheses.

### LLM Hypothesis Proposer (Step R)

Step R added `ai.sreagent.llm.proposer` to `sre-agent-llm` — an LLM-based hypothesis proposal system that complements the deterministic pattern-matching engine when investigations are inconclusive.

**Key architectural invariant: LLM proposals are purely advisory.** They never change `InvestigationDecision`, `ConfidenceResult`, `VerificationResult`, and never create `Evidence`. All proposals carry `ProposalStatus.UNVERIFIED_PROPOSAL` with `canAffectDecision=false`.

#### Module Components

| Component | Responsibility |
|---|---|
| `LlmHypothesisProposer` | Interface (SPI) for LLM hypothesis proposal. Implementations are pluggable. |
| `MockLlmHypothesisProposer` | Deterministic mock implementation. Returns predictable proposals without network access. Used as default for tests/CI. |
| `LlmHypothesisProposalPromptBuilder` | Constructs evidence-aware prompts from alert + evidence, embedding strict guardrails. |
| `LlmProposalTriggerPolicy` | Determines whether to propose: only triggers when investigation is inconclusive (`competing_hypotheses`, `uncertain`, low confidence, small score gap). |
| `ProposalGuardrail` | Enforces advisory-only constraints: all proposals are `UNVERIFIED_PROPOSAL`, `canAffectDecision=false`. |

#### New Domain Models

| Model | Purpose |
|---|---|
| `ProposalStatus` | Enum: `UNVERIFIED_PROPOSAL` — all LLM proposals start unverified |
| `ProbeType` | Enum classifying probe categories (metric, log, trace, k8s, custom) |
| `ProbeIntent` | Describes what a probe aims to verify or refute |
| `VerificationPlan` | Ordered list of probes to validate/refute a hypothesis |
| `UnverifiedHypothesisProposal` | A single LLM-proposed hypothesis with rationale and verification plan |
| `LlmHypothesisProposalResult` | Aggregate result: list of proposals + trigger reason + guardrail metadata |

#### Trigger Policy Behavior

| Scenario | Decision | Triggered? | Proposals |
|---|---|---|---|
| Scenario E | `competing_hypotheses` (score gap 0.06) | ✅ Yes | 1 proposal: `deployment_timeout_amplification` |
| Scenario F | `likely_root_cause` (score 0.95, gap 0.95) | ❌ No | None — high confidence, no proposals needed |

#### Guardrails

1. **Status guardrail** — all proposals are `UNVERIFIED_PROPOSAL`; they cannot be promoted to verified without human review
2. **Decision guardrail** — `canAffectDecision=false` ensures proposals cannot influence the deterministic investigation decision
3. **Evidence guardrail** — proposals never create `Evidence` objects; they are purely informational
4. **Scope guardrail** — LLM cannot modify `InvestigationDecision`, `ConfidenceResult`, or `VerificationResult`

#### CLI Integration

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  propose-hypotheses \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/proposals.json
```

---

### Probe Executor Module (`sre-agent-probe-executor`)

Step S introduced `sre-agent-probe-executor` — a pure Java module (zero Spring dependency) that routes LLM-generated `ProbeIntent` objects to existing evidence providers and collects new informational `Evidence`.

**Key architectural invariant: probe execution does NOT bypass Verification or mutate RCA decisions.** Collected probe evidence is informational only. `canAffectDecision` is always `false`, enforced at compile time via `ProbeExecutionPolicy`.

#### Module Components

| Component | Responsibility |
|---|---|
| `ProbeIntentRouter` | Routes `ProbeType` to the appropriate evidence provider. Supports Prometheus, Loki, Trace, Kubernetes, and Alertmanager. |
| `ProbeExecutionPolicy` | Enforces `canAffectDecision=false` and rejects LIVE mode. Only FIXTURE mode is supported in Step S. |
| `FixtureProbeExecutor` | Generates fixture `Evidence` per probe type by delegating to existing fixture clients from provider modules. |
| `PrometheusProbeMapper` | Maps metric probe intents to `FixturePrometheusQueryClient` calls. |
| `LokiProbeMapper` | Maps log probe intents to `FixtureLokiQueryClient` calls. |
| `TraceProbeMapper` | Maps trace probe intents to `FixtureTraceQueryClient` calls. |
| `KubernetesProbeMapper` | Maps K8s probe intents to `K8sFixtureLoader` calls. |
| `AlertmanagerProbeMapper` | Maps alert probe intents to `FixtureAlertmanagerQueryClient` calls. |

#### Guardrails

1. **Decision guardrail** — `canAffectDecision=false` is enforced by `ProbeExecutionPolicy`; probe evidence cannot influence the deterministic investigation decision
2. **Mode guardrail** — only FIXTURE mode is supported in Step S; LIVE mode is explicitly rejected
3. **Verification guardrail** — probe evidence does NOT bypass the Verification pipeline; it goes through the same classification
4. **Immutability guardrail** — probe execution does NOT mutate the existing RCA decision or `InvestigationResult`

#### CLI Integration

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  propose-and-execute-probes \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/probe-results.json
```

#### REST Integration

```
POST /api/investigations/scenario-e/propose-and-execute-probes
  → InvestigationService.proposeAndExecuteProbes()
    → LlmHypothesisProposer.propose() (Step R)
    → ProbeIntentRouter.route()
    → FixtureProbeExecutor.execute()
    → List<Evidence> (informational only)
```

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
| `ProposalStatus` | Enum: `UNVERIFIED_PROPOSAL` — all LLM proposals start unverified |
| `ProbeType` | Enum classifying probe categories (metric, log, trace, k8s, custom) |
| `ProbeIntent` | Describes what a probe aims to verify or refute |
| `VerificationPlan` | Ordered list of probes to validate/refute a hypothesis |
| `UnverifiedHypothesisProposal` | A single LLM-proposed hypothesis with rationale and verification plan |
| `LlmHypothesisProposalResult` | Aggregate result: list of proposals + trigger reason + guardrail metadata |

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

### Observability Status Service (Step T)

Step T adds an observability status service that provides real-time health visibility into the local observability stack (Prometheus, Loki, Tempo, Alertmanager, Grafana).

**Architecture:**

```
ObservabilityStatusController (REST)
  ↓ GET /api/observability/status
  ↓ POST /api/observability/check
  ↓
ObservabilityStatusService (Spring @Service)
  ↓
EndpointHealthChecker (interface)
  └── HttpEndpointHealthChecker (HTTP connectivity check)
  ↓
ObservabilityStatusResponse DTO
  └── List<EndpointStatus> — per-endpoint name, url, status, latency, last checked
```

**Components:**

| Component | Responsibility |
|---|---|
| `EndpointHealthChecker` | Interface for health-checking observability endpoints. Single method: `check(url) → EndpointStatus`. Mockable in tests. |
| `HttpEndpointHealthChecker` | Default implementation. Makes HTTP GET requests to check endpoint connectivity and measures response latency. |
| `ObservabilityStatusService` | Spring `@Service` that orchestrates health checks across all configured endpoints. Reads endpoint list from Spring configuration. |
| `ObservabilityStatusController` | REST controller exposing status and on-demand check endpoints. |
| DTOs | `ObservabilityStatusResponse`, `EndpointStatus` — immutable records for API responses. |

**REST API:**

| Endpoint | Method | Description |
|---|---|---|
| `/api/observability/status` | GET | Returns cached status of all configured observability endpoints |
| `/api/observability/check` | POST | Triggers a fresh health check of all endpoints and returns updated status |

**Configuration:**

Endpoint URLs are configured via `application.properties`:

```properties
observability.endpoints.prometheus.url=http://localhost:9090
observability.endpoints.loki.url=http://localhost:3100
observability.endpoints.tempo.url=http://localhost:3200
observability.endpoints.alertmanager.url=http://localhost:9093
observability.endpoints.grafana.url=http://localhost:3000
```

**Key design decisions:**

1. **Interface-based health checking** — `EndpointHealthChecker` is an interface, enabling easy mocking in tests without requiring live endpoints.
2. **Configuration-driven endpoints** — Endpoints are defined in `application.properties`, not hardcoded. Different environments can configure different endpoint lists.
3. **Separation from RCA pipeline** — The observability status service is completely independent from the investigation workflow. It does not feed evidence into the RCA pipeline.
4. **No live endpoint required for tests** — All 23 tests use mocked health checkers, so `mvn test` passes without any observability stack running.

**Local Stack Management:**

Step T also adds `scripts/observability/` with shell scripts for managing the local observability stack via Helm, and corresponding Makefile targets:

| Makefile Target | Script | Purpose |
|---|---|---|
| `observability-install` | `install.sh` | Install observability stack via Helm |
| `observability-uninstall` | `uninstall.sh` | Uninstall observability stack |
| `observability-status` | `status.sh` | Check Helm release status |
| `observability-port-forward` | `port-forward.sh` | Port-forward all endpoints to localhost |
| `observability-check` | `check.sh` | Health-check all endpoints |

**UI — Live Lab Status:**

A "Lab Status" button in the `index.html` header toggles a Live Lab Status page showing real-time health of all observability endpoints with status indicators and latency measurements.

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
| ~~Prometheus metrics~~ | ✅ `PrometheusEvidenceProvider` — implemented in Step M | `sre-agent-prometheus-provider/` |
| Real Loki logs | `LokiEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` or new module |
| K8s events (fixtures) | `K8sFixtureEvidenceProvider` ✅ | `sre-agent-k8s-provider/` |
| Real K8s API | `KubernetesEvidenceProvider implements EvidenceProvider` ✅ | `sre-agent-k8s-provider/` |
| Alertmanager alerts | ✅ `AlertmanagerEvidenceProvider` — implemented in Step O | `sre-agent-alertmanager-provider/` |
| Distributed traces | ✅ `TraceEvidenceProvider` — implemented in Step P | `sre-agent-trace-provider/` |
| Probe execution | ✅ `FixtureProbeExecutor` + `ProbeIntentRouter` — implemented in Step S | `sre-agent-probe-executor/` |
| EC2 instance metrics | `Ec2EvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| AWS managed services (RDS, ElastiCache, ALB) | `AwsManagedServiceEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| CMDB / service topology | `CmdbTopologyProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |

### Other Extensions

| Extension | What to Add | Where |
|---|---|---|
| ~~OpenAI-compatible LLM provider~~ | ✅ `OpenAiCompatibleLlmClient implements LlmClient` — implemented in Phase 4 | `sre-agent-llm/client/` |
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
│  │  ┌── LLM Proposal Layer ──────────────────────┐   │    │
│  │  │  LlmHypothesisProposerImpl（真实 LLM）      │   │    │
│  │  │  → MockLlmHypothesisProposer（降级回退）     │   │    │
│  │  │  (仅限咨询性，不可修改决策)                  │   │    │
│  │  └─────────────────────────────────────────────┘   │    │
│  │       ↓ (optional, advisory only)                 │    │
│  │  ┌── LLM Synthesis Layer ──────────────────────┐  │    │
│  │  │  LlmPromptBuilder → LlmClient → LlmReport   │  │    │
│  │  │  Synthesizer → LlmEnhancedReport             │  │    │
│  │  │  OpenAiCompatibleLlmClient（真实 LLM）       │  │    │
│  │  │  MockLlmClient（降级回退）                   │  │    │
│  │  │  (不可修改决策/分数/证据)                    │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

---

## Maven 模块架构

```
sre-production-agent (parent POM)
├── sre-agent-core                  ← 纯 Java，零 Spring 依赖
├── sre-agent-llm                   ← 依赖 core，零 Spring 依赖（LLM 综合分析 + LLM 假设提议器 + OpenAI 兼容客户端）
├── sre-agent-k8s-provider          ← 依赖 core，零 Spring 依赖，零 K8s 客户端库（基于 fixture 的 K8s 证据）
├── sre-agent-prometheus-provider   ← 依赖 core，零 Spring 依赖（Prometheus 指标证据）
├── sre-agent-loki-provider         ← 依赖 core，零 Spring 依赖（Loki 日志证据）
├── sre-agent-alertmanager-provider ← 依赖 core，零 Spring 依赖（Alertmanager 告警证据）
├── sre-agent-trace-provider        ← 依赖 core，零 Spring 依赖（分布式追踪证据）
├── sre-agent-probe-executor        ← 依赖 core + llm，零 Spring 依赖（探测执行框架）
├── sre-agent-cli                   ← 依赖 core + llm + k8s-provider + prometheus-provider + loki-provider + alertmanager-provider + trace-provider + probe-executor，使用 Picocli
├── sre-agent-server                ← 依赖 core + llm + k8s-provider + prometheus-provider + probe-executor，使用 Spring Boot（Phase 4: 实时场景 + 真实 LLM 集成）
└── demo-services                   ← 独立的 Spring Boot 微服务（order-service、payment-service、inventory-service），用于端到端 RCA 验证 + 合成流量生成（Phase 4）
```

### 为什么是十一个模块？

| 模块 | 职责 | 关键依赖 |
|---|---|---|
| `sre-agent-core` | 领域模型、RCA 工作流、评分、报告、证据分类体系（Step Q） | 仅 Jackson |
| `sre-agent-llm` | LLM 辅助综合分析（仅限咨询性叙述）+ LLM 假设提议器（仅限咨询性提议）+ `OpenAiCompatibleLlmClient`（真实 LLM，兼容 OpenAI API） | core + Jackson |
| `sre-agent-k8s-provider` | K8s fixture 证据提供者 | core + Jackson |
| `sre-agent-prometheus-provider` | Prometheus 指标证据提供者（fixture + HTTP） | core + Jackson |
| `sre-agent-loki-provider` | Loki 日志证据提供者（fixture + HTTP） | core + Jackson |
| `sre-agent-alertmanager-provider` | Alertmanager 告警证据提供者（fixture + HTTP）— 告警生命周期、事件映射、严重性证据 | core + Jackson |
| `sre-agent-trace-provider` | 分布式追踪证据提供者（fixture + HTTP）— span 延迟、错误 span、服务依赖图 | core + Jackson |
| `sre-agent-probe-executor` | 探测执行框架 — 将 LLM 生成的 ProbeIntent 路由到证据提供者，收集信息性证据 | core + llm + Jackson |
| `sre-agent-cli` | 命令行界面 | Picocli + core + llm + k8s-provider + prometheus-provider + loki-provider + alertmanager-provider + trace-provider + probe-executor |
| `sre-agent-server` | REST API + Web UI + LLM 端点 + 实时场景编排（Phase 4） | Spring Boot + core + llm + k8s-provider + prometheus-provider + probe-executor |
| `demo-services` | 仪表化的 Spring Boot 微服务，用于端到端 RCA 验证（Step U）+ 合成流量生成（Phase 4） | Spring Boot + Micrometer |

### 为什么 Core 零 Spring 依赖

1. **可测试性** — core 类可以脱离 Spring 上下文进行单元测试（毫秒级 vs 秒级）
2. **可复用性** — 同一个 RCA 引擎可以运行在 CLI、Server、Lambda 或任何未来的适配器中
3. **关注点分离** — 领域逻辑不应依赖 Web 框架
4. **面试加分** — 展示了对整洁架构边界的理解

### 依赖流向

```
core ← llm
core ← k8s-provider
core ← prometheus-provider
core ← loki-provider
core ← alertmanager-provider
core ← trace-provider
core ← probe-executor
llm ← probe-executor
llm ← cli
llm ← server
k8s-provider ← cli
k8s-provider ← server
prometheus-provider ← cli
prometheus-provider ← server
alertmanager-provider ← cli
trace-provider ← cli
probe-executor ← cli
probe-executor ← server
core ← cli (also via llm + k8s-provider + prometheus-provider + alertmanager-provider + trace-provider + probe-executor)
cli  ↗   ↖ server  (适配器之间无依赖)
```

### 演示服务拓扑（`demo-services`）

Step U 引入了 `demo-services` — 一个独立的 Maven 模块，包含三个仪表化的 Spring Boot 微服务，为端到端 RCA 验证提供真实的目标拓扑。Phase 4 增加了合成流量生成能力。

```
┌──────────────────────────────────────────────────────────┐
│                   Demo Service Mesh                       │
│                                                          │
│  Traffic Generator ──→ order-service ──→ payment-service  │
│                                   └──→ inventory-service │
│         (所有服务暴露 /actuator/prometheus)                │
│         (Phase 4: /traffic 端点用于合成流量生成)            │
└──────────────┬───────────────────────────────────────────┘
               ↓ (Prometheus 抓取所有服务)
┌──────────────────────────────────────────────────────────┐
│              可观测性栈（kind 集群）                        │
│  Prometheus → Grafana → Alertmanager → SRE Agent          │
└──────────────────────────────────────────────────────────┘
```

**关键设计决策：**
- `demo-services` **不依赖**任何 `sre-agent-*` 模块 — 纯粹是验证基础设施
- 故障注入通过 REST API 运行时控制（`POST /api/demo-services/fault/*`），无需代码变更
- 所有服务通过 `/actuator/prometheus` 暴露 Micrometer 指标，支持实时证据收集
- 服务拓扑明确：`order-service` 同时调用 `payment-service` 和 `inventory-service`
- **Phase 4**：`POST /traffic` 端点支持合成流量生成，模拟真实请求负载进行端到端验证

### LLM 模块（`sre-agent-llm`）

Step G 引入了 `sre-agent-llm` — 一个纯 Java 模块（零 Spring 依赖），在确定性 RCA 管道之上增加了**仅限咨询性**的 LLM 辅助综合分析。Step R 扩展了该模块，增加了 LLM 假设提议器（`ai.sreagent.llm.proposer`）。Phase 4 增加了 `OpenAiCompatibleLlmClient`（真实 LLM 客户端，兼容 OpenAI API）和 `LlmHypothesisProposerImpl`（基于真实 LLM 的假设提议器）。

**关键架构不变量：LLM 层不能更改决策、置信度分数或证据。** 它只添加叙述性上下文（执行摘要、推理过程、不确定性说明）和咨询性假设提议来帮助值班工程师解读确定性结果。

#### 模块组件

| 组件 | 职责 |
|---|---|
| `LlmClient` | LLM 补全接口。单一方法：`complete(LlmRequest) → LlmResponse`。实现可插拔。 |
| `MockLlmClient` | 确定性模拟实现。返回可预测的 RCA 辅助文本，无需网络访问。未配置真实 LLM 时作为默认值使用。 |
| `OpenAiCompatibleLlmClient` | 真实 LLM 客户端实现（Phase 4）。通过 HTTP 调用任何 OpenAI 兼容 API（OpenAI、Azure OpenAI、Ollama、vLLM 等）。由 `LLM_PROVIDER` 环境变量激活。 |
| `LlmPromptBuilder` | 从 `InvestigationResult` 构建 system + user 提示词。嵌入严格防护措施（系统提示词禁止覆盖决策/分数/编造证据）。 |
| `LlmReportSynthesizer` | 编排流程：构建提示词 → 调用 `LlmClient` → 解析 markdown 段落 → 构建 `LlmEnhancedReport`。确定性字段始终来自 `InvestigationResult`，绝不来自 LLM 输出。 |
| `LlmEnhancedReport` | 输出记录：基础决策字段（确定性）+ LLM 叙述字段（咨询性）。`advisoryOnly` 标志始终为 `true`。 |
| `LlmRequest` / `LlmResponse` | LLM 客户端接口的值对象。 |
| `LlmHypothesisProposer` | LLM 假设提议接口（SPI）。Step R。 |
| `MockLlmHypothesisProposer` | 确定性模拟提议实现。Step R。 |
| `LlmHypothesisProposerImpl` | 真实 LLM 假设提议实现（Phase 4）。使用 `LlmClient` 进行实际的 LLM 调用。 |
| `LlmHypothesisProposalPromptBuilder` | 构建证据感知的提议提示词。Step R。 |
| `LlmProposalTriggerPolicy` | 触发策略：仅在不确定时提议。Step R。 |
| `ProposalGuardrail` | 强制执行仅咨询性约束。Step R。 |

#### Server 集成

Server 模块通过 `LlmSynthesisService`（Spring `@Service`）接入 LLM：
- 默认：使用 `MockLlmClient`（确定性，无网络，无需 API 密钥）
- Phase 4：`resolveClient()` 检查 `LLM_PROVIDER` 环境变量；配置为 `openai_compatible` 时使用 `OpenAiCompatibleLlmClient`；配置不完整时回退到 mock
- 暴露 REST 端点用于 LLM 增强综合分析

#### 防护措施（Guardrails）

LLM 层的设计确保**完全移除它不会改变任何调查结果**：

1. **提示词防护** — `LlmPromptBuilder` 系统提示词禁止 LLM 覆盖决策、更改分数、编造证据或隐藏反面证据
2. **结构防护** — `LlmReportSynthesizer` 始终从确定性 `InvestigationResult` 填充 `base*` 字段，绝不使用 LLM 输出
3. **输出防护** — `LlmEnhancedReport.advisoryOnly` 始终为 `true`；消费者必须检查此标志
4. **范围防护** — 提示词明确告知 LLM 不要推断 K8s、EC2、RDS、ElastiCache、ALB、CMDB 或拓扑事实

### LLM 假设提议器（Step R）

Step R 在 `sre-agent-llm` 中添加了 `ai.sreagent.llm.proposer` — 一个基于 LLM 的假设提议系统，在调查结果不确定时补充确定性模式匹配引擎。

**关键架构约束：LLM 提议纯粹是咨询性的。** 它们不会改变 `InvestigationDecision`、`ConfidenceResult`、`VerificationResult`，也不会创建 `Evidence`。所有提议都带有 `ProposalStatus.UNVERIFIED_PROPOSAL` 且 `canAffectDecision=false`。

#### 模块组件

| 组件 | 职责 |
|---|---|
| `LlmHypothesisProposer` | LLM 假设提议接口（SPI）。实现可插拔。 |
| `MockLlmHypothesisProposer` | 确定性模拟实现。返回可预测的提议，无需网络访问。测试/CI 中作为默认值使用。 |
| `LlmHypothesisProposalPromptBuilder` | 从告警 + 证据构建感知证据的提示词，嵌入严格防护措施。 |
| `LlmProposalTriggerPolicy` | 触发策略：仅在调查不确定时提议（`competing_hypotheses`、`uncertain`、低置信度、小分数差距）。 |
| `ProposalGuardrail` | 强制执行仅咨询性约束：所有提议为 `UNVERIFIED_PROPOSAL`，`canAffectDecision=false`。 |

#### 新领域模型

| 模型 | 用途 |
|---|---|
| `ProposalStatus` | 枚举：`UNVERIFIED_PROPOSAL` — 所有 LLM 提议初始为未验证 |
| `ProbeType` | 枚举，分类探测类别（指标、日志、追踪、K8s、自定义） |
| `ProbeIntent` | 描述探测旨在验证或反驳的内容 |
| `VerificationPlan` | 有序探测列表，用于验证/反驳假设 |
| `UnverifiedHypothesisProposal` | 单个 LLM 提议的假设，包含推理和验证计划 |
| `LlmHypothesisProposalResult` | 聚合结果：提议列表 + 触发原因 + 防护元数据 |

#### 触发策略行为

| 场景 | 决策 | 是否触发？ | 提议 |
|---|---|---|---|
| Scenario E | `competing_hypotheses`（分数差距 0.06） | ✅ 是 | 1 个提议：`deployment_timeout_amplification` |
| Scenario F | `likely_root_cause`（分数 0.95，差距 0.95） | ❌ 否 | 无 — 高置信度，无需提议 |

#### 防护措施

1. **状态防护** — 所有提议为 `UNVERIFIED_PROPOSAL`；未经人工审查不能提升为已验证
2. **决策防护** — `canAffectDecision=false` 确保提议不能影响确定性调查决策
3. **证据防护** — 提议从不创建 `Evidence` 对象；纯粹是信息性的
4. **范围防护** — LLM 不能修改 `InvestigationDecision`、`ConfidenceResult` 或 `VerificationResult`

#### CLI 集成

```bash
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  propose-hypotheses \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/proposals.json
```

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
| `ProposalStatus` | LLM 提议状态（UNVERIFIED_PROPOSAL / VERIFIED / DISCARDED） |
| `ProbeType` | 探测类型（LOG_QUERY / METRIC_QUERY / K8S_API / DEPLOYMENT_CHECK） |
| `ProbeIntent` | 探测意图：类型 + 目标 + 预期结果 |
| `VerificationPlan` | 验证计划：提议的探测列表 |
| `UnverifiedHypothesisProposal` | 未验证假设提议：ID + 描述 + 置信度 + 来源 + 可影响决策标记 + 验证计划 |
| `LlmHypothesisProposalResult` | LLM 假设提议结果：提议列表 + 触发原因 + 空原因 |

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

### 可观测性状态服务（Step T）

Step T 新增可观测性状态服务，提供对本地可观测性栈（Prometheus、Loki、Tempo、Alertmanager、Grafana）的实时健康可见性。

**架构：**

```
ObservabilityStatusController (REST)
  ↓ GET /api/observability/status
  ↓ POST /api/observability/check
  ↓
ObservabilityStatusService (Spring @Service)
  ↓
EndpointHealthChecker (接口)
  └── HttpEndpointHealthChecker (HTTP 连通性检查)
  ↓
ObservabilityStatusResponse DTO
  └── List<EndpointStatus> — 每个端点的名称、URL、状态、延迟、最后检查时间
```

**组件：**

| 组件 | 职责 |
|---|---|
| `EndpointHealthChecker` | 可观测性端点健康检查接口。单一方法：`check(url) → EndpointStatus`。测试中可 mock。 |
| `HttpEndpointHealthChecker` | 默认实现。通过 HTTP GET 请求检查端点连通性并测量响应延迟。 |
| `ObservabilityStatusService` | Spring `@Service`，协调所有已配置端点的健康检查。从 Spring 配置读取端点列表。 |
| `ObservabilityStatusController` | REST 控制器，暴露状态和按需检查端点。 |
| DTOs | `ObservabilityStatusResponse`、`EndpointStatus` — 用于 API 响应的不可变 record。 |

**REST API：**

| 端点 | 方法 | 描述 |
|---|---|---|
| `/api/observability/status` | GET | 返回所有已配置可观测性端点的缓存状态 |
| `/api/observability/check` | POST | 触发所有端点的全新健康检查并返回更新后的状态 |

**配置：**

端点 URL 通过 `application.properties` 配置：

```properties
observability.endpoints.prometheus.url=http://localhost:9090
observability.endpoints.loki.url=http://localhost:3100
observability.endpoints.tempo.url=http://localhost:3200
observability.endpoints.alertmanager.url=http://localhost:9093
observability.endpoints.grafana.url=http://localhost:3000
```

**关键设计决策：**

1. **基于接口的健康检查** — `EndpointHealthChecker` 是接口，测试中可轻松 mock，无需实时端点。
2. **配置驱动的端点** — 端点在 `application.properties` 中定义，非硬编码。不同环境可配置不同的端点列表。
3. **与 RCA 管道隔离** — 可观测性状态服务完全独立于调查工作流。它不向 RCA 管道提供证据。
4. **测试无需实时端点** — 所有 23 个测试使用 mock 健康检查器，`mvn test` 无需运行任何可观测性栈。

**本地栈管理：**

Step T 还新增 `scripts/observability/`，包含通过 Helm 管理本地可观测性栈的 shell 脚本，以及对应的 Makefile 目标：

| Makefile 目标 | 脚本 | 用途 |
|---|---|---|
| `observability-install` | `install.sh` | 通过 Helm 安装可观测性栈 |
| `observability-uninstall` | `uninstall.sh` | 卸载可观测性栈 |
| `observability-status` | `status.sh` | 检查 Helm release 状态 |
| `observability-port-forward` | `port-forward.sh` | 将所有端点端口转发到 localhost |
| `observability-check` | `check.sh` | 对所有端点执行健康检查 |

**UI — Live Lab Status：**

`index.html` 头部的 "Lab Status" 按钮可切换 Live Lab Status 页面，显示所有可观测性端点的实时健康状态，包含状态指示器和延迟测量。

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

|| 扩展 | 添加内容 | 位置 |
||---|---|---|
|| 真实 Prometheus 指标 | ✅ `PrometheusEvidenceProvider` — Step M 已实现 | `sre-agent-prometheus-provider/` |
|| 真实 Loki 日志 | ✅ `LokiEvidenceProvider` — Step N 已实现 | `sre-agent-loki-provider/` |
|| K8s 事件（fixtures） | ✅ `K8sFixtureEvidenceProvider` — Step H 已实现 | `sre-agent-k8s-provider/` |
|| 真实 K8s API | ✅ `KubernetesEvidenceProvider` — Step L 已实现 | `sre-agent-k8s-provider/` |
|| Alertmanager 告警 | ✅ `AlertmanagerEvidenceProvider` — Step O 已实现 | `sre-agent-alertmanager-provider/` |
|| 分布式追踪 | ✅ `TraceEvidenceProvider` — Step P 已实现 | `sre-agent-trace-provider/` |
|| EC2 实例指标 | `Ec2EvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| AWS 托管服务（RDS、ElastiCache、ALB） | `AwsManagedServiceEvidenceProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |
| CMDB / 服务拓扑 | `CmdbTopologyProvider implements EvidenceProvider` | `sre-agent-core/evidence/` |

### 其他扩展

| 扩展 | 添加内容 | 位置 |
|---|---|---|
| ~~OpenAI 兼容 LLM 提供者~~ | ✅ `OpenAiCompatibleLlmClient implements LlmClient` — Phase 4 已实现 | `sre-agent-llm/client/` |
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
