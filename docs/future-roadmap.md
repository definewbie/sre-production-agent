# Future Roadmap

## Priority Depends on Interview Target

|| Target Role | Priority Step | Reason |
|---|---|---|
|| AI Agent Engineer | ~~Steps G–J~~ ✅ Done → ~~Step R~~ ✅ Done → ~~Step S~~ ✅ Done → ~~Step T~~ ✅ Done → ~~Step U~~ ✅ Done → Step V: Complex Live RCA | Demonstrates principled LLM + evidence + probe integration |
|| SRE / Platform Engineer | ~~Steps H–Q~~ ✅ Done → ~~Step R~~ ✅ Done → ~~Step S~~ ✅ Done → ~~Step T~~ ✅ Done → ~~Step U~~ ✅ Done → Step V: Complex Live RCA | Demonstrates full observability pipeline |
|| Engineering Manager / Architect | Polish architecture narrative | Demonstrates design trade-off reasoning |

---

## Step G: LLM Report Synthesis ✅ COMPLETED

**Status:** Completed. See `docs/llm-positioning.md` for full details.

**What was built:**
- `sre-agent-llm` module with `LlmClient` interface, `MockLlmClient`, `LlmPromptBuilder`, `LlmReportSynthesizer`
- `LlmEnhancedReport` record separating `base*` (deterministic) from LLM-generated fields
- Prompt guardrails: LLM cannot change decision, scores, evidence, or invent K8s/EC2/RDS/CMDB facts
- REST API: `POST /api/investigations/scenario-e/llm-summary`
- UI: LLM-assisted explanation section with Authoritative/Advisory badges and guardrail notice
- 23 new tests (111 total)

---

## Step H: Local K8s Provider Setup ✅ COMPLETED

**Status:** Completed. Fixture-based K8s evidence module — no live cluster needed.

**What was built:**
- `sre-agent-k8s-provider` module (5th Maven module)
- Fixture-based K8s evidence approach: realistic alert/evidence JSON fixtures instead of live `kind` cluster
- Zero K8s client library dependency — all evidence loaded from classpath fixtures
- Contains: K8s alert JSON fixtures, K8s evidence JSON fixtures (pod events, container status, deployment metadata), K8s fixture loader
- 24 tests in k8s module

**Design decision:** Chose fixture-based approach over `kind` cluster to keep the demo self-contained, fast, and CI-friendly. The fixture loader mirrors the structure a real K8s API client would return, making it straightforward to swap in a live provider later.

---

## Step I: K8s Evidence Provider ✅ COMPLETED

**Status:** Completed. `pod_crash_loop` diagnostic pattern wired into the full RCA pipeline.

**What was built:**
- `pod_crash_loop` diagnostic pattern (4th pattern, `baseScore` 0.25)
- Evidence types: `container_crash_loop_backoff`, `pod_restart_count_increased`, `pod_not_ready`, `deployment_metadata`
- Wired into `BuiltinPatterns`, `HypothesisEngine`, `VerificationEngine`
- Fixture-based K8s evidence provider (loaded from `sre-agent-k8s-provider` module)
- `PatternRegistryTest` and `HypothesisEngineTest` updated for 4 patterns total

**Architecture:**
```
InvestigationService
  ↓
K8sFixtureLoader (sre-agent-k8s-provider)
  ↓
List<Evidence> → HypothesisEngine → VerificationEngine
  ↓
ScoredHypothesis (pod_crash_loop, score=0.95, likely_root_cause)
```

**Design note:** Followed the same pattern structure as the existing 3 patterns — no special casing needed for K8s evidence types, confirming the platform-agnostic scoring pipeline design.

---

## Step I+: Multi-Platform Model & Evidence Providers (Future Extension)

> **Note:** This is a future extension beyond current scope. Steps H–J implemented K8s-specific evidence using a fixture-based approach. Multi-platform support remains a valid long-term direction.

**Goal:** Remove K8s-centric assumptions from the domain model and support non-K8s deployment targets (EC2 instances, AWS managed services).

**Why:** Real production environments are heterogeneous — a single incident may involve K8s-deployed services calling RDS databases behind ALB, with ElastiCache as a caching layer. The agent must be able to collect and reason about evidence from all these platforms.

### Model Changes (Prerequisite)

| Record | Field Change | Rationale |
|---|---|---|
| `IncidentTask` | `namespace` → `scope` | Not all platforms have namespaces. Use `scope` for namespace / AZ / VPC / region. |
| `IncidentTask` | Add `platform` field | `"kubernetes"` / `"ec2"` / `"managed_service"`. Determines which evidence providers to activate. |
| `Evidence` | `service` → `entity` | RDS instance, ElastiCache cluster, ALB — these aren't "services". `entity` is platform-neutral. |
| `Evidence` | Add `entityType` field | `"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`. Enables type-aware pattern matching. |

**Impact assessment:**
- Core pipeline (VerificationEngine, ConfidenceScorer, HypothesisComparator) — **zero changes**. They operate on `evidenceType` strings only.
- `HypothesisEngine` — minor: reads `incident.service()` for `affectedService`.
- Alert JSON / Evidence JSON — field name updates.
- Tests — update JSON fixtures and field references.
- Estimated test updates: ~15-20 files, mechanical changes.

### New Evidence Providers

| Provider | Source | Evidence Types |
|---|---|---|
| `Ec2EvidenceProvider` | CloudWatch Metrics + EC2 API | `ec2_status_check_impaired`, `ec2_cpu_steal_high`, `ebs_iops_saturation` |
| `AwsManagedServiceEvidenceProvider` | CloudWatch + RDS/ElastiCache API | `rds_connection_exhaustion`, `rds_replica_lag_high`, `elasticache_eviction_spike`, `elasticache_replication_lag` |
| `CmdbTopologyProvider` | Internal CMDB / service registry | `service_dependency_match`, `deployment_topology`, `service_owner`, `capacity_baseline` |

### New Diagnostic Patterns

| Pattern | Root Cause Type | Key Evidence |
|---|---|---|
| `ec2_instance_degradation` | `infra_degradation` | CPU steal time, EBS IOPS saturation, status check impaired |
| `rds_connection_exhaustion` | `resource_exhaustion` | max_connections reached, connection timeout spike, replica lag |
| `elasticache_memory_pressure` | `resource_pressure` | eviction rate spike, swap usage, replication lag |
| `managed_service_failover` | `infra_failover` | multi-AZ failover event, DNS endpoint change, connection reset burst |

### Why This Design Works

The core insight: **the scoring pipeline is already platform-agnostic**. It only cares about `evidenceType` string matching and confidence weights. Adding AWS evidence types and patterns follows the exact same mechanism as the existing K8s patterns — no special casing needed.

