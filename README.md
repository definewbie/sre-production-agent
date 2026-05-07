# SRE Production Agent

> **A verification-first AI SRE RCA Agent that turns incident investigation into an auditable Evidence → Hypothesis → Verification → Confidence → Decision workflow.**

---

## What Problem Does This Solve?

When a Kubernetes microservice triggers an alert, on-call engineers face two bad options:

1. **Manual RCA** — grep logs, check dashboards, page through Git history. Slow, inconsistent, and hard to hand off.
2. **Log chatbot** — paste logs into an LLM and ask "what went wrong?" Fast, but ungrounded, unverified, and impossible to audit.

This project proposes a third path: **a structured, verification-first investigation workflow** that generates multiple hypotheses, verifies each against evidence, scores them with explainable confidence, and preserves uncertainty instead of forcing a fake root cause.

**This is not a log chatbot.** It is a deterministic RCA agent that treats every claim as a hypothesis to be verified.

---

## Core Workflow

```
Alert
  ↓
Evidence Collection (8 items from deploy logs, metrics, git diff, service topology)
  ↓
Diagnostic Pattern Matching (4 built-in patterns)
  ↓
Hypothesis Generation (1 hypothesis per pattern)
  ↓
Verification (supporting / counter / missing / contradiction classification)
  ↓
Confidence Scoring (deterministic, explainable formula)
  ↓
Hypothesis Comparison (leading vs competing)
  ↓
Investigation Decision (identified_root_cause | competing_hypotheses | escalation | insufficient_evidence)
  ↓
Markdown Report + Event Trace
  ↓
LLM Synthesis (optional) ── MockLlmClient / OpenAiCompatibleLlmClient → LlmPromptBuilder → LlmReportSynthesizer → LlmEnhancedReport
  ↓
LLM Proposal (optional) ── LlmHypothesisProposer → UnverifiedHypothesisProposal → ProbeIntent → Probe Execution
```

Every step is recorded in an Event Trace — a fully auditable investigation log.

---

## Demo: Scenario E — Competing Hypotheses

`order-service` error rate spikes after a deployment. But `payment-service` latency also increased. Which is the root cause?

```
deployment_regression         score = 0.64
downstream_dependency_latency score = 0.58
pod_oom_killed                score = 0.05
```

**Score gap = 0.06** — too close to call.

**Decision: `competing_hypotheses`** — the agent preserves both explanations and suggests next probes instead of forcing a single RCA.

This is the key differentiator: **the agent knows when it doesn't know.**

---

## Demo: Scenario F — CrashLoopBackOff Detection

`recommend-service` in the `demo` namespace enters CrashLoopBackOff. The agent collects K8s evidence (pod status, container restarts, exit codes) and identifies the root cause with high confidence.

```
pod_crash_loop                 score = 0.95
deployment_regression          score = 0.00
downstream_dependency_latency  score = 0.00
```

**Decision: `likely_root_cause`** — `pod_crash_loop` dominates with a clear margin.

This demonstrates the K8s evidence provider module (`sre-agent-k8s-provider`) feeding fixture-based K8s data into the same deterministic RCA workflow.

---

## Live K8s Demo (Step K)

The fixture-based Scenario F can also run with **live Kubernetes evidence** from a local `kind` cluster:

```bash
make build
make cluster-up
make live-k8s-demo
```

This deploys a CrashLoopBackOff workload into `kind`, collects real K8s evidence via kubectl, and runs the same RCA workflow. The result is identical: `pod_crash_loop → likely_root_cause`.

See [docs/live-k8s-demo.md](docs/live-k8s-demo.md) for the full walkthrough.

---

## Kubernetes Java Client Integration (Step L)

Step L adds a production-grade **Kubernetes Java Client** (`io.kubernetes:client-java:24.0.0`) as an alternative evidence reader for `sre-agent-k8s-provider`. This complements the existing kubectl-based live reader with a proper API client that supports:

- **Kubeconfig mode** — reads `~/.kube/config` for local development
- **In-cluster mode** — auto-detects service account tokens when running inside a pod

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/k8s_crashloop.json \
  --evidence examples/evidence/k8s_crashloop_evidence.json \
  --reader java-client \
  --client-mode kubeconfig \
  --output /tmp/rca-javaclient-report.md
```

**Key additions:**
- `JavaClientKubernetesResourceReader` — implements the `KubernetesResourceReader` SPI with official Java client
- `KubernetesClientConfig` + `KubernetesApiClientFactory` — configuration and client lifecycle
- `KubernetesEvidenceCollectionException` — typed error handling
- CLI flags: `--reader java-client`, `--client-mode`, `--kubeconfig`
- Updated RBAC manifest and `docs/k8s-rbac.md`
- Bug fix: `mapPodToSemanticEvidence()` now detects CrashLoop from terminated state (not just waiting)

Live `kind` validation confirms identical results: `hyp_pod_crash_loop = 0.95`, decision = `likely_root_cause`.

---

## Prometheus Metrics Evidence Provider (Step M)

Step M adds a dedicated `sre-agent-prometheus-provider` module that collects **metric evidence** from Prometheus and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This is the first observability signal provider beyond Kubernetes resource evidence.

**Key design:** `sre-agent-core` has zero Prometheus dependency. The provider is a pure adapter that outputs `Evidence` with `source = "prometheus"`.

```bash
# Collect Prometheus evidence using fixture (no live Prometheus needed)
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-prometheus-evidence \
  --service payment-service \
  --namespace demo \
  --query-type LATENCY_P95 \
  --output examples/evidence/prometheus_payment_latency.json \
  --reader fixture

# Collect Prometheus evidence from live instance
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-prometheus-evidence \
  --service payment-service \
  --namespace demo \
  --query-type LATENCY_P95 \
  --output examples/evidence/prometheus_payment_latency.json \
  --reader http \
  --prometheus-url http://localhost:9090
```

**Key additions:**
- `PrometheusQueryClient` interface → `FixturePrometheusQueryClient` / `HttpPrometheusQueryClient`
- `PrometheusResponseParser` — handles vector/range results, NaN/+Inf, empty results
- `PrometheusQueryTemplateRegistry` — 8 query types: ERROR_RATE, LATENCY_P95, LATENCY_P99, DOWNSTREAM_LATENCY_P95, MEMORY_USAGE, CPU_USAGE, RESTART_RATE, REQUEST_RATE
- `PrometheusEvidenceMapper` — maps to 9 semantic evidence types (e.g., `metric_latency_p95_spike`, `metric_error_rate_spike`, `metric_no_signal`)
- 43 new tests (229 total, up from 186)

See [docs/prometheus-evidence-provider.md](docs/prometheus-evidence-provider.md) for full documentation.

---

## Loki Logs Evidence Provider (Step N)

Step N adds a dedicated `sre-agent-loki-provider` module that collects **log evidence** from Grafana Loki and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This complements the Prometheus metric provider by adding log-line-level observability.

**Key design:** `sre-agent-core` has zero Loki dependency. The provider is a pure adapter that outputs `Evidence` with `source = "loki"`.

```bash
# Collect Loki evidence using fixture (no live Loki needed)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR \
  --output /tmp/loki_timeout_evidence.json \
  --reader fixture

# Collect multiple query types at once
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR,EXCEPTION_LOGS,OOM_LOGS \
  --output /tmp/loki_multi.json \
  --reader fixture

# Collect Loki evidence from live instance
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-loki-evidence \
  --service order-service \
  --namespace demo \
  --query-type TIMEOUT_ERROR \
  --output /tmp/loki_timeout_live.json \
  --reader http \
  --loki-url http://localhost:3100
```

**Key additions:**
- `LokiQueryClient` interface → `FixtureLokiQueryClient` / `HttpLokiQueryClient`
- `LokiResponseParser` — handles stream results, nanosecond timestamps, error/empty results
- `LokiQueryTemplateRegistry` — 9 LogQL query types: TIMEOUT_ERROR, DOWNSTREAM_TIMEOUT, DOWNSTREAM_ERROR, EXCEPTION_LOGS, CRASH_LOGS, OOM_LOGS, DB_CONNECTION_TIMEOUT, RETRY_EXHAUSTED, HTTP_5XX_LOGS
- `LokiEvidenceMapper` — maps to 9 semantic evidence types (e.g., `log_timeout_error`, `log_exception_spike`, `log_oom_message`, `log_no_signal`)
- 30 new tests (263 total, up from 229)

See [docs/loki-evidence-provider.md](docs/loki-evidence-provider.md) for full documentation.

---

## Alertmanager Alert Evidence Provider (Step O)

Step O adds a dedicated `sre-agent-alertmanager-provider` module that collects **alert evidence** from Alertmanager and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This complements the Prometheus and Loki providers by adding alert lifecycle and severity evidence.

**Key design:** `sre-agent-core` has zero Alertmanager dependency. The provider is a pure adapter that outputs `Evidence` with `source = "alertmanager"`.

```bash
# Collect Alertmanager evidence using fixture (no live Alertmanager needed)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-alertmanager-evidence \
  --service order-service \
  --namespace demo \
  --output /tmp/alertmanager_evidence.json \
  --reader fixture