**Estimated effort:** 3-4 days (model changes 1 day + providers 2-3 days)

---

## Step J: Wire K8s Evidence into RCA Workflow ✅ COMPLETED

**Status:** Completed. Full end-to-end Scenario F for K8s CrashLoopBackOff.

**What was built:**
- New **Scenario F**: `recommend-service` CrashLoopBackOff in `demo` namespace
- Server: `InvestigationService.runScenarioF()` + `POST /api/investigations/scenario-f` endpoint
- CLI: Scenario F alert + evidence JSON support
- UI: Scenario F button in `index.html`
- 27 new tests (162 total, up from 135)
- Score: `pod_crash_loop` = 0.95, decision = `likely_root_cause`

**Why this matters:**
- Demonstrates the K8s evidence pipeline works end-to-end: alert → evidence → pattern matching → scored hypothesis → decision
- Validates that the fixture-based K8s provider integrates cleanly with the existing RCA workflow
- Provides a compelling demo scenario for K8s-native failure modes

> **Note:** The original "Human Feedback and Confidence Calibration" plan has been deferred to a future step. It remains a valid long-term direction but is not currently prioritized.

---

## Step K: Live K8s / kind Integration ✅ COMPLETED

**Status:** Completed. Scenario F extended with optional live kind demo path.

**What was built:**
- CrashLoopBackOff demo manifest: `k8s/demo-services/recommend-crashloop-demo.yaml`
- Namespace-scoped read-only RBAC: `k8s/rbac/sre-agent-reader.yaml`
- Makefile targets: `cluster-up`, `deploy-crashloop-demo`, `wait-crashloop`, `collect-k8s-evidence-live`, `investigate-k8s-live`, `clean-crashloop-demo`, `live-k8s-demo`
- Live K8s demo documentation: `docs/live-k8s-demo.md`
- K8s evidence provider architecture doc: `docs/k8s-evidence-provider.md`
- Updated `docs/LOCAL_K8S_SETUP.md` with live demo instructions

**Why this matters:**
- Proves the provider abstraction works with real K8s cluster data, not just fixture JSON
- Same RCA core produces `likely_root_cause` from both fixture and live evidence
- Demonstrates SRE / Platform engineering capability with real K8s tooling
- `mvn test` remains independent of live cluster

**Two evidence paths:**

| Path | Purpose | Run by |
|------|---------|--------|
| Fixture (default) | Deterministic CI / unit tests | `mvn test` |
| Live kind (optional) | Local demo / platform credibility | `make live-k8s-demo` |

---

## Step L: Kubernetes Java Client Integration ✅ COMPLETED

**Status:** Completed. Production-grade Kubernetes Java Client as an alternative evidence reader.

**What was built:**
- Added `io.kubernetes:client-java:24.0.0` dependency to `sre-agent-k8s-provider`
- `JavaClientKubernetesResourceReader` — implements `KubernetesResourceReader` SPI using the official Java client
- `KubernetesClientConfig` — configuration holder for client mode, kubeconfig path, namespace, timeouts
- `KubernetesApiClientFactory` — client lifecycle management (kubeconfig + in-cluster modes)
- `KubernetesEvidenceCollectionException` — typed exception for evidence collection failures
- CLI flags: `--reader java-client`, `--client-mode <kubeconfig|in-cluster>`, `--kubeconfig <path>`
- Updated RBAC YAML and created `docs/k8s-rbac.md`
- Updated `docs/k8s-evidence-provider.md` with Java client architecture
- Bug fix: `mapPodToSemanticEvidence()` now detects CrashLoop from terminated container state (not just waiting state)

**Why this matters:**
- Demonstrates production-grade K8s API integration using the official Java client library
- Supports both kubeconfig (local dev) and in-cluster (pod) authentication modes
- Validates the `KubernetesResourceReader` SPI abstraction — fixture, kubectl, and Java client all produce identical RCA results
- 15 new tests (186 total, up from 171)
- Live `kind` validation: `hyp_pod_crash_loop = 0.95`, decision = `likely_root_cause`

**Three evidence paths:**

| Path | Purpose | Run by |
|------|---------|--------|
| Fixture (default) | Deterministic CI / unit tests | `mvn test` |
| Live kubectl (optional) | Local demo via shell | `make live-k8s-demo` |
| Java Client (optional) | Production-grade API client | `--reader java-client` |

---

## Step K+: More Diagnostic Patterns (Future Extension)

**Goal:** Expand coverage beyond the current 4 patterns (config_drift, resource_exhaustion, dependency_failure, pod_crash_loop).

**Candidates:**
- DNS resolution failure
- Certificate expiry / TLS handshake errors
- Network partition / connectivity loss
- Database connection pool exhaustion
- Rate limiting / throttling
- Configuration drift
- Hot loop / CPU spike from bad code path
- Disk I/O saturation
- Cascading failure / circuit breaker missing

**Implementation pattern:** Each pattern follows the same structure — define evidence requirements, supporting types, counter types, and confidence weights. Add to `BuiltinPatterns` or load from external configuration.

**Estimated effort:** 1-2 days per pattern (including tests and evidence JSON)

---

## Step M: Prometheus Metrics Evidence Provider ✅ COMPLETED

**Status:** Completed. Prometheus metric evidence adapter module with fixture-based testing.

**What was built:**
- `sre-agent-prometheus-provider` module (6th Maven module, package `ai.sreagent.prometheus`)
- `PrometheusQueryClient` interface (SPI) with `FixturePrometheusQueryClient` and `HttpPrometheusQueryClient` implementations
- `PrometheusResponseParser` — handles vector/range results, NaN/+Inf, empty results
- `PrometheusQueryTemplateRegistry` — 8 query types: ERROR_RATE, LATENCY_P95, LATENCY_P99, DOWNSTREAM_LATENCY_P95, MEMORY_USAGE, CPU_USAGE, RESTART_RATE, REQUEST_RATE
- `PrometheusEvidenceMapper` — threshold-based mapping to 9 semantic evidence types
- `PrometheusEvidenceProvider` — orchestrator (client → parser → mapper)
- CLI command: `collect-prometheus-evidence` with `--service`, `--namespace`, `--query-type`, `--reader`, `--prometheus-url` flags
- 43 new tests (229 total, up from 186)

**Architecture:**
```
PrometheusEvidenceProvider (orchestrator)
  ↓
PrometheusQueryClient (SPI)
  ├── FixturePrometheusQueryClient  ← Tests / CI
  └── HttpPrometheusQueryClient     ← Production
  ↓
PrometheusResponseParser → PrometheusEvidenceMapper
  ↓
List<Evidence> (source = "prometheus")
```

**Key design invariants:**
- `sre-agent-core` has zero Prometheus dependency — the provider is a pure adapter
- Fixture-based testing — no live Prometheus required for tests or CI
- Provider outputs generic `Evidence` objects; core pipeline is data-source agnostic
- All PromQL templates are environment-specific and designed to be overridden per deployment

**Why this matters:**
- First observability signal provider beyond Kubernetes resource evidence
- Enables the agent to reason about metric anomalies (error rate spikes, latency degradation, resource saturation)
- Validates the adapter pattern for evidence providers — same `core ← provider` boundary as K8s provider
- Foundation for Step R (LLM Hypothesis Proposer) which will consume metric evidence

---

## Step N: Loki Logs Evidence Provider ✅ COMPLETED

**Status:** Completed. Loki log evidence adapter module with fixture-based testing.

**What was built:**
- `sre-agent-loki-provider` module (7th Maven module, package `ai.sreagent.loki`)
- `LokiQueryClient` interface (SPI) with `FixtureLokiQueryClient` and `HttpLokiQueryClient` implementations
- `LokiResponseParser` — handles stream results, nanosecond timestamps, error/empty results
- `LokiQueryTemplateRegistry` — 8 LogQL query types: TIMEOUT_ERROR, DOWNSTREAM_TIMEOUT, DOWNSTREAM_ERROR, EXCEPTION_LOGS, CRASH_LOGS, OOM_LOGS, DB_CONNECTION_TIMEOUT, RETRY_EXHAUSTED, HTTP_5XX_LOGS
- `LokiEvidenceMapper` — maps log patterns to 9 semantic evidence types
- `LokiEvidenceProvider` — orchestrator (client → parser → mapper)
- CLI command: `collect-loki-evidence` with `--service`, `--namespace`, `--query-type`, `--output`, `--reader`, `--loki-url` flags
- 30 new tests (263 total, up from 229)

**Architecture:**
```
LokiEvidenceProvider (orchestrator)
  ↓
LokiQueryClient (SPI)
  ├── FixtureLokiQueryClient  ← Tests / CI
  └── HttpLokiQueryClient     ← Production
  ↓
LokiResponseParser → LokiEvidenceMapper
  ↓
List<Evidence> (source = "loki")
```

**Key design invariants:**
- `sre-agent-core` has zero Loki dependency — the provider is a pure adapter
- Fixture-based testing — no live Loki required for tests or CI
- Provider outputs generic `Evidence` objects; core pipeline is data-source agnostic
- All LogQL templates are environment-specific and designed to be overridden per deployment

**Why this matters:**
- First log-based observability provider — complements Prometheus metric evidence
- Enables the agent to reason about log anomalies (timeout errors, exception bursts, OOM messages)
- Combined Prometheus + Loki evidence enables multi-signal correlation
- Foundation for Step R (LLM Hypothesis Proposer) which will consume both metric and log evidence

---

## Step O: Alertmanager Alert Evidence Provider ✅ COMPLETED

**Status:** Completed. Alertmanager alert evidence adapter module with fixture-based testing.

**What was built:**
- `sre-agent-alertmanager-provider` module (8th Maven module, package `ai.sreagent.alertmanager`)
- `AlertmanagerQueryClient` interface (SPI) with `FixtureAlertmanagerQueryClient` and `HttpAlertmanagerQueryClient` implementations
- `AlertmanagerResponseParser` — handles alert/route/silence results, status/state parsing, empty results
- `AlertmanagerEvidenceMapper` — maps alert patterns to 7 semantic evidence types (alert lifecycle, incident mapping, severity evidence)
- `AlertmanagerEvidenceProvider` — orchestrator (client → parser → mapper) producing dual output: incidents + evidence
- CLI command: `collect-alertmanager-evidence` with `--service`, `--namespace`, `--alertmanager-url`, `--reader` flags
- 45 new tests (308 total, up from 263)

**Key design invariants:**
- `sre-agent-core` has zero Alertmanager dependency — the provider is a pure adapter
- Fixture-based testing — no live Alertmanager required for tests or CI
- Provider outputs generic `Evidence` objects; core pipeline is data-source agnostic
- Dual output: both incident context and evidence objects for full RCA integration

---

## Step P: Distributed Trace Evidence Provider ✅ COMPLETED

**Status:** Completed. Distributed trace evidence adapter module with fixture-based testing.

**What was built:**
- `sre-agent-trace-provider` module (9th Maven module, package `ai.sreagent.trace`)
- `TraceQueryClient` interface (SPI) with `FixtureTraceQueryClient` and `HttpTraceQueryClient` implementations
- `TraceResponseParser` — handles trace/span results, duration parsing, error status, service dependency extraction
- `TraceQueryTemplateRegistry` — 6 query types: SLOW_SPANS, ERROR_SPANS, SERVICE_DEPENDENCY, SPAN_ERRORS_BY_SERVICE, TRACE_DURATION_HISTOGRAM, SERVICE_CALL_GRAPH
- `TraceEvidenceMapper` — maps trace patterns to 8 semantic evidence types
- `TraceEvidenceProvider` — orchestrator (client → parser → mapper)
- CLI command: `collect-trace-evidence` with `--service`, `--namespace`, `--query-type`, `--output`, `--reader`, `--trace-url` flags
- 35 new tests (343 total, up from 308)

**Architecture:**
```
TraceEvidenceProvider (orchestrator)
  ↓
TraceQueryClient (SPI)
  ├── FixtureTraceQueryClient  ← Tests / CI
  └── HttpTraceQueryClient     ← Production (Jaeger/Tempo)
  ↓
TraceResponseParser → TraceEvidenceMapper
  ↓
List<Evidence> (source = "trace")
```

**Key design invariants:**
- `sre-agent-core` has zero trace dependency — the provider is a pure adapter
- Fixture-based testing — no live Jaeger/Tempo required for tests or CI
- Provider outputs generic `Evidence` objects; core pipeline is data-source agnostic

---

## Step Q: Observability Evidence Taxonomy ✅ COMPLETED

**Status:** Completed. Provider-agnostic evidence taxonomy with category, signal, sourceKind, severity, and causalRole normalization in `sre-agent-core`.

**What was built:**
- `EvidenceCategory` enum — classifies evidence into metric, log, trace, alert, k8s_resource, deploy, topology categories
- `EvidenceSignal` enum — normalizes signal direction: spike, drop, saturation, anomaly, recovery, no_signal
- `EvidenceSourceKind` enum — classifies data source: prometheus, loki, jaeger, alertmanager, kubernetes, git, cmdb, manual
- `EvidenceSeverity` enum — normalized severity: critical, high, medium, low, info
- `CausalRole` enum — causal role in hypothesis: supporting, counter, contextual, trigger, consequence
- `EvidenceTaxonomy` record — composite taxonomy combining all classifications
- `EvidenceNormalizer` — maps raw `Evidence` to normalized `EvidenceTaxonomy`
- Provider-agnostic: all providers now produce evidence that can be classified through the same taxonomy
- Foundation for Step R (LLM Hypothesis Proposer)