# Collect Alertmanager evidence from live instance
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-alertmanager-evidence \
  --service order-service \
  --namespace demo \
  --output /tmp/alertmanager_live.json \
  --reader http \
  --alertmanager-url http://localhost:9093
```

**Key additions:**
- `AlertmanagerQueryClient` interface → `FixtureAlertmanagerQueryClient` / `HttpAlertmanagerQueryClient`
- `AlertmanagerResponseParser` — handles alert/route/silence results, status/state parsing, empty results
- `AlertmanagerEvidenceMapper` — maps to 7 semantic evidence types (alert lifecycle, incident mapping, severity evidence)
- `AlertmanagerEvidenceProvider` — dual output: incidents + evidence
- 45 new tests (308 total, up from 263)

---

## Distributed Trace Evidence Provider (Step P)

Step P adds a dedicated `sre-agent-trace-provider` module that collects **trace evidence** (span latency, error spans, service dependency graph) from Jaeger/Tempo and maps it to the generic `Evidence` objects consumed by the core RCA pipeline. This complements the Prometheus, Loki, and Alertmanager providers by adding distributed tracing observability.

**Key design:** `sre-agent-core` has zero trace dependency. The provider is a pure adapter that outputs `Evidence` with `source = "trace"`.

```bash
# Collect trace evidence using fixture (no live Jaeger/Tempo needed)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS \
  --output /tmp/trace_slow_spans.json \
  --reader fixture

# Collect multiple query types at once
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS,ERROR_SPANS,SERVICE_DEPENDENCY \
  --output /tmp/trace_multi.json \
  --reader fixture

# Collect trace evidence from live Jaeger/Tempo instance
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS \
  --output /tmp/trace_slow_live.json \
  --reader http \
  --trace-url http://localhost:16686
```

**Key additions:**
- `TraceQueryClient` interface → `FixtureTraceQueryClient` / `HttpTraceQueryClient`
- `TraceResponseParser` — handles trace/span results, duration parsing, error status, service dependency extraction
- `TraceQueryTemplateRegistry` — 6 query types: SLOW_SPANS, ERROR_SPANS, SERVICE_DEPENDENCY, SPAN_ERRORS_BY_SERVICE, TRACE_DURATION_HISTOGRAM, SERVICE_CALL_GRAPH
- `TraceEvidenceMapper` — maps to 8 semantic evidence types (e.g., `trace_span_latency_high`, `trace_error_span_detected`, `trace_service_dependency`, `trace_no_signal`)
- 35 new tests (343 total, up from 308)

See [docs/trace-evidence-provider.md](docs/trace-evidence-provider.md) for full documentation.

---

## Probe Execution Framework (Step S)

Step S adds a dedicated `sre-agent-probe-executor` module that routes LLM-generated `ProbeIntent` objects to existing evidence providers and collects new informational `Evidence`. This completes the LLM → Probe → Evidence feedback loop started in Step R.

**Key design:** Probe execution does NOT bypass Verification or mutate RCA decisions. `canAffectDecision` is always `false`, enforced at compile time. Only FIXTURE mode is supported in Step S.

```bash
# Generate hypothesis proposals and execute probes for Scenario E
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  propose-and-execute-probes \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/probe-results.json
```

**Key additions:**
- `sre-agent-probe-executor` module (10th Maven module, package `ai.sreagent.probe`)
- `ProbeIntentRouter` — routes ProbeType to supported providers
- `ProbeExecutionPolicy` — enforces `canAffectDecision=false`, rejects LIVE mode
- `FixtureProbeExecutor` — generates fixture Evidence per probe type
- 5 provider mappers (Prometheus, Loki, Trace, Kubernetes, Alertmanager)
- CLI command: `propose-and-execute-probes`
- REST endpoint: `POST /api/investigations/scenario-e/propose-and-execute-probes`
- UI: Probe Execution card in `index.html`
- 46 new tests (484 total, up from 435)

---

## Observability Status Service (Step T)

Step T adds an observability status service with health checking, local stack management scripts, and a Live Lab Status UI page. It provides real-time visibility into the local observability stack (Prometheus, Loki, Tempo, Alertmanager, Grafana) without deploying demo services or fault injection.

**Key design:** `EndpointHealthChecker` is an interface (mockable in tests), endpoint configuration is via `application.properties`, and no live endpoints are required for tests.

**Backend components:**
- `EndpointHealthChecker` interface → `HttpEndpointHealthChecker` (HTTP connectivity check)
- `ObservabilityStatusService` (Spring `@Service`)
- `ObservabilityStatusController` (REST)
- DTOs: `ObservabilityStatusResponse`, `EndpointStatus`

**REST API:**
- `GET /api/observability/status` — cached status of all endpoints
- `POST /api/observability/check` — trigger fresh health check

**Local stack management (scripts/observability/):**
- Makefile targets: `observability-install`, `observability-uninstall`, `observability-status`, `observability-port-forward`, `observability-check`

**UI:** "Lab Status" button in header toggles Live Lab Status page showing real-time endpoint health.

**23 new tests** in server module (507 total, up from 484, 0 failures)

---

## Instrumented Demo Services (Step U)

Step U adds **instrumented demo microservices** to the local kind lab, producing real metrics, logs, and traces with controllable fault injection.

**Service topology:**
```
client / traffic-generator
        ↓
order-service /checkout
        ├── payment-service /charge
        └── inventory-service /reserve