**Estimated effort:** 1 day

---

## Step R: LLM Hypothesis Proposer v1 ✅ COMPLETED

**Status:** Completed. LLM-based hypothesis proposal system with strict advisory-only guardrails.

**What was built:**
- `ai.sreagent.llm.proposer` package in `sre-agent-llm` module
- `LlmHypothesisProposer` interface — SPI for LLM hypothesis proposal
- `MockLlmHypothesisProposer` — deterministic implementation for tests/CI
- `LlmHypothesisProposalPromptBuilder` — constructs evidence-aware prompts with guardrails
- `LlmProposalTriggerPolicy` — determines when to propose (competing_hypotheses, uncertain, low confidence, small score gap)
- `ProposalGuardrail` — enforces advisory-only constraints
- New models: `ProposalStatus`, `ProbeType`, `ProbeIntent`, `VerificationPlan`, `UnverifiedHypothesisProposal`, `LlmHypothesisProposalResult`
- CLI command: `propose-hypotheses --alert <path> --evidence <path> --output <path>`
- 92 new tests (435 total, up from 343, across 9 modules)

**Key architectural constraint:** LLM proposals are **advisory only** — they never change `InvestigationDecision`, `ConfidenceResult`, `VerificationResult`, and never create `Evidence`. All proposals carry `ProposalStatus.UNVERIFIED_PROPOSAL` and `canAffectDecision=false`.

**Trigger policy:**
- Only proposes when investigation is inconclusive: `competing_hypotheses`, `uncertain`, low confidence, or small score gap
- Scenario E (`competing_hypotheses`) → triggers proposals (1 proposal: `deployment_timeout_amplification`)
- Scenario F (`likely_root_cause`, high confidence) → no proposals

**Guardrails:**
- All proposals are `UNVERIFIED_PROPOSAL` — never override deterministic RCA
- `canAffectDecision=false` — proposals cannot influence the investigation decision
- Never creates `Evidence` objects — proposals are purely informational
- LLM cannot change `InvestigationDecision`, `ConfidenceResult`, or `VerificationResult`

**Test breakdown:** Core:124, LLM:51, K8s:48, Prometheus:43, Loki:30, Alertmanager:45, Trace:66, Server:23, CLI:15 (435 total, 0 failures)

**Next step:** Step V — Complex Live RCA with real observability data

---

## Step U: Instrumented Demo Services ✅ COMPLETED

**Status:** Completed. Three instrumented Spring Boot microservices (order-service, payment-service, inventory-service) deployed to kind with Prometheus metrics, fault injection, and service topology visualization.

**What was built:**
- `demo-services` Maven module with 3 instrumented Spring Boot microservices
- Kubernetes manifests: `k8s/demo-services/{order-service,payment-service,inventory-service,traffic-generator,servicemonitors}.yaml`
- Shell scripts: `scripts/demo-services/` for build, load, deploy, port-forward, check, and traffic generation
- Makefile targets: `demo-build-images`, `demo-load-images`, `demo-services-install`, `demo-services-port-forward`, `demo-services-status`, `demo-fault-payment-latency`, `demo-fault-clear`
- REST API: `GET /api/demo-services/status`, `POST /api/demo-services/fault/*`
- UI: "Demo Services" page with topology visualization, service status cards, and fault injection controls
- Fault injection: latency injection on payment-service (1500ms), with live metric evidence collection

**Architecture:**
```
Traffic Generator → order-service → payment-service
                                  → inventory-service
  ↓ (Prometheus scrapes all services)
Prometheus → Grafana → SRE Agent evidence providers
```

**Key design decisions:**
- Demo services are a separate Maven module (`demo-services`), not part of the RCA pipeline — they are infrastructure for validation
- Fault injection is runtime-controlled via REST API, not code changes
- Service topology is explicit: order-service calls both payment and inventory services
- All services expose Prometheus metrics via Micrometer (`/actuator/prometheus`)

**Why this matters:**
- Provides real microservice topology for end-to-end RCA validation
- Demonstrates fault injection without modifying the SRE agent codebase
- Bridges the gap between static fixture evidence and live observability data
- Foundation for Step V (Complex Live RCA) which will use real metric/log evidence from these services

---

## Step S: Probe Execution Framework v1 ✅ COMPLETED

**Status:** Completed. Routes LLM-generated ProbeIntents to existing evidence providers and collects new Evidence.

**What was built:**
- `sre-agent-probe-executor` module (10th Maven module, package `ai.sreagent.probe`)
- `ProbeIntentRouter` — routes ProbeType to supported providers (Prometheus, Loki, Trace, Kubernetes, Alertmanager)
- `ProbeExecutionPolicy` — enforces `canAffectDecision=false` and rejects LIVE mode (only FIXTURE supported in Step S)
- `FixtureProbeExecutor` — generates fixture Evidence per probe type using existing fixture clients
- 5 provider mappers: `PrometheusProbeMapper`, `LokiProbeMapper`, `TraceProbeMapper`, `KubernetesProbeMapper`, `AlertmanagerProbeMapper`
- CLI command: `propose-and-execute-probes`
- REST endpoint: `POST /api/investigations/scenario-e/propose-and-execute-probes`
- UI: Probe Execution card in `index.html`
- 46 new tests in probe-executor module (484 total, up from 435, 0 failures)

**Architecture:**
```
LlmHypothesisProposalResult (from Step R)
  ↓  contains List<ProbeIntent>
ProbeIntentRouter
  ↓  maps ProbeType → Provider
ProbeExecutionPolicy (canAffectDecision=false, mode=FIXTURE only)
  ↓
FixtureProbeExecutor
  ├── PrometheusProbeMapper → FixturePrometheusQueryClient
  ├── LokiProbeMapper       → FixtureLokiQueryClient
  ├── TraceProbeMapper      → FixtureTraceQueryClient
  ├── KubernetesProbeMapper → K8sFixtureLoader
  └── AlertmanagerProbeMapper → FixtureAlertmanagerQueryClient
  ↓
List<Evidence> (probe evidence, informational only)
```

**Key constraints:**
- Probe execution does **NOT** bypass Verification — probe evidence goes through the same pipeline
- Probe execution does **NOT** mutate RCA decision — collected evidence is informational only
- `canAffectDecision` is always `false` — enforced at compile time via `ProbeExecutionPolicy`
- Only FIXTURE mode supported in Step S — LIVE mode is rejected by policy
- Probe evidence is informational only — it does not automatically re-trigger investigation