```

Each service is a Spring Boot app with:
- **Metrics**: Micrometer Prometheus at `/actuator/prometheus`
- **Logs**: Structured stdout for Promtail → Loki
- **Traces**: OpenTelemetry → Jaeger
- **Fault injection**: In-memory `FaultConfig` via `POST /fault-config`

Fault modes: `normal`, `latency`, `error`, `timeout`, `mixed`

```bash
make demo-build-images
make demo-load-images
make demo-services-install
make demo-services-port-forward
make demo-fault-payment-latency  # inject 1500ms latency
```

**Server API**: `GET /api/demo-services/status`, `POST /api/demo-services/fault/*`

**UI**: "Demo Services" page with topology visualization, service status cards, and fault injection controls.

**41 new tests** across demo services and server (548 total, up from 507, 0 failures)

---

## Complex Live RCA Scenario + 中文 Investigation Console (Step V)

Step V adds **multi-signal live RCA orchestration** with a Chinese-language investigation console UI. It wires all 5 evidence providers (Prometheus, Loki, Trace, K8s, Alertmanager) into a single Scenario G investigation with fault injection on demo services.

**Architecture:**
```
LiveScenarioController (REST)
  ↓
LiveScenarioService (orchestrator)
  ├── DemoServiceClient → fault injection
  ├── LiveEvidenceCollector → multi-signal collection
  │   ├── PrometheusEvidenceProvider
  │   ├── LokiEvidenceProvider
  │   ├── TraceEvidenceProvider
  │   ├── KubernetesEvidenceProvider
  │   └── AlertmanagerEvidenceProvider
  ├── InvestigationWorkflow.runFromMemory() → RCA pipeline
  └── LiveScenarioResult → response
```

**Scenario G flow:**
```
traffic-generator → order-service /checkout
  → payment-service /charge (fault: latency/error/timeout)
  → inventory-service /reserve
  ↓
Multi-signal evidence → RCA → Decision
```

**REST API:**
- `GET /api/live-scenario/simulate` — run Scenario G in simulation mode (fixture clients)
- `GET /api/live-scenario/simulate?runLlm=true` — simulation with real LLM proposal (requires `LLM_BASE_URL` + `LLM_API_KEY` env vars)
- `POST /api/live-scenario/run` — run Scenario G with live fault injection
- `POST /api/live-scenario/run` (body: `{ "runLlmProposal": true }`) — live run with real LLM proposal
- `GET /api/live-scenario/{id}` — get scenario status and results
- `GET /api/live-scenario/latest` — get latest scenario result
- `GET /api/live-scenario` — list all scenario results
- `POST /api/live-scenario/reset` — clear fault injection and results

**UI:** "🔍 实时排查" button opens Chinese-language investigation console with fault mode selection, run mode toggle, step progress indicator, and RCA result visualization.

**Makefile targets:** `live-scenario-simulate`, `live-scenario-run`, `live-scenario-latest`, `live-scenario-reset`

**Key design:** `InvestigationWorkflow.runFromMemory()` enables programmatic RCA without filesystem I/O. Simulation mode uses fixture clients (no live endpoints required for tests). UI is fully Chinese-localized.

**12 new tests** (560 total, up from 548, 0 failures)

---

## V.2-UI — React Investigation Console

V.2-UI is a complete rewrite of the investigation console from a single-page HTML file to a modular React + TypeScript + Vite application. It introduces a professional sidebar navigation, real API integration, and per-page focused functionality.

### V.2-UI-2: React Rewrite + Environment Status

Replaced `index.html` with `sre-agent-ui/` React app. Environment Status page connects to `/api/observability/status` with sidebar environment summary badges.

### V.2-UI-3: Service Health Overview

KPI cards (service count, unhealthy, alerts, affected users), service table with health metrics, interactive topology graph with D3 force layout, Chaos fault injection controls.

### V.2-UI-4: RCA Analysis Page (Real API)

Connected RCA analysis to real `LiveScenarioService` API. Semantic typing for Kubernetes evidence (PodStatus → DEGRADED/CRASHING/TERMINATED). Full hypothesis → verification → confidence → decision display.

### V.2-UI-5: Evidence Drilldown Page

Per-hypothesis evidence breakdown with raw data inspection. Shows supporting/counter/missing evidence classification with source attribution (Prometheus, Loki, Jaeger, K8s, Alertmanager).

### V.2-UI-6: Alert-Driven Incident Intake (Current)

Closes the loop from **Alertmanager alert → IncidentTask → RCA analysis** without manual intervention. The first production-like end-to-end path in the SRE Agent.

**Architecture:**
```
Alertmanager (firing alerts)
  ↓ GET /api/alertmanager/alerts?filter=...
IncidentController
  ↓ pollFiringAlerts() → List<AlertView>
  ↓ triggerRcaFromAlert(fingerprint)
IncidentService
  ↓ AlertmanagerIncidentMapper.toIncidentTask()
  ↓ LiveEvidenceCollector (5 providers)
  ↓ InvestigationWorkflow.runFromMemory()
IncidentRcaResultView → UI
```

**REST API (new in V.2-UI-6):**
- `GET /api/incidents/alerts` — poll firing alerts from Alertmanager
- `POST /api/incidents/from-alert` — trigger RCA from alert fingerprint
- `GET /api/incidents/{id}` — get incident record
- `GET /api/incidents/{id}/rca` — get incident RCA result
- `GET /api/incidents/{id}/report` — get incident markdown report

**Key design:**
- `AlertIncidentMapper` filters by severity (critical/warning) and excludes ignored types (`NONE`, `k8s_no_signal`, `k8s_runtime_healthy`, `restart_count_observed`)
- `IncidentRecord` is an in-memory aggregate (incidentTask + alert + rcaResult + evidenceReport)
- "触发 RCA 分析" button on each alert card → POST → auto-navigate to RCA analysis page

**Tests:** 34 backend tests (IncidentController, DTOs) + 5 Playwright E2E tests, all passing.

**39 new tests** (599 backend + 5 E2E frontend)

---

## Phase 4 — 中文化全链路 + 真实 LLM 接入 + E2E 验证

Phase 4 将整个 RCA 输出链路从英文转为中文，新增 LLM Proposal UI 卡片展示，并接入真实 LLM（OpenAI-compatible API）完成端到端验证。

### P1 — 中文化全链路

| 组件 | 改动 | 说明 |
|------|------|------|
| `HypothesisEngine` | 假设 title / candidateCause 中文 | deployment_regression → "近期部署引入了回归缺陷" 等 4 个模板 |
| `MarkdownReporter` | 全中文报告输出 | 标题："竞争假设分析报告"，段落：概要/调查时间线/假设评分/调查决策 |
| `index.html` | decision_type 中文映射 | `likely_root_cause` → "高置信根因" 等 5 种映射 |
| `index.html` | LLM Proposal 卡片 | 显示 AI 提案的 title/reasoning/signals/verificationPlan/confidence/status |
| `index.html` | Report 渲染增强 | markdown→HTML 转换（标题/加粗/列表/表格） |
| 6 个测试文件 | 断言同步更新 | 所有断言从英文改中文 |

### P2 — 真实 LLM 接入 + E2E 验证

| 组件 | 改动 | 说明 |
|------|------|------|
| `OpenAiCompatibleLlmClient` | 新增实现类 | 支持 OpenAI / DeepSeek / OpenRouter 等兼容 API（`LLM_BASE_URL` + `LLM_API_KEY` + `LLM_MODEL`） |
| `LiveScenarioController` | `?runLlm=true` 参数 | `GET /simulate?runLlm=true` 或 `POST /run { runLlmProposal: true }` 触发真实 LLM |
| `LiveScenarioService` | LLM proposal 集成 | 根据 `runLlm` 标志选择 `MockLlmHypothesisProposer` 或 `OpenAiCompatibleLlmClient` |
| Bug fixes | 3 个修复 | LiveScenarioResult JSON 序列化、中文 hypothesis 映射、API 字段对齐 |

**中文映射表：**

- 假设：`deployment_regression`→"近期部署引入了回归缺陷"、`downstream_dependency`→"下游依赖延迟导致服务降级"、`pod_oom`→"Pod OOMKilled 或资源超限"、`pod_crash_loop`→"容器崩溃循环导致服务不可用"
- 决策：`LIKELY_ROOT_CAUSE`→"高置信根因"、`PROBABLE_ROOT_CAUSE`→"可能根因"、`COMPETING_HYPOTHESES`→"竞争假设"、`UNCERTAIN`→"不确定-需更多证据"、`INSUFFICIENT_DATA`→"数据不足"

**1194 tests, 0 failures.**

---

## LLM Hypothesis Proposer (Step R)

Step R adds an **LLM Hypothesis Proposer** to the `sre-agent-llm` module that generates advisory hypothesis proposals when the deterministic RCA workflow produces inconclusive results. This bridges the gap between deterministic investigation and AI-assisted exploration.

**Key design:** LLM proposals are **advisory only** — they never change `InvestigationDecision`, `ConfidenceResult`, `VerificationResult`, or create `Evidence`. All proposals carry `UNVERIFIED_PROPOSAL` status and `canAffectDecision = false`.

```bash
# Generate hypothesis proposals for Scenario E (competing hypotheses)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  propose-hypotheses \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output examples/reports/hypothesis_proposals.json
```

**Trigger policy:**
- ✅ Proposes when: competing hypotheses, uncertain decision, low confidence (<0.60), small score gap (<0.10)
- ❌ Does not propose when: clear RCA with high confidence (>=0.80) and good margin (>=0.15)

**Key additions:**
- `LlmHypothesisProposer` interface + `MockLlmHypothesisProposer` (deterministic impl)
- `LlmHypothesisProposalPromptBuilder` — constructs LLM prompts from investigation context
- `LlmProposalTriggerPolicy` — decides when to trigger proposals
- `ProposalGuardrail` — validates all proposals are advisory-only
- Models: `ProposalStatus`, `ProbeType`, `ProbeIntent`, `VerificationPlan`, `UnverifiedHypothesisProposal`, `LlmHypothesisProposalResult`
- 28 new tests (435 total, up from 407)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  sre-agent-cli                    sre-agent-server                   │
│  (Picocli)                        (Spring Boot 3.x)                  │
│       │                                  │                            │
│       └──────────────┬───────────────────┘                            │
│                      ↓                                                │
│             InvestigationWorkflow                                     │
│             (shared orchestrator)                                     │
│                      ↓                                                │
│  ┌──────────────── sre-agent-core ────────────────────┐              │
│  │                                                     │              │
│  │  EvidenceLoader → PatternRegistry                   │              │
│  │       ↓                                             │              │
│  │  HypothesisEngine                                   │              │
│  │       ↓                                             │              │
│  │  VerificationEngine                                 │              │
│  │       ↓                                             │              │
│  │  ConfidenceScorer                                   │              │
│  │       ↓                                             │              │
│  │  HypothesisComparator → InvestigationDecision       │              │
│  │       ↓                                             │              │
│  │  MarkdownReporter + EventTraceStore                 │              │
│  │                                                     │              │
│  │  Zero Spring dependency                             │              │
│  └─────────────────────────────────────────────────────┘              │
│          ↑               ↑               ↓ (optional)                 │
│  ┌── k8s-provider ──┐  ┌── prometheus ──┐  ┌── llm ─────────────────────┐    │
│  │                   │  │                │  │                              │    │
│  │  K8s evidence     │  │  Metric        │  │  MockLlmClient /            │    │
│  │  (fixture +       │  │  evidence      │  │  OpenAiCompatibleLlmClient  │    │
│  │  kubectl +        │  │  (fixture +    │  │       ↓                      │    │
│  │  Java client):    │  │  HTTP client)  │  │  LlmPrompt →                │    │
│  │  pod status,      │  │                │  │  LlmReportSynth. →          │    │
│  │  restarts,        │  │  Zero Spring   │  │  LlmEnhancedReport          │    │
│  │  exit codes       │  │  dependency    │  │       ↓                      │    │
│  │                   │  └────────────────┘  │  LlmHypothesisProposer →    │    │
│  │  Zero Spring      │                      │  UnverifiedHypothesisProposal│    │
│  │  dependency       │  ┌── loki ──────┐   │                              │    │
│  └───────────────────┘  │              │   │  Guardrails: no auto-action  │    │
│                         │  Log          │   └──────────────────────────────┘    │
│                         │  evidence     │                             │
│                         │  (fixture +   │                             │
│                         │  HTTP client) │                             │
│                         │               │                             │
│                         │  Zero Spring  │                             │
│                         └───────────────┘                             │
└──────────────────────────────────────────────────────────┘
```

### Module Structure

**15 Maven modules** (11 agent modules + 4 demo-services modules: parent + order-service + payment-service + inventory-service)

| Module | Purpose | Spring Dependency |
|---|---|---|
| `sre-agent-core` | Deterministic RCA workflow engine | **None** |
| `sre-agent-k8s-provider` | K8s evidence provider (fixture + live kubectl + Java client) — pod status, restarts, exit codes | **None** |
| `sre-agent-prometheus-provider` | Prometheus metric evidence provider (fixture + HTTP client) — error rates, latency, CPU/memory | **None** |
| `sre-agent-loki-provider` | Loki log evidence provider (fixture + HTTP client) — timeout errors, exception bursts, OOM messages | **None** |
| `sre-agent-alertmanager-provider` | Alertmanager alert evidence provider (fixture + HTTP) — alert lifecycle, incident mapping | **None** |
| `sre-agent-trace-provider` | Distributed trace evidence provider (fixture + HTTP) — span latency, error spans, service dependency graph | **None** |
| `sre-agent-probe-executor` | Probe execution framework — routes LLM ProbeIntents to evidence providers, collects informational Evidence | **None** |
| `sre-agent-llm` | LLM report synthesis + Hypothesis Proposer (MockLlmClient, OpenAiCompatibleLlmClient, prompt building, report enhancement, advisory hypothesis proposals) | **None** |
| `sre-agent-cli` | Command-line adapter (Picocli) | None |
| `sre-agent-server` | Spring Boot REST API + Web UI | Spring Boot 3.x |
| `demo-services` | Instrumented demo microservices (order/payment/inventory) for fault injection | Spring Boot 3.x |

- **Evidence Taxonomy (core)**: Provider-agnostic normalized evidence model with category, signal, source kind, severity, and causal role classification

**Key constraint:** `sre-agent-core`, `sre-agent-k8s-provider`, `sre-agent-prometheus-provider`, `sre-agent-loki-provider`, `sre-agent-alertmanager-provider`, `sre-agent-trace-provider`, `sre-agent-probe-executor`, and `sre-agent-llm` have zero Spring dependency. `sre-agent-k8s-provider` supports three evidence modes: fixture-based (unit tests), live kubectl (local demo), and Kubernetes Java Client (production-grade API client). `sre-agent-prometheus-provider`, `sre-agent-loki-provider`, `sre-agent-alertmanager-provider`, and `sre-agent-trace-provider` each support two evidence modes:
fixture-based (unit tests/CI) and HTTP client (production). `demo-services` provides 3 instrumented Spring Boot microservices (order-service, payment-service, inventory-service) for local fault injection and observability validation. The workflow is pure Java. CLI and Server are thin adapters that call the same `InvestigationWorkflow`. The LLM module is optional — it enhances reports with AI-synthesized narratives and generates advisory hypothesis proposals while respecting guardrails (no auto-action, no data exfiltration, no RCA decision override). Real LLM integration is available via `OpenAiCompatibleLlmClient` (configure `LLM_BASE_URL` + `LLM_API_KEY` + `LLM_MODEL` env vars).

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+

### Build and Test

```bash
mvn test
```

Expected: 1194 tests passing.

### Run CLI Demo

**Scenario E — Competing Hypotheses:**

```bash
mvn -pl sre-agent-cli package -DskipTests
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

**Scenario F — CrashLoopBackOff:**

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/k8s_crashloop.json \
  --evidence examples/evidence/k8s_crashloop_evidence.json \
  --output /tmp/rca-crashloop-report.md \
  --show-trace
```

### Run Spring Boot Server

```bash
mvn -pl sre-agent-server spring-boot:run
```

### Use Web UI

Open http://localhost:8080/ and click **"Run Scenario E"**.

---

## API Examples

### Run Scenario E

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-e
```

Response:
```json
{
  "incidentId": "inc_20260428T100800Z",
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06,
  "scores": {
    "hyp_deployment_regression": 0.64,
    "hyp_downstream_dependency_latency": 0.58,
    "hyp_pod_oom_killed": 0.05
  },
  "competingHypotheses": ["hyp_downstream_dependency_latency"],
  "reportUrl": "/api/investigations/inc_20260428T100800Z/report",
  "traceUrl": "/api/investigations/inc_20260428T100800Z/trace"
}
```

### Run Scenario F

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-f
```

Response:
```json
{
  "incidentId": "inc_20260430T120000Z",
  "decisionType": "likely_root_cause",
  "selectedHypothesisId": "hyp_pod_crash_loop",
  "confidenceScore": 0.95,
  "scoreGap": 0.95,
  "scores": {
    "hyp_pod_crash_loop": 0.95,
    "hyp_deployment_regression": 0.00,
    "hyp_downstream_dependency_latency": 0.00
  },
  "competingHypotheses": [],
  "reportUrl": "/api/investigations/inc_20260430T120000Z/report",
  "traceUrl": "/api/investigations/inc_20260430T120000Z/trace"
}
```

### Get Markdown Report

```bash
curl http://localhost:8080/api/investigations/{incidentId}/report
```

### Get Event Trace

```bash
curl http://localhost:8080/api/investigations/{incidentId}/trace
```

### Health Check

```bash
curl http://localhost:8080/health
```

### LLM-Enhanced Report

**Generate LLM summary for Scenario E:**

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-e/llm-summary
```

Response:
```json
{
  "incidentId": "inc_20260428T100800Z",
  "llmSummary": "The incident was triggered by a deployment to order-service that introduced a regression...",
  "guardrailNotice": "This summary is AI-generated and intended as a supplementary explanation. Verify all claims against the deterministic investigation report.",
  "reportUrl": "/api/investigations/inc_20260428T100800Z/report"
}
```

**Generate LLM summary for a specific investigation:**

```bash
curl -X POST http://localhost:8080/api/investigations/{incidentId}/llm-summary
```

> **Note:** The default implementation uses `MockLlmClient` which returns deterministic placeholder summaries. To use a real LLM, configure environment variables `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` and the `OpenAiCompatibleLlmClient` will be used automatically. Supports OpenAI, DeepSeek, OpenRouter, and any OpenAI-compatible API.

---

## Design Principles

1. **Evidence-first, not LLM-first** — every conclusion is grounded in collected evidence; LLM synthesis is a supplementary layer, never the decision-maker
2. **Verification before conclusion** — hypotheses are verified against supporting/counter evidence
3. **Preserve uncertainty** — competing hypotheses are explicit, not hidden
4. **Deterministic and explainable** — same input always produces same output; every score is traceable
5. **Fully auditable** — every workflow step is recorded in Event Trace
6. **Read-only by default** — agent suggests, never executes

---

## Tech Stack

- Java 21 (records, sealed types preview)
- Spring Boot 3.3.6 (server adapter only)
- Maven multi-module
- Jackson (JSON serialization)
- Picocli (CLI framework)
- JUnit 5 + AssertJ (1194 tests)
- Static HTML + vanilla JS (minimal Web UI)

---

## Current Limitations

- **Prometheus, Loki, Alertmanager, and Trace evidence now available** — Steps M+N+O+P add `sre-agent-prometheus-provider`, `sre-agent-loki-provider`, `sre-agent-alertmanager-provider`, and `sre-agent-trace-provider` with fixture-based testing; live integration requires `--prometheus-url` / `--loki-url` / `--alertmanager-url` / `--trace-url`
- **Static evidence for Scenarios E/F** — Demo scenarios still use pre-loaded JSON, not live Prometheus/Loki/K8s queries
- **Manual confidence weights** — weights are based on SRE diagnostic experience, not learned from data
- **In-memory store** — investigation results are not persisted across server restarts
- **Real LLM integration available** — `OpenAiCompatibleLlmClient` supports OpenAI/DeepSeek/OpenRouter APIs; configure via `LLM_BASE_URL` + `LLM_API_KEY` + `LLM_MODEL` env vars; `MockLlmClient` used by default when env vars not set
- **4 diagnostic patterns** — covers deployment regression, dependency latency, resource pressure, and CrashLoopBackOff
- **2 demo scenarios** — Scenario E (competing hypotheses) and Scenario F (CrashLoopBackOff)
- **Live K8s demo is optional** — `mvn test` does not require a live cluster; live demo runs via `make live-k8s-demo`

---

## Project Structure

```
sre-production-agent/
├── pom.xml                          # Parent POM
├── Makefile                         # Build shortcuts
├── README.md
├── docs/
│   ├── architecture.md
│   ├── demo-script.md
│   ├── interview-walkthrough.md
│   ├── interview-qa.md
│   ├── resume-bullets.md
│   ├── llm-positioning.md
│   ├── future-roadmap.md
│   ├── live-k8s-demo.md
│   ├── k8s-evidence-provider.md
│   ├── k8s-rbac.md
│   ├── prometheus-evidence-provider.md
│   ├── loki-evidence-provider.md
│   ├── alertmanager-provider.md
│   ├── trace-evidence-provider.md
│   └── LOCAL_K8S_SETUP.md
├── examples/
│   ├── alerts/competing_hypotheses.json
│   ├── alerts/k8s_crashloop.json
│   ├── evidence/competing_hypotheses.json
│   ├── evidence/k8s_crashloop_evidence.json
│   └── reports/competing_hypotheses_report.md
├── sre-agent-core/                  # Pure Java RCA engine
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/core/
│       ├── domain/                  # 10 domain records
│       ├── evidence/                # EvidenceLoader, StaticEvidenceProvider
│       ├── patterns/                # PatternRegistry, BuiltinPatterns (4 patterns incl. pod_crash_loop)
│       ├── hypothesis/              # HypothesisEngine
│       ├── verification/            # VerificationEngine, ConfidenceScorer, HypothesisComparator
│       ├── report/                  # MarkdownReporter
│       ├── eventtrace/              # EventTraceStore, InMemoryEventTraceStore
│       └── workflow/                # InvestigationWorkflow, InvestigationResult
├── sre-agent-k8s-provider/          # K8s evidence provider (fixture + kubectl + Java client)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/k8s/
│       ├── K8sEvidenceProvider.java
│       └── client/                  # JavaClientKubernetesResourceReader, KubernetesClientConfig, etc.
├── sre-agent-prometheus-provider/   # Prometheus metric evidence provider (fixture + HTTP)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/prometheus/
│       ├── client/                  # PrometheusQueryClient, FixturePrometheusQueryClient, HttpPrometheusQueryClient
│       ├── parser/                  # PrometheusResponseParser
│       ├── query/                   # PrometheusQueryTemplateRegistry
│       ├── mapper/                  # PrometheusEvidenceMapper
│       └── provider/                # PrometheusEvidenceProvider
├── sre-agent-loki-provider/         # Loki log evidence provider (fixture + HTTP)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/loki/
│       ├── client/                  # LokiQueryClient, FixtureLokiQueryClient, HttpLokiQueryClient
│       ├── parser/                  # LokiResponseParser
│       ├── query/                   # LokiQueryTemplateRegistry
│       ├── mapper/                  # LokiEvidenceMapper
│       └── LokiEvidenceProvider.java
├── sre-agent-alertmanager-provider/ # Alertmanager alert evidence provider (fixture + HTTP)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/alertmanager/
│       ├── client/                  # AlertmanagerQueryClient, FixtureAlertmanagerQueryClient, HttpAlertmanagerQueryClient
│       ├── parser/                  # AlertmanagerResponseParser
│       ├── mapper/                  # AlertmanagerEvidenceMapper
│       └── AlertmanagerEvidenceProvider.java
├── sre-agent-trace-provider/        # Distributed trace evidence provider (fixture + HTTP)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/trace/
│       ├── client/                  # TraceQueryClient, FixtureTraceQueryClient, HttpTraceQueryClient
│       ├── parser/                  # TraceResponseParser
│       ├── query/                   # TraceQueryTemplateRegistry
│       ├── mapper/                  # TraceEvidenceMapper
│       └── TraceEvidenceProvider.java
├── sre-agent-probe-executor/        # Probe execution framework (routes LLM ProbeIntents to evidence providers)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/probe/
│       ├── router/                  # ProbeIntentRouter
│       ├── policy/                  # ProbeExecutionPolicy
│       ├── executor/                # FixtureProbeExecutor
│       └── mapper/                  # 5 provider mappers (Prometheus, Loki, Trace, K8s, Alertmanager)
├── k8s/
│   ├── demo-services/recommend-crashloop-demo.yaml
│   ├── demo-services/order-service.yaml
│   ├── demo-services/payment-service.yaml
│   ├── demo-services/inventory-service.yaml
│   ├── demo-services/traffic-generator.yaml
│   └── demo-services/servicemonitors.yaml
│   └── rbac/sre-agent-reader.yaml
├── sre-agent-llm/                   # LLM report synthesis (optional)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/llm/
│       ├── client/                  # LlmClient interface, MockLlmClient
│       ├── prompt/                  # LlmPromptBuilder
│       ├── synthesis/               # LlmReportSynthesizer
│       └── model/                   # LlmEnhancedReport
├── sre-agent-cli/                   # CLI adapter
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/cli/
│       ├── Main.java
│       └── InvestigateCommand.java
└── sre-agent-server/                # Spring Boot adapter
    ├── pom.xml
    └── src/main/java/ai/sreagent/server/
        ├── SreAgentApplication.java
        ├── controller/              # HealthController, InvestigationController, ObservabilityStatusController
        └── service/                 # InvestigationService, InvestigationResponse, InMemoryInvestigationStore, ObservabilityStatusService
├── scripts/
│   ├── observability/              # Helm values + install/uninstall/port-forward/check scripts
│   └── demo-services/              # Build/load/deploy/port-forward/check/traffic scripts
```

---

## Roadmap

See [docs/future-roadmap.md](docs/future-roadmap.md) for the full plan.

||| Step | Scope | Status |
|------|-------|--------|
| A | Project skeleton + domain model + JSON loading + patterns | ✅ Done |
| B | HypothesisEngine + VerificationEngine | ✅ Done |
| C | ConfidenceScorer + HypothesisComparator + InvestigationDecision | ✅ Done |
| D | CLI end-to-end + Markdown report + Event trace | ✅ Done |
| E | REST API + minimal Web UI | ✅ Done |
| F | Interview packaging + documentation | ✅ Done |
| G | LLM report synthesis | ✅ Done |
| H | Local K8s provider module setup (sre-agent-k8s-provider) | ✅ Done |
| I | K8s evidence provider (fixture-based) + pod_crash_loop pattern | ✅ Done |
| J | Wire K8s evidence into RCA workflow + Scenario F | ✅ Done |
| K | Live K8s / kind integration for Scenario F (optional live demo path) | ✅ Done |
| L | Kubernetes Java Client integration — JavaClientKubernetesResourceReader | ✅ Done |
|| M | Prometheus Metrics Evidence Provider — sre-agent-prometheus-provider | ✅ Done |
|| N | Loki Logs Evidence Provider — sre-agent-loki-provider | ✅ Done |
|| O | Alertmanager Alert Evidence Provider — sre-agent-alertmanager-provider | ✅ Done |
||| P | Distributed Trace Evidence Provider — sre-agent-trace-provider | ✅ Done |
||| Q | Observability Evidence Taxonomy / Normalization — sre-agent-core | ✅ Done |
||| R | LLM Hypothesis Proposer — sre-agent-llm | ✅ Done |
||| S | Probe Execution Framework v1 — sre-agent-probe-executor | ✅ Done |
||| T | Local Observability Stack — health checking + stack management + Live Lab Status UI | ✅ Done |
||| U | Instrumented Demo Services — fault injection + real metrics/logs/traces | ✅ Done |
||| V | Complex Live RCA Scenarios + 中文 Investigation Console | ✅ Done |
||| **Phase 4 P1** | **中文化全链路** (Hypothesis/Report/Decision type) + LLM Proposal UI 卡片 | ✅ Done |
||||| **Phase 4 P2** | **真实 LLM 接入**（OpenAiCompatibleLlmClient）+ E2E 验证 + 3 个 bug 修复 | ✅ 已完成 |
||||| **V.2-UI-2** | **React 重写**：env status 真实 API + sidebar 环境摘要 + E2E | ✅ 已完成 |
||||| **V.2-UI-3** | **服务健康总览**：KPI 卡片 + 服务表格 + 拓扑图 + 故障注入 | ✅ 已完成 |
||||| **V.2-UI-4** | **RCA 分析页**：真实 live scenario API 接入 + 语义化类型 | ✅ 已完成 |
||||| **V.2-UI-5** | **证据明细页**：RCA 证据 drilldown + 原始数据展示 | ✅ 已完成 |
||||| **V.2-UI-6** | **告警驱动 Incident Intake**：Alertmanager → IncidentTask → RCA 闭环 | ✅ 已完成 |
||||| W | 探测后 RCA 重新运行策略 | 🔲 待开始 |

---

## License

This project is for interview and portfolio demonstration purposes.

---

## 中文版 / Chinese Version

# SRE 生产环境智能体

> **一个以验证为先的 AI SRE 根因分析智能体，将故障排查转化为可审计的 证据 → 假设 → 验证 → 置信度 → 决策 工作流。**

---

## 本项目解决什么问题？

当 Kubernetes 微服务触发告警时，值班工程师面临两个都不理想的选择：

1. **手动根因分析** —— grep 日志、查看仪表盘、翻阅 Git 提交记录。速度慢、结果不一致，且难以交接。
2. **日志聊天机器人** —— 将日志粘贴到大语言模型中问"哪里出了问题"。速度快，但缺乏依据、未经验证，无法审计。

本项目提出第三条路径：**一套结构化的、以验证为先的排查工作流**，生成多个假设，逐一用证据验证，以可解释的置信度进行评分，保留不确定性而非强行指定一个虚假的根因。

**这不是一个日志聊天机器人。** 它是一个确定性的根因分析智能体，将每一条结论都视为需要验证的假设。

---

## 核心工作流

```
Alert
  ↓
Evidence Collection (8 items from deploy logs, metrics, git diff, service topology)
  ↓
Diagnostic Pattern Matching (4 built-in patterns)
  ↓
Hypothesis Generation (1 hypothesis per pattern)
  ↓
Verification (supporting / counter / missing / contradiction classification)
  ↓
Confidence Scoring (deterministic, explainable formula)
  ↓
Hypothesis Comparison (leading vs competing)
  ↓
Investigation Decision (identified_root_cause | competing_hypotheses | escalation | insufficient_evidence)
  ↓
Markdown Report + Event Trace
  ↓
LLM Synthesis (optional) ── MockLlmClient / OpenAiCompatibleLlmClient → LlmPromptBuilder → LlmReportSynthesizer → LlmEnhancedReport
  ↓
LLM Proposal (optional) ── LlmHypothesisProposer → UnverifiedHypothesisProposal → ProbeIntent → Probe Execution
```

每个步骤都记录在事件追踪（Event Trace）中——一份完全可审计的排查日志。

---

## 演示：场景 E —— 竞争性假设

`order-service` 在一次部署后错误率飙升。但 `payment-service` 的延迟也同时上升。哪个才是根因？

```
deployment_regression         score = 0.64
downstream_dependency_latency score = 0.58
pod_oom_killed                score = 0.05
```

**分数差距 = 0.06** —— 太接近，无法判定。

**决策：`competing_hypotheses`（竞争性假设）** —— 智能体保留两种解释，并建议下一步探测方向，而不是强行给出单一根因。

这是核心差异化特征：**智能体知道自己何时不知道。**

---

## 演示：场景 F —— CrashLoopBackOff 检测

`demo` 命名空间中的 `recommend-service` 进入 CrashLoopBackOff 状态。智能体收集 K8s 证据（Pod 状态、容器重启、退出码）并以高置信度识别根因。

```
pod_crash_loop                 score = 0.95
deployment_regression          score = 0.00
downstream_dependency_latency  score = 0.00
```

**决策：`likely_root_cause`** —— `pod_crash_loop` 以明显优势领先。

这展示了 K8s 证据提供器模块（`sre-agent-k8s-provider`）将基于固件的 K8s 数据输入到同一确定性根因分析工作流中。

---

## 实时 K8s 演示（Step K）

基于 Fixture 的场景 F 也可以使用本地 `kind` 集群的**实时 Kubernetes 证据**运行：

```bash
make build
make cluster-up
make live-k8s-demo
```

这会在 `kind` 中部署一个 CrashLoopBackOff 工作负载，通过 kubectl 收集真实的 K8s 证据，并运行相同的 RCA 工作流。结果完全一致：`pod_crash_loop → likely_root_cause`。

完整演示流程详见 [docs/live-k8s-demo.md](docs/live-k8s-demo.md)。

---

## Alertmanager 告警证据提供器（Step O）

Step O 新增了专用 `sre-agent-alertmanager-provider` 模块，从 Alertmanager 收集**告警证据**并映射到核心 RCA 流水线使用的通用 `Evidence` 对象。通过添加告警生命周期和严重性证据，补充了 Prometheus 和 Loki 提供器的功能。

**关键设计：** `sre-agent-core` 零 Alertmanager 依赖。该提供器是纯适配器，输出 `source = "alertmanager"` 的 `Evidence`。

```bash
# 使用 fixture 收集 Alertmanager 证据（无需实时 Alertmanager）
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-alertmanager-evidence \
  --service order-service \
  --namespace demo \
  --output /tmp/alertmanager_evidence.json \
  --reader fixture

# 从实时实例收集 Alertmanager 证据
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-alertmanager-evidence \
  --service order-service \
  --namespace demo \
  --output /tmp/alertmanager_live.json \
  --reader http \
  --alertmanager-url http://localhost:9093
```

**关键新增：**
- `AlertmanagerQueryClient` 接口 → `FixtureAlertmanagerQueryClient` / `HttpAlertmanagerQueryClient`
- `AlertmanagerResponseParser` — 处理告警/路由/静默结果，状态解析，空结果
- `AlertmanagerEvidenceMapper` — 映射到 7 种语义证据类型（告警生命周期、事件映射、严重性证据）
- `AlertmanagerEvidenceProvider` — 双重输出：事件 + 证据
- 45 个新测试（共 308 个，从 263 个增加）

---

## 分布式追踪证据提供器（Step P）

Step P 新增了专用 `sre-agent-trace-provider` 模块，从 Jaeger/Tempo 收集**追踪证据**（span 延迟、错误 span、服务依赖图）并映射到核心 RCA 流水线使用的通用 `Evidence` 对象。通过添加分布式追踪可观测性，补充了 Prometheus、Loki 和 Alertmanager 提供器的功能。

**关键设计：** `sre-agent-core` 零 trace 依赖。该提供器是纯适配器，输出 `source = "trace"` 的 `Evidence`。

```bash
# 使用 fixture 收集追踪证据（无需实时 Jaeger/Tempo）
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS \
  --output /tmp/trace_slow_spans.json \
  --reader fixture

# 同时收集多种查询类型
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS,ERROR_SPANS,SERVICE_DEPENDENCY \
  --output /tmp/trace_multi.json \
  --reader fixture

# 从实时 Jaeger/Tempo 实例收集追踪证据
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  collect-trace-evidence \
  --service order-service \
  --namespace demo \
  --query-type SLOW_SPANS \
  --output /tmp/trace_slow_live.json \
  --reader http \
  --trace-url http://localhost:16686
```

**关键新增：**
- `TraceQueryClient` 接口 → `FixtureTraceQueryClient` / `HttpTraceQueryClient`
- `TraceResponseParser` — 处理 trace/span 结果、时长解析、错误状态、服务依赖提取
- `TraceQueryTemplateRegistry` — 6 种查询类型：SLOW_SPANS, ERROR_SPANS, SERVICE_DEPENDENCY, SPAN_ERRORS_BY_SERVICE, TRACE_DURATION_HISTOGRAM, SERVICE_CALL_GRAPH
- `TraceEvidenceMapper` — 映射到 8 种语义证据类型（如 `trace_span_latency_high`、`trace_error_span_detected`、`trace_service_dependency`、`trace_no_signal`）
- 35 个新测试（共 343 个，从 308 个增加）

详见 [docs/trace-evidence-provider.md](docs/trace-evidence-provider.md)。

---

## 架构概览

```
┌──────────────────────────────────────────────────────────┐
│  sre-agent-cli        sre-agent-server                   │
│  (Picocli)            (Spring Boot 3.x)                  │
│       │                      │                            │
│       └──────────┬───────────┘                            │
│                  ↓                                        │
│         InvestigationWorkflow                             │
│         (shared orchestrator)                             │
│                  ↓                                        │
│  ┌─────────── sre-agent-core ───────────────────┐        │
│  │                                               │        │
│  │  EvidenceLoader → PatternRegistry             │        │
│  │       ↓                                       │        │
│  │  HypothesisEngine                             │        │
│  │       ↓                                       │        │
│  │  VerificationEngine                           │        │
│  │       ↓                                       │        │
│  │  ConfidenceScorer                             │        │
│  │       ↓                                       │        │
│  │  HypothesisComparator → InvestigationDecision │        │
│  │       ↓                                       │        │
│  │  MarkdownReporter + EventTraceStore           │        │
│  │                                               │        │
│  │  Zero Spring dependency                       │        │
│  └───────────────────────────────────────────────┘        │
│          ↑               ↓ (optional)                     │
│  ┌──── sre-agent-k8s-provider ────┐  ┌──── sre-agent-llm ──────────────────┐
│  │                                │  │                                      │
│  │  K8s evidence (fixture +        │  │  MockLlmClient /                    │
│  │  live kubectl + Java client):  │  │  OpenAiCompatibleLlmClient          │
│  │  pod status, container         │  │       ↓                              │
│  │  restarts, exit codes          │  │  LlmReportSynthesizer →              │
│  │                                │  │  LlmEnhancedReport                   │
│  │  Zero Spring dependency        │  │       ↓                              │
│  └────────────────────────────────┘  │  LlmHypothesisProposer →            │
│                                      │  UnverifiedHypothesisProposal        │
│                                      │                                      │
│                                      │  Guardrails: no auto-act.            │
│                                      └──────────────────────────────────────┘
└──────────────────────────────────────────────────────────┘
```

### 模块结构

**15 个 Maven 模块**（11 个 Agent 模块 + 4 个 Demo Services 模块：parent + order-service + payment-service + inventory-service）

| 模块 | 用途 | Spring 依赖 |
|---|---|---|
| `sre-agent-core` | 确定性根因分析工作流引擎 | **无** |
| `sre-agent-k8s-provider` | K8s 证据提供器（fixture + 实时 kubectl + Java 客户端）— Pod 状态、重启、退出码 | **无** |
| `sre-agent-prometheus-provider` | Prometheus 指标证据提供器（Fixture + HTTP 客户端）— 错误率、延迟、CPU/内存 | **无** |
| `sre-agent-loki-provider` | Loki 日志证据提供器（Fixture + HTTP 客户端）— 超时错误、异常爆发、OOM 消息 | **无** |
| `sre-agent-alertmanager-provider` | Alertmanager 告警证据提供器（Fixture + HTTP）— 告警生命周期、事件映射 | **无** |
| `sre-agent-trace-provider` | 分布式追踪证据提供器（Fixture + HTTP）— span 延迟、错误 span、服务依赖 | **无** |
| `sre-agent-probe-executor` | 探测执行框架（将 LLM ProbeIntent 路由到证据提供器） | **无** |
| `sre-agent-llm` | LLM 报告综合 + 假设提议器（MockLlmClient、OpenAiCompatibleLlmClient、提示词构建、报告增强） | **无** |
| `sre-agent-cli` | 命令行适配器（Picocli） | 无 |
| `sre-agent-server` | Spring Boot REST API + Web UI | Spring Boot 3.x |
| `demo-services` | 仪表化演示微服务（order/payment/inventory）用于故障注入 | Spring Boot 3.x |
- **证据分类体系（core）**: Provider-agnostic 归一化证据模型，包含 category/signal/sourceKind/severity/causalRole 分类

|**关键约束：** `sre-agent-core`、`sre-agent-k8s-provider`、`sre-agent-prometheus-provider`、`sre-agent-loki-provider`、`sre-agent-alertmanager-provider`、`sre-agent-trace-provider`、`sre-agent-probe-executor` 和 `sre-agent-llm` 零 Spring 依赖。`sre-agent-k8s-provider` 支持三种证据模式：基于 Fixture（单元测试）、实时 kubectl（本地演示）和 Kubernetes Java Client（生产级 API 客户端）。`sre-agent-prometheus-provider`、`sre-agent-loki-provider`、`sre-agent-alertmanager-provider` 和 `sre-agent-trace-provider` 各支持两种证据模式：基于 Fixture（单元测试/CI）和 HTTP 客户端（生产环境）。工作流是纯 Java 实现。CLI 和 Server 是薄适配层，调用同一个 `InvestigationWorkflow`。LLM 模块是可选的——它在遵守护栏（不自动执行操作、不泄露数据、不覆盖 RCA 决策）的前提下，用 AI 综合叙述来增强报告。真实 LLM 集成可通过 `OpenAiCompatibleLlmClient` 实现（配置 `LLM_BASE_URL` + `LLM_API_KEY` + `LLM_MODEL` 环境变量）。

---

## 快速开始

### 前置条件

- Java 21+
- Maven 3.8+

### 构建与测试

```bash
mvn test
```

预期结果：1194 个测试全部通过。

### 运行 CLI 演示

**场景 E —— 竞争性假设：**

```bash
mvn -pl sre-agent-cli package -DskipTests
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

**场景 F —— CrashLoopBackOff：**

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/k8s_crashloop.json \
  --evidence examples/evidence/k8s_crashloop_evidence.json \
  --output /tmp/rca-crashloop-report.md \
  --show-trace
```

### 运行 Spring Boot 服务器

```bash
mvn -pl sre-agent-server spring-boot:run
```

### 使用 Web UI

打开 http://localhost:8080/，点击 **"Run Scenario E"**。

---

## API 示例

### 运行场景 E

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-e
```

响应：
```json
{
  "incidentId": "inc_20260428T100800Z",
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06,
  "scores": {
    "hyp_deployment_regression": 0.64,
    "hyp_downstream_dependency_latency": 0.58,
    "hyp_pod_oom_killed": 0.05
  },
  "competingHypotheses": ["hyp_downstream_dependency_latency"],
  "reportUrl": "/api/investigations/inc_20260428T100800Z/report",
  "traceUrl": "/api/investigations/inc_20260428T100800Z/trace"
}
```

### 运行场景 F

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-f
```

响应：
```json
{
  "incidentId": "inc_20260430T120000Z",
  "decisionType": "likely_root_cause",
  "selectedHypothesisId": "hyp_pod_crash_loop",
  "confidenceScore": 0.95,
  "scoreGap": 0.95,
  "scores": {
    "hyp_pod_crash_loop": 0.95,
    "hyp_deployment_regression": 0.00,
    "hyp_downstream_dependency_latency": 0.00
  },
  "competingHypotheses": [],
  "reportUrl": "/api/investigations/inc_20260430T120000Z/report",
  "traceUrl": "/api/investigations/inc_20260430T120000Z/trace"
}
```

### 获取 Markdown 报告

```bash
curl http://localhost:8080/api/investigations/{incidentId}/report
```

### 获取事件追踪

```bash
curl http://localhost:8080/api/investigations/{incidentId}/trace
```

### 健康检查

```bash
curl http://localhost:8080/health
```

### LLM 增强报告

**为场景 E 生成 LLM 摘要：**

```bash
curl -X POST http://localhost:8080/api/investigations/scenario-e/llm-summary
```

响应：
```json
{
  "incidentId": "inc_20260428T100800Z",
  "llmSummary": "The incident was triggered by a deployment to order-service that introduced a regression...",
  "guardrailNotice": "This summary is AI-generated and intended as a supplementary explanation. Verify all claims against the deterministic investigation report.",
  "reportUrl": "/api/investigations/inc_20260428T100800Z/report"
}
```

**为特定排查生成 LLM 摘要：**

```bash
curl -X POST http://localhost:8080/api/investigations/{incidentId}/llm-summary
```

> **注意：** 默认实现使用 `MockLlmClient`，返回确定性的占位摘要。如需使用真实 LLM，请配置环境变量 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`，系统将自动使用 `OpenAiCompatibleLlmClient`。支持 OpenAI、DeepSeek、OpenRouter 及任何 OpenAI 兼容 API。

---

## 设计原则

1. **证据优先，而非 LLM 优先** —— 每个结论都基于收集到的证据；LLM 综合是补充层，从不做决策
2. **先验证再结论** —— 假设需通过支持/反驳证据进行验证
3. **保留不确定性** —— 竞争性假设是显式呈现的，而非被隐藏
4. **确定性与可解释** —— 相同输入始终产生相同输出；每个分数都可追溯
5. **完全可审计** —— 每个工作流步骤都记录在事件追踪中
6. **默认只读** —— 智能体只提建议，从不执行操作

---

## 技术栈

- Java 21（records、sealed types 预览特性）
- Spring Boot 3.3.6（仅服务器适配器）
- Maven 多模块
- Jackson（JSON 序列化）
- Picocli（CLI 框架）
- JUnit 5 + AssertJ（1194 个测试）
- 静态 HTML + 原生 JS（轻量 Web UI）

---

## 当前限制

- **Prometheus、Loki、Alertmanager 和 Trace 证据现已可用** — Steps M+N+O+P 新增 `sre-agent-prometheus-provider`、`sre-agent-loki-provider`、`sre-agent-alertmanager-provider` 和 `sre-agent-trace-provider`，支持基于 Fixture 的测试；实时集成需要 `--prometheus-url` / `--loki-url` / `--alertmanager-url` / `--trace-url`
- **场景 E/F 使用静态证据** — 演示场景仍使用预加载的 JSON，而非实时 Prometheus/Loki/K8s 查询
- **手动置信度权重** —— 权重基于 SRE 诊断经验设定，未通过数据学习
- **内存存储** —— 排查结果不会在服务器重启后持久化
- **真实 LLM 集成已可用** — `OpenAiCompatibleLlmClient` 支持 OpenAI/DeepSeek/OpenRouter API；通过 `LLM_BASE_URL` + `LLM_API_KEY` + `LLM_MODEL` 环境变量配置；未配置时默认使用 `MockLlmClient`
- **4 种诊断模式** —— 覆盖部署回退、依赖延迟、资源压力和 CrashLoopBackOff
- **2 个演示场景** —— 场景 E（竞争性假设）和场景 F（CrashLoopBackOff）
- **实时 K8s 演示是可选的** —— `mvn test` 不需要实时集群；实时演示通过 `make live-k8s-demo` 运行

---

## 项目结构

```
sre-production-agent/
├── pom.xml                          # 父 POM
├── Makefile                         # 构建快捷方式
├── README.md
├── docs/
│   ├── architecture.md
│   ├── demo-script.md
│   ├── interview-walkthrough.md
│   ├── interview-qa.md
│   ├── resume-bullets.md
│   ├── llm-positioning.md
│   ├── future-roadmap.md
│   ├── live-k8s-demo.md
│   ├── k8s-evidence-provider.md
│   ├── k8s-rbac.md
│   ├── prometheus-evidence-provider.md
│   ├── loki-evidence-provider.md
│   ├── alertmanager-provider.md
│   ├── trace-evidence-provider.md
│   └── LOCAL_K8S_SETUP.md
├── examples/
│   ├── alerts/competing_hypotheses.json
│   ├── alerts/k8s_crashloop.json
│   ├── evidence/competing_hypotheses.json
│   ├── evidence/k8s_crashloop_evidence.json
│   └── reports/competing_hypotheses_report.md
├── sre-agent-core/                  # 纯 Java 根因分析引擎
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/core/
│       ├── domain/                  # 10 个领域 record
│       ├── evidence/                # EvidenceLoader, StaticEvidenceProvider
│       ├── patterns/                # PatternRegistry, BuiltinPatterns（4 个模式，含 pod_crash_loop）
│       ├── hypothesis/              # HypothesisEngine
│       ├── verification/            # VerificationEngine, ConfidenceScorer, HypothesisComparator
│       ├── report/                  # MarkdownReporter
│       ├── eventtrace/              # EventTraceStore, InMemoryEventTraceStore
│       └── workflow/                # InvestigationWorkflow, InvestigationResult
├── sre-agent-k8s-provider/          # K8s 证据提供器（基于 Fixture）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/k8s/
│       ├── K8sEvidenceProvider.java
│       └── client/                  # JavaClientKubernetesResourceReader, KubernetesClientConfig 等
├── sre-agent-prometheus-provider/   # Prometheus 指标证据提供器（Fixture + HTTP）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/prometheus/
│       ├── client/                  # PrometheusQueryClient, FixturePrometheusQueryClient, HttpPrometheusQueryClient
│       ├── parser/                  # PrometheusResponseParser
│       ├── query/                   # PrometheusQueryTemplateRegistry
│       ├── mapper/                  # PrometheusEvidenceMapper
│       └── provider/                # PrometheusEvidenceProvider
├── sre-agent-loki-provider/         # Loki 日志证据提供器（Fixture + HTTP）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/loki/
│       ├── client/                  # LokiQueryClient, FixtureLokiQueryClient, HttpLokiQueryClient
│       ├── parser/                  # LokiResponseParser
│       ├── query/                   # LokiQueryTemplateRegistry
│       ├── mapper/                  # LokiEvidenceMapper
│       └── LokiEvidenceProvider.java
├── sre-agent-alertmanager-provider/ # Alertmanager 告警证据提供器（Fixture + HTTP）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/alertmanager/
│       ├── client/                  # AlertmanagerQueryClient, FixtureAlertmanagerQueryClient, HttpAlertmanagerQueryClient
│       ├── parser/                  # AlertmanagerResponseParser
│       ├── mapper/                  # AlertmanagerEvidenceMapper
│       └── AlertmanagerEvidenceProvider.java
├── sre-agent-trace-provider/     # 分布式追踪证据提供器（Fixture + HTTP）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/trace/
│       ├── client/                  # TraceQueryClient, FixtureTraceQueryClient, HttpTraceQueryClient
│       ├── parser/                  # TraceResponseParser
│       ├── query/                   # TraceQueryTemplateRegistry
│       ├── mapper/                  # TraceEvidenceMapper
│       └── TraceEvidenceProvider.java
├── sre-agent-probe-executor/        # 探测执行框架（将 LLM ProbeIntent 路由到证据提供者）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/probe/
│       ├── router/                  # ProbeIntentRouter
│       ├── policy/                  # ProbeExecutionPolicy
│       ├── executor/                # FixtureProbeExecutor
│       └── mapper/                  # 5 provider mappers (Prometheus, Loki, Trace, K8s, Alertmanager)
├── k8s/
│   ├── demo-services/recommend-crashloop-demo.yaml
│   ├── demo-services/order-service.yaml
│   ├── demo-services/payment-service.yaml
│   ├── demo-services/inventory-service.yaml
│   ├── demo-services/traffic-generator.yaml
│   └── demo-services/servicemonitors.yaml
│   └── rbac/sre-agent-reader.yaml
├── sre-agent-llm/                   # LLM 报告综合（可选）
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/llm/
│       ├── client/                  # LlmClient 接口, MockLlmClient
│       ├── prompt/                  # LlmPromptBuilder
│       ├── synthesis/               # LlmReportSynthesizer
│       └── model/                   # LlmEnhancedReport
├── sre-agent-cli/                   # CLI 适配器
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/cli/
│       ├── Main.java
│       └── InvestigateCommand.java
└── sre-agent-server/                # Spring Boot 适配器
    ├── pom.xml
    └── src/main/java/ai/sreagent/server/
        ├── SreAgentApplication.java
        ├── controller/              # HealthController, InvestigationController, ObservabilityStatusController
        └── service/                 # InvestigationService, InvestigationResponse, InMemoryInvestigationStore, ObservabilityStatusService
├── scripts/
│   └── observability/              # Helm values + install/uninstall/port-forward/check 脚本
```

---

## 路线图

完整计划详见 [docs/future-roadmap.md](docs/future-roadmap.md)。

| 步骤 | 范围 | 状态 |
|------|------|------|
| A | 项目骨架 + 领域模型 + JSON 加载 + 诊断模式 | ✅ 已完成 |
| B | HypothesisEngine + VerificationEngine | ✅ 已完成 |
| C | ConfidenceScorer + HypothesisComparator + InvestigationDecision | ✅ 已完成 |
| D | CLI 端到端 + Markdown 报告 + 事件追踪 | ✅ 已完成 |
| E | REST API + 轻量 Web UI | ✅ 已完成 |
| F | 面试包装 + 文档 | ✅ 已完成 |
| G | LLM 报告综合 | ✅ 已完成 |
| H | 本地 K8s 提供器模块搭建（sre-agent-k8s-provider） | ✅ 已完成 |
| I | K8s 证据提供器（基于 Fixture）+ pod_crash_loop 模式 | ✅ 已完成 |
| J | 将 K8s 证据接入 RCA 工作流 + 场景 F | ✅ 已完成 |
|| K | 实时 K8s / kind 集成，用于场景 F（可选实时演示路径） | ✅ 已完成 |
|| L | Kubernetes Java Client 集成 — JavaClientKubernetesResourceReader | ✅ 已完成 |
||| M | Prometheus 指标证据提供者 — sre-agent-prometheus-provider | ✅ 已完成 |
||| N | Loki 日志证据提供者 — sre-agent-loki-provider | ✅ 已完成 |
||| O | Alertmanager 告警证据提供者 — sre-agent-alertmanager-provider | ✅ 已完成 |
|||| P | 分布式追踪证据提供者 — sre-agent-trace-provider | ✅ 已完成 |
|||| Q | 证据分类体系 / 归一化 — sre-agent-core | ✅ 已完成 |
|||| R | LLM 假设提议器 — sre-agent-llm | ✅ 已完成 |
|||| S | 探测执行框架 v1 — sre-agent-probe-executor | ✅ 已完成 |
|||| T | 本地可观测性栈（健康检查 + 栈管理 + Live Lab Status UI） | ✅ 已完成 |
|||| U | 示例微服务（故障注入 + 真实指标/日志/链路追踪） | ✅ 已完成 |
|||| V | 复杂实时 RCA 场景 + 中文排查控制台 | ✅ 已完成 |
|||| **Phase 4 P1** | **中文化全链路**（假设/报告/决策类型）+ LLM Proposal UI 卡片 | ✅ 已完成 |
||||| **Phase 4 P2** | **真实 LLM 接入**（OpenAiCompatibleLlmClient）+ E2E 验证 + 3 个 bug 修复 | ✅ 已完成 |
||||| **V.2-UI-2** | **React 重写**：env status 真实 API + sidebar 环境摘要 + E2E | ✅ 已完成 |
||||| **V.2-UI-3** | **服务健康总览**：KPI 卡片 + 服务表格 + 拓扑图 + 故障注入 | ✅ 已完成 |
||||| **V.2-UI-4** | **RCA 分析页**：真实 live scenario API 接入 + 语义化类型 | ✅ 已完成 |
||||| **V.2-UI-5** | **证据明细页**：RCA 证据 drilldown + 原始数据展示 | ✅ 已完成 |
||||| **V.2-UI-6** | **告警驱动 Incident Intake**：Alertmanager → IncidentTask → RCA 闭环 | ✅ 已完成 |
||||| W | 探测后 RCA 重新运行策略 | 🔲 待开始 |