**Why this matters:**
- Completes the LLM → Probe → Evidence feedback loop started in Step R
- Demonstrates principled separation: LLM proposes probes, probe executor collects evidence, but neither changes the RCA decision
- Validates that existing evidence providers (Steps M–P) can be reused for probe-driven evidence collection
- The `canAffectDecision=false` guardrail ensures the deterministic pipeline remains authoritative

**Next steps after S:**
- ~~Step T: Local observability stack on kind (Prometheus + Loki + Tempo + Alertmanager)~~ ✅ Done
- Step U: Instrumented demo services (order-service, payment-service, recommend-service)
- Step V: Complex live RCA with real observability data
- Step W: Post-probe RCA re-run policy (allowing controlled evidence injection into re-investigation)

---

## Step T: Local Observability Stack ✅ COMPLETED

**Status:** Completed. Observability status service with health checking, local stack management scripts, and Live Lab Status UI.

**What was built:**
- `scripts/observability/` — Helm values + install/uninstall/port-forward/check shell scripts
- Makefile targets: `observability-install`, `observability-uninstall`, `observability-status`, `observability-port-forward`, `observability-check`
- Backend: `EndpointHealthChecker` interface, `HttpEndpointHealthChecker`, `ObservabilityStatusService`, `ObservabilityStatusController`, DTOs, Spring config
- REST API: `GET /api/observability/status`, `POST /api/observability/check`
- UI: Live Lab Status page in `index.html` (toggle via "Lab Status" button in header)
- 23 new tests in server module (ObservabilityEndpointStatusTest, ObservabilityStatusServiceTest, ObservabilityEndpointConfigTest, HttpEndpointHealthCheckerTest)
- Total: 507 tests, 0 failures

**Architecture:**
```
ObservabilityStatusController (REST)
  ↓
ObservabilityStatusService (Spring @Service)
  ↓
EndpointHealthChecker (interface — mockable)
  └── HttpEndpointHealthChecker (HTTP health check)
  ↓
ObservabilityStatusResponse DTO (per-endpoint status)
```

**Key design decisions:**
- `EndpointHealthChecker` is an interface — easily mockable in tests, no live endpoint required
- Endpoint configuration via `application.properties` — configurable per environment
- Step T does NOT deploy demo services or fault injection — those are Step U/V concerns
- Scripts manage local observability stack lifecycle (Helm install/uninstall/port-forward)

**Why this matters:**
- Provides real-time visibility into the local observability stack (Prometheus, Loki, Tempo, Alertmanager, Grafana)
- Demonstrates Spring Boot service layer patterns (interface → implementation → controller → DTO)
- Bridges the gap between infrastructure scripts and application-level health awareness
- Foundation for Step U (instrumented demo services) which will use the same stack

---

## Beyond Step M (Longer Term)

These are not committed — listed for discussion only:

| Area | Description | Complexity |
|---|---|---|
| Multi-service correlation | Handle incidents spanning multiple services | High |
| Remediation suggestions | Not just root cause, but actionable fixes with risk assessment | Medium |
| Historical pattern matching | Compare current incident against similar past incidents | Medium |
| Slack / Teams integration | Post investigation results to incident channels | Low |
| Runbook automation | Link decision to specific runbook steps | Medium |
| AIOps benchmarking | Compare agent accuracy against human-only RCA | High |

---

## Architecture Principles for All Steps

1. **Core workflow does not change.** New functionality is added through new interfaces and implementations, not by modifying existing workflow steps.

2. **Evidence providers are pluggable.** The workflow accepts `List<Evidence>` — it doesn't care where the evidence comes from.

3. **LLM stays at the edges.** LLM consumes investigation output, never participates in the investigation.

4. **Every step is tested.** New features come with tests that verify behavior independently of the full workflow.

5. **No over-engineering.** Each step adds the minimum necessary to demonstrate the capability. The project is an MVP, not a production platform.

---

## 中文版

# 未来路线图

## 优先级取决于面试目标

| 目标岗位 | 优先步骤 | 原因 |
|---|---|---|
|| AI Agent 工程师 | ~~Steps G–J~~ ✅ 已完成 → ~~Step R~~ ✅ 已完成 → ~~Step S~~ ✅ 已完成 → ~~Step T~~ ✅ 已完成 → ~~Step U~~ ✅ 已完成 → Step V: 复杂实时 RCA | 展示了规范的 LLM + 证据 + 探测集成能力 |
|| SRE / 平台工程师 | ~~Steps H–Q~~ ✅ 已完成 → ~~Step R~~ ✅ 已完成 → ~~Step S~~ ✅ 已完成 → ~~Step T~~ ✅ 已完成 → ~~Step U~~ ✅ 已完成 → Step V: 复杂实时 RCA | 展示了完整的可观测性流水线 |
| 工程经理 / 架构师 | 完善架构叙事 | 展示了设计权衡推理能力 |

---

## Step G: LLM 报告综合 ✅ 已完成

**状态：** 已完成。详见 `docs/llm-positioning.md`。

**已构建内容：**
- `sre-agent-llm` 模块，包含 `LlmClient` 接口、`MockLlmClient`、`LlmPromptBuilder`、`LlmReportSynthesizer`
- `LlmEnhancedReport` 记录，将 `base*`（确定性）字段与 LLM 生成的字段分离
- 提示词防护机制：LLM 不能更改决策、评分、证据，也不能捏造 K8s/EC2/RDS/CMDB 事实
- REST API：`POST /api/investigations/scenario-e/llm-summary`
- UI：LLM 辅助说明部分，带有权威/建议标签和防护说明
- 23 个新测试（共 111 个）

---

## Step H: 本地 K8s 提供者搭建 ✅ 已完成

**状态：** 已完成。基于固定数据（fixture）的 K8s 证据模块——无需实时集群。

**已构建内容：**
- `sre-agent-k8s-provider` 模块（第 5 个 Maven 模块）
- 基于固定数据的 K8s 证据方案：使用真实的告警/证据 JSON 固定数据，而非实时 `kind` 集群
- 零 K8s 客户端库依赖——所有证据从 classpath 固定数据加载
- 包含：K8s 告警 JSON 固定数据、K8s 证据 JSON 固定数据（Pod 事件、容器状态、部署元数据）、K8s 固定数据加载器
- k8s 模块中 24 个测试

**设计决策：** 选择固定数据方案而非 `kind` 集群，以保持演示自包含、快速且 CI 友好。固定数据加载器的结构与真实 K8s API 客户端返回的数据一致，后续可轻松替换为实时提供者。

---

## Step I: K8s 证据提供者 ✅ 已完成

**状态：** 已完成。`pod_crash_loop` 诊断模式已接入完整 RCA 流水线。

**已构建内容：**
- `pod_crash_loop` 诊断模式（第 4 个模式，`baseScore` 0.25）
- 证据类型：`container_crash_loop_backoff`、`pod_restart_count_increased`、`pod_not_ready`、`deployment_metadata`
- 接入 `BuiltinPatterns`、`HypothesisEngine`、`VerificationEngine`
- 基于固定数据的 K8s 证据提供者（从 `sre-agent-k8s-provider` 模块加载）
- `PatternRegistryTest` 和 `HypothesisEngineTest` 已更新，共 4 个模式

**架构：**
```
InvestigationService
  ↓
K8sFixtureLoader (sre-agent-k8s-provider)
  ↓
List<Evidence> → HypothesisEngine → VerificationEngine
  ↓
ScoredHypothesis (pod_crash_loop, score=0.95, likely_root_cause)
```

**设计说明：** 遵循与现有 3 个模式相同的结构——K8s 证据类型无需特殊处理，验证了平台无关的评分流水线设计。

---

## Step I+: 多平台模型与证据提供者（未来扩展）

> **注意：** 这是超出当前范围的未来扩展。Steps H–J 已使用固定数据方案实现了 K8s 专用证据。多平台支持仍是有效的长期方向。

**目标：** 移除领域模型中以 K8s 为中心的假设，支持非 K8s 部署目标（EC2 实例、AWS 托管服务）。

**原因：** 真实的生产环境是异构的——单个事件可能涉及 K8s 部署的服务调用 RDS 数据库，背后是 ALB，使用 ElastiCache 作为缓存层。Agent 必须能够从所有这些平台收集和推理证据。

### 模型变更（前置条件）

| 记录 | 字段变更 | 理由 |
|---|---|---|
| `IncidentTask` | `namespace` → `scope` | 并非所有平台都有命名空间。使用 `scope` 表示命名空间 / 可用区 / VPC / 区域。 |
| `IncidentTask` | 新增 `platform` 字段 | `"kubernetes"` / `"ec2"` / `"managed_service"`。决定激活哪些证据提供者。 |
| `Evidence` | `service` → `entity` | RDS 实例、ElastiCache 集群、ALB——这些不是"服务"。`entity` 是平台中性的。 |
| `Evidence` | 新增 `entityType` 字段 | `"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`。支持类型感知的模式匹配。 |

**影响评估：**
- 核心流水线（VerificationEngine、ConfidenceScorer、HypothesisComparator）——**零修改**。它们仅操作 `evidenceType` 字符串。
- `HypothesisEngine`——小改：读取 `incident.service()` 获取 `affectedService`。
- Alert JSON / Evidence JSON——字段名更新。
- 测试——更新 JSON 固定数据和字段引用。
- 预计测试更新：约 15-20 个文件，机械性修改。

### 新证据提供者

| 提供者 | 数据源 | 证据类型 |
|---|---|---|
| `Ec2EvidenceProvider` | CloudWatch 指标 + EC2 API | `ec2_status_check_impaired`、`ec2_cpu_steal_high`、`ebs_iops_saturation` |
| `AwsManagedServiceEvidenceProvider` | CloudWatch + RDS/ElastiCache API | `rds_connection_exhaustion`、`rds_replica_lag_high`、`elasticache_eviction_spike`、`elasticache_replication_lag` |
| `CmdbTopologyProvider` | 内部 CMDB / 服务注册表 | `service_dependency_match`、`deployment_topology`、`service_owner`、`capacity_baseline` |

### 新诊断模式

| 模式 | 根因类型 | 关键证据 |
|---|---|---|
| `ec2_instance_degradation` | `infra_degradation` | CPU 窃取时间、EBS IOPS 饱和、状态检查异常 |
| `rds_connection_exhaustion` | `resource_exhaustion` | 达到最大连接数、连接超时激增、副本延迟 |
| `elasticache_memory_pressure` | `resource_pressure` | 驱逐率激增、交换区使用、复制延迟 |
| `managed_service_failover` | `infra_failover` | 多可用区故障转移事件、DNS 端点变更、连接重置激增 |

### 为什么这个设计可行

核心洞察：**评分流水线已经是平台无关的**。它只关心 `evidenceType` 字符串匹配和置信度权重。添加 AWS 证据类型和模式遵循与现有 K8s 模式完全相同的机制——无需特殊处理。

**预计工作量：** 3-4 天（模型变更 1 天 + 提供者 2-3 天）

---

## Step J: 将 K8s 证据接入 RCA 工作流 ✅ 已完成

**状态：** 已完成。完整的端到端 Scenario F——K8s CrashLoopBackOff 场景。

**已构建内容：**
- 新增 **Scenario F**：`recommend-service` 在 `demo` 命名空间中的 CrashLoopBackOff
- 服务端：`InvestigationService.runScenarioF()` + `POST /api/investigations/scenario-f` 端点
- CLI：Scenario F 告警 + 证据 JSON 支持
- UI：`index.html` 中的 Scenario F 按钮
- 27 个新测试（共 162 个，从 135 个增加）
- 评分：`pod_crash_loop` = 0.95，决策 = `likely_root_cause`

**为什么重要：**
- 验证 K8s 证据流水线端到端工作：告警 → 证据 → 模式匹配 → 评分假设 → 决策
- 验证基于固定数据的 K8s 提供者与现有 RCA 工作流无缝集成
- 为 K8s 原生故障模式提供了有说服力的演示场景

> **注意：** 原始的"人工反馈与置信度校准"计划已推迟至未来步骤。它仍然是有效的长期方向，但目前未优先安排。

---

## Step K: 实时 K8s / kind 集成 ✅ 已完成

**状态：** 已完成。Scenario F 已扩展，支持可选的实时 kind 演示路径。

**已构建内容：**
- CrashLoopBackOff 演示清单：`k8s/demo-services/recommend-crashloop-demo.yaml`
- 命名空间范围的只读 RBAC：`k8s/rbac/sre-agent-reader.yaml`
- Makefile 目标：`cluster-up`、`deploy-crashloop-demo`、`wait-crashloop`、`collect-k8s-evidence-live`、`investigate-k8s-live`、`clean-crashloop-demo`、`live-k8s-demo`
- 实时 K8s 演示文档：`docs/live-k8s-demo.md`
- K8s 证据提供者架构文档：`docs/k8s-evidence-provider.md`
- 更新了 `docs/LOCAL_K8S_SETUP.md`，添加了实时演示说明

**为什么重要：**
- 证明了提供者抽象不仅适用于固定数据 JSON，也能处理真实 K8s 集群数据
- 相同的 RCA 核心从固定数据和实时证据都能生成 `likely_root_cause`
- 展示了使用真实 K8s 工具的 SRE / 平台工程能力
- `mvn test` 仍独立于实时集群

**两条证据路径：**

| 路径 | 用途 | 运行方式 |
|------|------|----------|
| 固定数据（默认） | 确定性 CI / 单元测试 | `mvn test` |
| 实时 kind（可选） | 本地演示 / 平台可信度 | `make live-k8s-demo` |

---

## Step L: Kubernetes Java Client 集成 ✅ 已完成

**状态：** 已完成。生产级 Kubernetes Java Client 作为替代证据读取器。

**已构建内容：**
- 在 `sre-agent-k8s-provider` 中添加 `io.kubernetes:client-java:24.0.0` 依赖
- `JavaClientKubernetesResourceReader` — 使用官方 Java 客户端实现 `KubernetesResourceReader` SPI
- `KubernetesClientConfig` — 客户端模式、kubeconfig 路径、命名空间、超时等配置
- `KubernetesApiClientFactory` — 客户端生命周期管理（kubeconfig + 集群内模式）
- `KubernetesEvidenceCollectionException` — 证据收集异常的类型化处理
- CLI 标志：`--reader java-client`、`--client-mode <kubeconfig|in-cluster>`、`--kubeconfig <path>`
- 更新 RBAC YAML 并创建 `docs/k8s-rbac.md`
- 更新 `docs/k8s-evidence-provider.md` 添加 Java 客户端架构
- Bug 修复：`mapPodToSemanticEvidence()` 现在可从已终止状态检测 CrashLoop（不仅限于等待状态）

**为什么重要：**
- 展示了使用官方 Java 客户端库的生产级 K8s API 集成
- 同时支持 kubeconfig（本地开发）和集群内（Pod）认证模式
- 验证了 `KubernetesResourceReader` SPI 抽象——固定数据、kubectl 和 Java 客户端均产生相同的 RCA 结果
- 15 个新测试（共 186 个，从 171 个增加）
- 实时 `kind` 验证：`hyp_pod_crash_loop = 0.95`，决策 = `likely_root_cause`

**三条证据路径：**

| 路径 | 用途 | 运行方式 |
|------|------|----------|
| 固定数据（默认） | 确定性 CI / 单元测试 | `mvn test` |
| 实时 kubectl（可选） | 通过 shell 本地演示 | `make live-k8s-demo` |
| Java 客户端（可选） | 生产级 API 客户端 | `--reader java-client` |

---

## Step K+: 更多诊断模式（未来扩展）

**目标：** 将覆盖范围扩展到当前 4 个模式之外（config_drift、resource_exhaustion、dependency_failure、pod_crash_loop）。

**候选模式：**
- DNS 解析失败
- 证书过期 / TLS 握手错误
- 网络分区 / 连接丢失
- 数据库连接池耗尽
- 限流 / 节流
- 配置漂移
- 热循环 / 错误代码路径导致的 CPU 飙升
- 磁盘 I/O 饱和
- 级联故障 / 缺少熔断器

**实现模式：** 每个模式遵循相同的结构——定义证据需求、支持类型、反证类型和置信度权重。添加到 `BuiltinPatterns` 或从外部配置加载。

**预计工作量：** 每个模式 1-2 天（包括测试和证据 JSON）

---

## Step L 以后（长期方向）

这些尚未纳入计划——仅供讨论：

| 领域 | 描述 | 复杂度 |
|---|---|---|
| 多服务关联 | 处理跨多个服务的事件 | 高 |
| 修复建议 | 不仅提供根因，还提供带风险评估的可执行修复方案 | 中 |
| 历史模式匹配 | 将当前事件与类似的历史事件进行比对 | 中 |
| Slack / Teams 集成 | 将调查结果发送到事件频道 | 低 |
| Runbook 自动化 | 将决策关联到具体的 Runbook 步骤 | 中 |
| AIOps 基准测试 | 将 Agent 准确率与纯人工 RCA 进行比较 | 高 |

---

## Step R: LLM 假设提议 v1 ✅ 已完成

**状态：** 已完成。具有严格仅咨询性防护措施的 LLM 假设提议系统。

**已构建内容：**
- `ai.sreagent.llm.proposer` 包，位于 `sre-agent-llm` 模块
- `LlmHypothesisProposer` 接口 — LLM 假设提议的 SPI
- `MockLlmHypothesisProposer` — 用于测试/CI 的确定性实现
- `LlmHypothesisProposalPromptBuilder` — 构建带防护措施的证据感知提示词
- `LlmProposalTriggerPolicy` — 确定何时提议（competing_hypotheses、uncertain、低置信度、小分数差距）
- `ProposalGuardrail` — 强制执行仅咨询性约束
- 新模型：`ProposalStatus`、`ProbeType`、`ProbeIntent`、`VerificationPlan`、`UnverifiedHypothesisProposal`、`LlmHypothesisProposalResult`
- CLI 命令：`propose-hypotheses --alert <path> --evidence <path> --output <path>`
- 92 个新测试（共 435 个，从 343 个增加，跨 9 个模块）

**关键架构约束：** LLM 提议**仅限咨询性** — 它们永远不会更改 `InvestigationDecision`、`ConfidenceResult`、`VerificationResult`，也永远不会创建 `Evidence`。所有提议都带有 `ProposalStatus.UNVERIFIED_PROPOSAL` 和 `canAffectDecision=false`。

**触发策略：**
- 仅在调查不确定时提议：`competing_hypotheses`、`uncertain`、低置信度或小分数差距
- 场景 E（`competing_hypotheses`）→ 触发提议（1 个提议：`deployment_timeout_amplification`）
- 场景 F（`likely_root_cause`，高置信度）→ 无提议

**防护措施：**
- 所有提议均为 `UNVERIFIED_PROPOSAL` — 永远不覆盖确定性 RCA
- `canAffectDecision=false` — 提议不能影响调查决策
- 永远不创建 `Evidence` 对象 — 提议纯粹是信息性的
- LLM 不能更改 `InvestigationDecision`、`ConfidenceResult` 或 `VerificationResult`

**测试分布：** Core:124, LLM:51, K8s:48, Prometheus:43, Loki:30, Alertmanager:45, Trace:66, Server:23, CLI:15（共 435 个，0 失败）

**下一步：** Step U — 仪表化演示服务

---

## Step S: 探测执行框架 v1 ✅ 已完成

**状态：** 已完成。将 LLM 生成的 ProbeIntent 路由到现有证据提供者，收集新证据。

**已构建内容：**
- `sre-agent-probe-executor` 模块（第 10 个 Maven 模块，包 `ai.sreagent.probe`）
- `ProbeIntentRouter` — 将 ProbeType 路由到受支持的提供者（Prometheus、Loki、Trace、Kubernetes、Alertmanager）
- `ProbeExecutionPolicy` — 强制 `canAffectDecision=false` 并拒绝 LIVE 模式（Step S 仅支持 FIXTURE）
- `FixtureProbeExecutor` — 使用现有 fixture 客户端按探测类型生成 fixture 证据
- 5 个提供者映射器：`PrometheusProbeMapper`、`LokiProbeMapper`、`TraceProbeMapper`、`KubernetesProbeMapper`、`AlertmanagerProbeMapper`
- CLI 命令：`propose-and-execute-probes`
- REST 端点：`POST /api/investigations/scenario-e/propose-and-execute-probes`
- UI：`index.html` 中的探测执行卡片
- probe-executor 模块中 46 个新测试（共 484 个，从 435 个增加，0 失败）

**关键约束：**
- 探测执行**不会**绕过验证——探测证据通过相同流水线处理
- 探测执行**不会**变更 RCA 决策——收集的证据仅用于信息参考
- `canAffectDecision` 始终为 `false`——通过 `ProbeExecutionPolicy` 在编译时强制执行
- Step S 仅支持 FIXTURE 模式——LIVE 模式被策略拒绝
- 探测证据仅用于信息参考——不会自动重新触发调查

**重要性：**
- 完成了 Step R 开始的 LLM → 探测 → 证据反馈循环
- 展示了有原则的分离：LLM 提议探测，探测执行器收集证据，但两者都不改变 RCA 决策
- 验证了现有证据提供者（Steps M–P）可被复用于探测驱动的证据收集
- `canAffectDecision=false` 防护措施确保确定性流水线保持权威性

**S 之后的后续步骤：**
- ~~Step T：kind 上的本地可观测性栈（Prometheus + Loki + Tempo + Alertmanager）~~ ✅ 已完成
- Step U：仪表化演示服务（order-service、payment-service、recommend-service）
- Step V：使用真实可观测性数据的复杂实时 RCA
- Step W：探测后 RCA 重新运行策略（允许受控证据注入重新调查）

---

## Step T: 本地可观测性栈 ✅ 已完成

**状态：** 已完成。可观测性状态服务，包含健康检查、本地栈管理脚本和 Live Lab Status UI。

**已构建内容：**
- `scripts/observability/` — Helm values + install/uninstall/port-forward/check shell 脚本
- Makefile 目标：`observability-install`、`observability-uninstall`、`observability-status`、`observability-port-forward`、`observability-check`
- 后端：`EndpointHealthChecker` 接口、`HttpEndpointHealthChecker`、`ObservabilityStatusService`、`ObservabilityStatusController`、DTOs、Spring 配置
- REST API：`GET /api/observability/status`、`POST /api/observability/check`
- UI：`index.html` 中的 Live Lab Status 页面（通过头部 "Lab Status" 按钮切换）
- server 模块中 23 个新测试（ObservabilityEndpointStatusTest、ObservabilityStatusServiceTest、ObservabilityEndpointConfigTest、HttpEndpointHealthCheckerTest）
- 总计：507 个测试，0 失败

**架构：**
```
ObservabilityStatusController (REST)
  ↓
ObservabilityStatusService (Spring @Service)
  ↓
EndpointHealthChecker (接口 — 可 mock)
  └── HttpEndpointHealthChecker (HTTP 健康检查)
  ↓
ObservabilityStatusResponse DTO (每个端点的状态)
```

**关键设计决策：**
- `EndpointHealthChecker` 是一个接口 — 测试中易于 mock，无需实时端点
- 端点配置通过 `application.properties` — 可按环境配置
- Step T 不部署演示服务或故障注入 — 这些属于 Step U/V 的范围
- 脚本管理本地可观测性栈的生命周期（Helm install/uninstall/port-forward）

**重要性：**
- 提供对本地可观测性栈（Prometheus、Loki、Tempo、Alertmanager、Grafana）的实时可见性
- 展示了 Spring Boot 服务层模式（接口 → 实现 → 控制器 → DTO）
- 连接基础设施脚本和应用层健康感知的桥梁
- 为 Step U（仪表化演示服务）奠定基础

---

## Step U: 仪表化演示服务 ✅ 已完成

**状态：** 已完成。三个仪表化的 Spring Boot 微服务（order-service、payment-service、inventory-service）部署到 kind，支持 Prometheus 指标、故障注入和服务拓扑可视化。

**已构建内容：**
- `demo-services` Maven 模块，包含 3 个仪表化的 Spring Boot 微服务
- Kubernetes 清单：`k8s/demo-services/{order-service,payment-service,inventory-service,traffic-generator,servicemonitors}.yaml`
- Shell 脚本：`scripts/demo-services/`，用于构建、加载、部署、端口转发、检查和流量生成
- Makefile 目标：`demo-build-images`、`demo-load-images`、`demo-services-install`、`demo-services-port-forward`、`demo-services-status`、`demo-fault-payment-latency`、`demo-fault-clear`
- REST API：`GET /api/demo-services/status`、`POST /api/demo-services/fault/*`
- UI："Demo Services" 页面，包含拓扑可视化、服务状态卡片和故障注入控制
- 故障注入：在 payment-service 上注入延迟（1500ms），支持实时指标证据收集

**架构：**
```
Traffic Generator → order-service → payment-service
                                  → inventory-service
  ↓（Prometheus 抓取所有服务）
Prometheus → Grafana → SRE Agent 证据提供者
```

**关键设计决策：**
- 演示服务是独立的 Maven 模块（`demo-services`），不属于 RCA 管道 — 它们是验证用的基础设施
- 故障注入通过 REST API 运行时控制，无需代码变更
- 服务拓扑明确：order-service 同时调用 payment 和 inventory 服务
- 所有服务通过 Micrometer 暴露 Prometheus 指标（`/actuator/prometheus`）

**重要性：**
- 为端到端 RCA 验证提供真实的微服务拓扑
- 演示了无需修改 SRE agent 代码库的故障注入
- 连接了静态 fixture 证据和实时可观测性数据之间的鸿沟
- 为 Step V（复杂实时 RCA）奠定基础，后者将使用来自这些服务的真实指标/日志证据

---

## 所有步骤的架构原则

1. **核心工作流不变。** 新功能通过新接口和实现添加，而不是修改现有工作流步骤。

2. **证据提供者可插拔。** 工作流接受 `List<Evidence>`——它不关心证据来自哪里。

3. **LLM 保持在边缘。** LLM 消费调查输出，永远不参与调查过程。

4. **每一步都有测试。** 新功能附带独立验证行为的测试，不依赖完整工作流。

5. **不过度工程。** 每一步只添加展示能力所需的最小内容。这是一个 MVP，不是生产平台。
