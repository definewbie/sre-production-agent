# Interview Q&A

## Q1: How is this different from a log chatbot?

A log chatbot takes unstructured logs and asks an LLM "what went wrong." It's a single-shot inference with no verification, no structured evidence, and no audit trail.

This agent follows a structured workflow: collect evidence → generate hypotheses → verify each one → score confidence → compare → decide. Every step is recorded. Every score is traceable. When the evidence doesn't support a single conclusion, the agent says so explicitly.

The difference is: **a chatbot guesses, this agent investigates.**

---

## Q2: Why not let the LLM directly infer root cause?

Three reasons:

1. **Hallucination risk.** LLMs can generate plausible but wrong root causes. In SRE, a wrong root cause can lead to incorrect remediation — like rolling back the wrong deployment or restarting the wrong service.

2. **Non-determinism.** Same alert + same evidence should produce the same conclusion every time. LLM output varies between runs. On-call engineers need reproducible results.

3. **Unauditability.** You can't trace why an LLM picked root cause A over B. In this agent, you can see exactly which evidence supported each hypothesis and what the confidence weights are.

The LLM will be added later — for report synthesis only, not for decision-making.

---

## Q3: Where does the confidence score come from?

The `ConfidenceScorer` uses a deterministic formula:

```
rawScore = pattern.baseScore
         + Σ(weight for matched supporting evidence types)
         - Σ(|weight| for matched counter evidence types)
         - missingPenalty
```

The weights are defined in each `DiagnosticPattern`. For example, the deployment regression pattern gives `deploy_event_near_alert_window` a weight of 0.12 and `retry_timeout_config_change` a weight of 0.12.

These weights are **manually assigned based on SRE diagnostic experience**, not learned from data. The value is that every score is explainable — you can trace exactly why deployment regression scored 0.64.

---

## Q4: Are the weights learned from historical incidents?

No. In the MVP, weights are manually assigned based on SRE domain knowledge. This is clearly documented as a limitation.

Production evolution paths:
- Start with manual weights (current state)
- Add human feedback after each investigation (calibration loop)
- Eventually learn weights from historical incident data with label propagation
- Or use LLM-assisted weight suggestion with human approval

The architecture already supports this — weights are stored in `DiagnosticPattern.confidenceWeights()`, which can be loaded from a database or configuration file instead of hardcoded.

---

## Q5: Why use Java and Spring Boot instead of Python?

Four reasons:

1. **Enterprise SRE context.** Most large-scale SRE platforms (at companies running Kubernetes at scale) are built in Java or Go. Spring Boot is the dominant framework for enterprise Java.

2. **Type safety.** Java records give me immutable domain objects with compile-time type checking. In a system where wrong conclusions have operational impact, type safety matters.

3. **Module architecture.** Maven multi-module cleanly enforces the zero-Spring-dependency constraint on core. Python's packaging model makes this boundary harder to enforce.

4. **Interview signal.** For SRE and platform engineering roles at companies using Java, this demonstrates hands-on Java 21, Spring Boot 3.x, and Maven multi-module skills.

---

## Q6: Why keep core zero-Spring?

Three reasons:

1. **Test speed.** Core unit tests run in milliseconds because there's no Spring context to start. The server integration tests that do use Spring take longer. Separating them means I can run 70+ core tests instantly.

2. **Reusability.** The same `InvestigationWorkflow` runs in CLI and server. In the future, it could run in a Lambda function, a Kafka consumer, or a K8s operator — without pulling in Spring Boot.

3. **Architecture clarity.** The zero-Spring constraint forces a clean boundary. Domain logic lives in core. Framework glue lives in adapters. This is hexagonal architecture in practice.

---

## Q7: Why does Scenario E output `competing_hypotheses`?

Because the evidence doesn't clearly point to a single root cause.

- Deployment regression scores 0.64 — supported by the deploy event, error spike, timeout logs, and a config change in git.
- Downstream dependency latency scores 0.58 — supported by timeout logs, latency spike, and service topology.
- The score gap is only 0.06 — well below the 0.10 threshold for a decisive conclusion.

The agent's decision policy says: if both top hypotheses score above 0.50 and the gap is below 0.10, output `competing_hypotheses`.

**Forcing a single answer when the evidence is ambiguous would be worse than admitting uncertainty.** In a real incident, this tells the on-call engineer: "investigate both — compare timeout errors before and after deployment, and check payment-service latency by endpoint."

---

## Q8: What happens when evidence contradicts itself?

The `VerificationEngine` explicitly handles contradictions. Each `VerificationResult` has:
- `supportingEvidenceIds` — evidence that supports the hypothesis
- `counterEvidenceIds` — evidence that contradicts the hypothesis
- `missingEvidence` — expected evidence types that are absent
- `contradictions` — human-readable descriptions of conflicting evidence

In Scenario E, deployment regression has a contradiction: "Timeout logs existed before the deployment, so the deployment may not be the only cause." This contradiction reduces the confidence score via counter evidence weights.

The `MarkdownReporter` includes a **Contradictions** section in the report, so the on-call engineer can see exactly where evidence conflicts.

---

## Q9: How would this connect to real Prometheus / Loki / Kubernetes?

The current `StaticEvidenceProvider` loads evidence from JSON files. The extension path is:

1. **Define an `EvidenceProvider` interface** in core:
   ```java
   public interface EvidenceProvider {
       List<Evidence> collect(IncidentTask incident);
   }
   ```

2. **Implement providers:**
   - `PrometheusEvidenceProvider` — queries Prometheus API for metric-based evidence
   - `LokiEvidenceProvider` — queries Loki for log-based evidence
   - `KubernetesEvidenceProvider` — queries K8s API for deployment events, pod status, resource metrics

3. **Compose providers:** `CompositeEvidenceProvider` calls multiple providers and merges results

4. **Configuration-driven:** Which providers to use would be configured per environment

The core workflow doesn't change — `InvestigationWorkflow.run()` already accepts a `List<Evidence>`. The providers just replace the JSON file loading.

---

## Q10: When would you add LLM?

**Already done.** Phase 4 (commit `cdaecac`) integrates a real LLM via `OpenAiCompatibleLlmClient`.

The original sequence was:
1. First, connect real evidence providers (Prometheus, Loki, K8s) — Step H/I ✅
2. Validate that the scoring and decision logic produces correct results on real incidents ✅
3. Then add LLM for report synthesis + hypothesis proposal — Step G / Phase 4 ✅

The reason for this order: **you need to trust the structured output before you let an LLM narrate it.** If the scoring is wrong, a well-written LLM report just makes wrong conclusions sound more convincing.

The LLM is configured via environment variables: `LLM_PROVIDER=openai`, `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`. It's been E2E validated with Zhipu glm-4-flash (65s, 87KB Chinese report).

---

## Q11: What would the LLM be allowed to do?

The LLM now handles two roles, both strictly advisory:

1. **Hypothesis Proposal** (`LlmHypothesisProposerImpl`) — Given the evidence context, the LLM proposes additional root cause hypotheses that the deterministic `DiagnosticPattern` engine may not cover. These proposals are marked `advisoryOnly=true` and `canAffectDecision=false`.

2. **Report Synthesis** (`LlmReportSynthesizer`) — Synthesizes the structured `InvestigationResult` into a human-readable narrative with:
   - Executive summary
   - Reasoning narrative
   - Uncertainty assessment
   - Next steps
   - Limitations

Additional capabilities:
- Suggest remediation actions based on the decision and next probes
- Summarize contradictions in plain language
- Translate technical findings for non-technical stakeholders

The LLM's input contract is the `InvestigationResult` record — it contains everything the LLM needs, and nothing it shouldn't modify. The `OpenAiCompatibleLlmClient` supports any OpenAI-compatible endpoint (Zhipu, OpenAI, Ollama, vLLM) via environment variables.

---

## Q12: What would the LLM NOT be allowed to do?

- **Decide root cause** — that's the `HypothesisComparator`'s job
- **Modify confidence scores** — scores are deterministic
- **Invent evidence** — evidence comes from providers, not LLM
- **Override InvestigationDecision** — the decision is the output of the workflow
- **Skip verification** — every hypothesis must be verified before scoring

These are guardrails. The architecture enforces them by design — the LLM consumes the investigation output, it doesn't participate in the investigation.

---

## Q13: How do you prevent hardcoded demo logic?

Three mechanisms:

1. **Pattern-driven hypothesis generation.** `HypothesisEngine` generates one hypothesis per `DiagnosticPattern`. Adding a new pattern automatically adds a new hypothesis. No manual wiring.

2. **Evidence-driven verification.** `VerificationEngine` classifies evidence by type, not by hardcoded IDs. If you swap the evidence JSON, the verification results change accordingly.

3. **Test coverage.** The 88 tests cover the full workflow with specific assertions on scores, decisions, and evidence classification. If someone hardcodes a result, the tests would fail for different input data.

The demo uses static JSON files because there's no real Prometheus/Loki yet. But the workflow code doesn't know or care where the evidence comes from.

---

## Q14: What is the value of Event Trace?

Four things:

1. **Auditability.** Every step is recorded with a timestamp and payload. You can see exactly what happened, in what order, with what data.

2. **Debuggability.** If the agent outputs a surprising decision, the trace shows where the scoring diverged. "Oh, the counter evidence for downstream latency included the deploy event — that's why it scored lower."

3. **Handoff.** When one on-call engineer runs an investigation and hands off to another, the trace tells the complete story without verbal explanation.

4. **Calibration data.** Over time, event traces from many investigations become a dataset for calibrating confidence weights and evaluating decision accuracy.

---

## Q15: How would this evolve into a production system?

In priority order:

1. **Real evidence providers** — connect Prometheus, Loki, K8s API
2. **Persistent store** — replace in-memory store with a database
3. **LLM report synthesis** — add natural language narrative
4. **Human feedback loop** — on-call engineers confirm or correct decisions, feeding back into weight calibration
5. **More diagnostic patterns** — cover more failure modes (network partition, certificate expiry, DNS issues, etc.)
6. **Multi-service correlation** — handle incidents that span multiple services
7. **Remediation suggestions** — not just root cause analysis, but actionable fixes with risk assessment

The architecture supports all of these without modifying the core workflow — they're all extensions at the edges.

---

## Q16: What if the application is not deployed on Kubernetes? For example, EC2 instances, RDS, ElastiCache?

Good question. The current MVP is K8s-centric in naming (`IncidentTask.namespace`, `Evidence.service`, `pod_oom_killed` pattern), but the **core pipeline is already platform-agnostic**.

Here's why: `VerificationEngine` and `ConfidenceScorer` only match on `evidenceType` strings. They don't inspect `service`, `namespace`, or any K8s-specific fields. So adding EC2, RDS, or ElastiCache evidence types works through the exact same mechanism — define new `evidenceType` strings, new patterns, and new providers.

**What needs to change:**

| Change | Why |
|---|---|
| `IncidentTask.namespace` → `scope` | EC2 has AZ/VPC, not namespace. RDS has region. |
| Add `IncidentTask.platform` | The agent needs to know whether it's investigating K8s, EC2, or a managed service. |
| `Evidence.service` → `entity` | An RDS instance or ElastiCache cluster is not a "service". |
| Add `Evidence.entityType` | Enables type-aware reasoning — distinguish a database from a cache from a load balancer. |

**The core scoring pipeline requires zero changes.** That's the architectural payoff of matching on `evidenceType` strings instead of platform-specific fields.

---

## Q17: How would you get service topology and deployment info — from CMDB or static JSON?

For the MVP, it's static JSON — the `service_dependency_match` evidence type in Scenario E has a hardcoded `call_path` attribute.

For production, the right answer is **both, with CMDB as the primary source**:

1. **CMDB / service registry** — the source of truth for:
   - Service dependency topology (who calls whom, sync vs async)
   - Deployment topology (which service runs where — K8s cluster, EC2 ASG, ECS task)
   - Service owner and on-call rotation
   - Capacity baselines (is this service at its normal load?)
   - Change records (not just git commits, but human-initiated changes)

2. **Real-time discovery** as a supplement:
   - Service mesh telemetry (Istio/Envoy, AWS App Mesh) for actual call paths
   - Distributed tracing (Jaeger, X-Ray) for observed dependencies

3. **JSON mock for testing** — the current approach becomes the test fixture format, not the production path.

The `EvidenceProvider` interface handles this cleanly — `CmdbTopologyProvider` implements the same interface as `StaticEvidenceProvider`. The workflow doesn't care where the topology evidence comes from.

---

## Q18: How do you handle incidents that span K8s and non-K8s infrastructure?

This is a realistic scenario — order-service on K8s calls RDS for persistence and ElastiCache for session cache. An incident could involve all three.

**The key design decision:** `entityType` on each Evidence record tells the agent what kind of resource it's dealing with. The `platform` field on `IncidentTask` determines which evidence providers to activate.

```
CompositeEvidenceProvider
  ├── KubernetesEvidenceProvider   → pod events, resource metrics
  ├── Ec2EvidenceProvider          → instance status, CPU steal
  ├── AwsManagedServiceEvidenceProvider → RDS, ElastiCache metrics
  └── CmdbTopologyProvider         → cross-platform dependency map
  ↓
Merged List<Evidence> with entityType on each item
```

Each piece of evidence is tagged with its `entityType` (`"service"`, `"database"`, `"cache"`), so patterns can reason across platform boundaries. For example, an `rds_connection_exhaustion` pattern would look for evidence from both the calling service (timeout logs) and the database (max_connections reached) — regardless of what platform each runs on.

**This is why the `evidenceType` matching design matters.** If the pipeline were tied to K8s-specific fields, cross-platform reasoning would require special casing. With `evidenceType` strings, it just works.

---

## Q19: What does Step G add and why is LLM advisory-only?

Step G adds an LLM integration layer with four components: `LlmClient` (interface), `MockLlmClient` (no-op implementation), `LlmPromptBuilder` (system prompt with guardrails), and `LlmReportSynthesizer` (orchestrates the call). The LLM produces a natural-language narrative summary and suggested remediation — it does **not** touch the decision, confidence scores, or evidence.

The LLM is advisory-only because the deterministic pipeline already produces verified, auditable results. Letting an LLM modify scores or decisions would introduce non-determinism and hallucination risk into the investigation — exactly the problems this agent was designed to avoid. The LLM enhances presentation; it does not participate in reasoning.

---

## Q20: How does the LLM prompt contract enforce guardrails?

`LlmPromptBuilder` constructs a system prompt that explicitly constrains the LLM:

- It must **only** summarize the provided `InvestigationResult` — no inventing evidence, no second-guessing scores, no overriding the decision.
- It must label its output as advisory.
- It receives the full structured result (hypotheses, scores, evidence, decision, contradictions) as context, so it has everything it needs without needing to infer.

The contract is enforced by architecture: the LLM never sees the raw evidence or the workflow internals — only the final `InvestigationResult` record. It cannot call back into the system. Even if the LLM hallucinated a different decision in its narrative, the structured `InvestigationResult.decision()` remains the authoritative answer.

---

## Q21: What is the MockLlmClient and why does it exist?

`MockLlmClient` is the default implementation of `LlmClient`. It returns a canned response string without calling any external API. It exists so the system runs end-to-end without requiring an API key, network access, or LLM provider configuration.

This matters for three reasons:

1. **Development speed** — the full pipeline including UI renders correctly without external dependencies.
2. **CI/CD** — tests and builds pass without managing secrets or rate limits.
3. **Demo readiness** — anyone can clone and run the project immediately.

Swapping in a real provider is a single Spring `@Profile` or configuration switch — no code changes to the synthesizer or workflow.

---

## Q22: How would you swap in a real OpenAI-compatible LLM?

**Already implemented.** `OpenAiCompatibleLlmClient` is the real implementation, activated via environment variables:

```bash
export LLM_PROVIDER=openai
export LLM_API_KEY=your-api-key
export LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4  # Zhipu
export LLM_MODEL=glm-4-flash
```

The client features smart endpoint URL construction:
- If base URL ends with `/chat/completions` → use directly
- If base URL contains `/v4` or `/v3` → append `/chat/completions` (Zhipu-style)
- Otherwise → append `/v1/chat/completions` (standard OpenAI-style)

This makes it compatible with Zhipu, OpenAI, Azure OpenAI, Ollama, and vLLM without code changes. The `MockLlmClient` activates when `LLM_PROVIDER` is unset or set to `mock`, ensuring all 1194 tests remain deterministic.

**E2E verified:** Zhipu glm-4-flash returns 87KB Chinese LLM-enhanced report in ~65s via `GET /api/live-scenario/simulate?runLlm=true`.

---

## Q23: What is LlmEnhancedReport and why does it carry both `base*` and LLM fields?

`LlmEnhancedReport` is a record that wraps the original `InvestigationResult` alongside the LLM-generated additions. It carries:

- **`baseResult`** — the full `InvestigationResult` (decision, hypotheses, scores, evidence, contradictions, trace). This is the authoritative, deterministic output.
- **LLM fields** — `llmNarrative` (natural-language summary), `llmRemediation` (suggested actions), `llmProvider` (which provider was used, e.g. "Mock Provider").

Carrying both in one object gives the UI and API consumers a single response envelope. The separation makes it clear which fields are deterministic and which are advisory — you can always ignore the LLM fields and rely entirely on `baseResult`.

---

## Q24: How does the UI distinguish deterministic vs LLM-assisted results?

The UI applies distinct visual badges:

- **Authoritative** badge — shown on the deterministic section (decision, scores, evidence, hypotheses). This content is produced by the verified pipeline and is fully auditable.
- **Advisory Only** badge — shown on the LLM narrative and remediation section. This signals that the content is AI-generated and should be treated as suggestions, not conclusions.
- **Mock Provider** badge — shown when `MockLlmClient` is active, so reviewers know the LLM text is a placeholder, not real AI output.

A guardrail notice is also displayed at the bottom of the LLM section, reiterating that the LLM cannot modify decisions or scores. This three-badge approach ensures that no one reading the report confuses synthesized text for verified findings.

---

## Q25: What is the Probe Execution Framework and what guardrails does it enforce?

Step S introduces the Probe Execution Framework (`sre-agent-probe-executor`). After the LLM Hypothesis Proposer generates hypotheses, it also emits **ProbeIntents** — structured suggestions like "check p95 latency" or "check error rate". The probe executor routes these intents to the appropriate evidence providers (Prometheus, Loki, Trace, Kubernetes, Alertmanager) and collects the resulting Evidence.

**Key guardrails:**
- `canAffectDecision` is always `false` — probe evidence is informational only and cannot change the RCA decision.
- This is enforced at **compile time** via the `ProbeExecutionResult` constructor, which throws `IllegalArgumentException` if `canAffectDecision=true`.
- Only `FIXTURE` mode is supported in Step S — no live backend probes.
- `ProbeExecutionPolicy` validates every plan before execution: max probes limit, mode check, canAffectDecision check.

---

## Q26: Why is probe execution in a separate module from LLM hypothesis generation?

Separation of concerns:
- `sre-agent-llm` owns hypothesis generation — it proposes *what to investigate*.
- `sre-agent-probe-executor` owns *how to investigate* — it routes intents, executes against providers, and collects Evidence.
- The core RCA pipeline (`sre-agent-core`) remains completely unaware of probes.

This means you can swap or upgrade the probe execution logic without touching the LLM module, and vice versa. It also keeps `sre-agent-core` dependency-free (zero LLM, zero Spring, zero provider-specific imports).

---

## Q27: What is the `canAffectDecision` guardrail and why is it compile-time enforced?

Probe evidence supplements the RCA but must never override the deterministic pipeline's conclusion. If probe evidence could flip a decision, the entire auditability guarantee breaks — an engineer would not be able to reproduce the decision from the original evidence alone.

The guardrail is compile-time enforced because runtime checks can be bypassed or forgotten. The `ProbeExecutionResult` record constructor rejects `canAffectDecision=true` with an `IllegalArgumentException`, ensuring no code path — intentional or accidental — can create a result that claims to affect the decision.

In future Step W (post-probe RCA re-run policy), this will be relaxed under strict conditions, but for Step S the answer is: probes observe, they do not decide.

---

## Q28: How does the Observability Status Service check endpoint health without requiring live endpoints?

The `EndpointHealthChecker` is an **interface** with a single method: `check(url) → EndpointStatus`. The default implementation (`HttpEndpointHealthChecker`) makes HTTP GET requests to check connectivity and measure latency. In tests, this interface is mocked — no live Prometheus, Loki, or Tempo instance is needed.

This follows the same pattern as `LlmClient` / `MockLlmClient`: the interface defines the contract, and a test-friendly implementation is always available. The 23 new tests (ObservabilityEndpointStatusTest, ObservabilityStatusServiceTest, ObservabilityEndpointConfigTest, HttpEndpointHealthCheckerTest) all use mocked health checkers, so `mvn test` passes without any observability stack running.

The endpoint URLs themselves come from `application.properties`, not from hardcoded constants. This means different environments (local kind, staging, production) can configure different endpoint lists without code changes.

---

## Q29: Why is `EndpointHealthChecker` an interface rather than just using `RestTemplate` directly?

Three reasons:

1. **Testability.** Making HTTP calls in unit tests means either starting real servers or using mock HTTP servers. By extracting an interface, tests inject a mock that returns predetermined results instantly. No network, no timeouts, no flaky tests.

2. **Extensibility.** Not every observability endpoint speaks HTTP the same way. Prometheus has `/-/healthy`, Grafana has `/api/health`, and some systems might need gRPC or TCP checks. The interface allows different implementations per endpoint type without changing the service layer.

3. **Consistent with project patterns.** Every external dependency in this project is behind an interface: `LlmClient`, `PrometheusQueryClient`, `LokiQueryClient`, `TraceQueryClient`, `KubernetesResourceReader`. `EndpointHealthChecker` follows the same hexagonal architecture principle — infrastructure concerns are behind SPI boundaries, not embedded in business logic.

---

## Q30: What does the Live Lab Status page show and why does it matter for a demo?

The Live Lab Status page (toggled via the "Lab Status" button in the header) shows real-time health status of all configured observability endpoints — Prometheus, Loki, Tempo, Alertmanager, and Grafana. For each endpoint it displays: name, URL, status (UP/DOWN/UNKNOWN), response latency, and last-checked timestamp.

This matters for three reasons:

1. **Demo credibility.** When demonstrating the SRE agent to interviewers, being able to show "all observability endpoints are running and healthy" is a concrete signal that this isn't just a toy project — there's real infrastructure behind it.

2. **Developer experience.** During local development, the page provides at-a-glance confirmation that the observability stack is up before running scenarios. If Prometheus is down, you see it immediately rather than getting cryptic connection errors later.

3. **Bridges infrastructure and application.** Step T's scripts (`scripts/observability/`) manage the infrastructure (Helm install, port-forward), and the status page reflects that infrastructure state back into the application. This demonstrates the full-stack thinking that SRE and platform engineering roles require.

**Important:** Step T does NOT deploy demo services or inject faults — that's Step U and Step V. Step T focuses purely on observability stack lifecycle management and health visibility.

---

## Q31: Why is `demo-services` a separate Maven module instead of part of `sre-agent-server`?

Three reasons:

1. **Separation of concerns.** `demo-services` are target services that the SRE agent investigates — they are NOT part of the RCA pipeline. Mixing them into `sre-agent-server` would conflate "the system being monitored" with "the system doing the monitoring," violating the hexagonal architecture boundary.

2. **Independent lifecycle.** Demo services are built into Docker images and deployed to a kind cluster. The SRE agent runs as a Java process. They have completely different build, test, and deploy cycles. A separate Maven module means `mvn -pl demo-services package docker:build` doesn't trigger the entire agent build.

3. **Interview signal.** This demonstrates understanding that infrastructure/validate tooling should be isolated from production code. In real SRE orgs, synthetic test services and canary deployments follow the same pattern — separate repositories or modules.

---

## Q32: How does fault injection work in the demo services, and why is it runtime-controlled?

Fault injection is triggered via REST API calls to the SRE agent server:

- `POST /api/demo-services/fault/payment-latency` — injects 1500ms artificial latency into the `payment-service` endpoint
- `POST /api/demo-services/fault/clear` — clears all injected faults

Internally, the `payment-service` has a `FaultInjectionController` that toggles a thread-local boolean. When the fault flag is active, the service's `PaymentController` adds a `Thread.sleep()` before processing. Prometheus scrapes the elevated latency, and the SRE agent's `PrometheusEvidenceProvider` picks it up as real metric evidence.

It's runtime-controlled (not code-change-based) because:

1. **Demo flow.** During an interview demo, you can show normal traffic → inject fault → agent detects anomaly → clear fault → recovery, all without restarting anything.
2. **Repeatability.** Tests can inject and clear faults programmatically, enabling deterministic end-to-end RCA validation.
3. **No source modification.** The agent codebase stays untouched — fault injection is an external stimulus, just like a real production incident.

---

## Q33: How do the demo services connect to the SRE agent's evidence pipeline?

The demo services form a three-tier microservice topology: `order-service` calls both `payment-service` and `inventory-service`. All three expose Micrometer metrics at `/actuator/prometheus`, which are scraped by the Prometheus instance running in the kind cluster.

The connection works in three steps:

1. **Metrics collection.** Prometheus scrapes all demo services every 15 seconds. When `payment-service` has injected latency, the `http_server_requests_seconds` metric shows elevated p99 values.

2. **Evidence extraction.** The SRE agent's `PrometheusEvidenceProvider` queries Prometheus (via `PrometheusQueryClient`) using PromQL like `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{service="payment-service"}[5m]))`. This returns real metric evidence — not fixtures.

3. **RCA pipeline.** The real metric evidence enters the standard `InvestigationWorkflow`: collected → scored against diagnostic patterns → compared → decision made. The agent doesn't know or care that the metrics came from demo services vs. production services — the `EvidenceProvider` interface abstracts the source.

This is the critical bridge: Step T provided the observability stack (Prometheus, Grafana), and Step U provides the target services that generate observable telemetry. Together they enable end-to-end RCA validation with real metrics, not just static JSON fixtures.

---

## Q34: How does the real LLM integration work?

Phase 4 added `OpenAiCompatibleLlmClient` — a production-ready LLM client that connects to any OpenAI-compatible endpoint via environment variables (`LLM_PROVIDER`, `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). It features smart URL construction that handles Zhipu, OpenAI, Azure, Ollama, and vLLM endpoint differences automatically.

The integration has two paths:
1. **LLM Proposal** (`LlmHypothesisProposerImpl`) — The LLM proposes additional root cause hypotheses based on collected evidence. These proposals are marked `advisoryOnly=true` and `canAffectDecision=false` — they cannot influence the deterministic decision.
2. **Report Synthesis** (`LlmReportSynthesizer`) — The LLM generates a structured narrative report (executive summary, reasoning, uncertainty, next steps, limitations) from the deterministic `InvestigationResult`.

If the LLM is unavailable or fails, both paths fall back to `MockLlmClient` behavior automatically.

---

## Q35: What happens when the LLM is unavailable?

The system is designed to be fully functional without LLM:

1. **Auto-fallback.** If `LLM_PROVIDER` is unset, or if the real LLM client throws an exception, `LlmHypothesisProposerImpl` catches the failure and returns an empty proposal list. The investigation proceeds with only deterministic hypotheses.
2. **MockLlmClient for tests.** All 1194 tests use `MockLlmClient` — zero external API dependencies, fully deterministic and repeatable.
3. **Graceful degradation.** The API response includes an `llmProposal` field that's either populated or null. The UI shows "LLM unavailable" rather than crashing.

The key design principle: **the deterministic engine always works, LLM is a bonus.**

---

## Q36: How do you validate that LLM output doesn't corrupt the investigation?

Three guardrails, enforced at different levels:

1. **Compile-time.** `canAffectDecision` is a `boolean` field on `UnverifiedHypothesisProposal` — it's always `false` for LLM proposals, and there's no code path that can set it to `true` for advisory hypotheses.
2. **Runtime.** The `HypothesisComparator` ignores `advisoryOnly=true` proposals when computing the final `InvestigationDecision`. Only deterministic hypotheses influence the decision.
3. **Structural.** `LlmEnhancedReport` strictly separates `base*` fields (deterministic engine output) from LLM fields (advisory content). The API consumer can always ignore LLM fields entirely.

---

## Q37: How do you prevent false positives from Kubernetes evidence?

False positives in RCA ranking often come from imprecise evidence typing. In V.2-UI-4.1, I encountered a real case: `pod_crash_loop` ranked #1 (score 0.25) in a **latency scenario** where it should have ranked last.

The root causes were:
1. **Generic event mapping.** All K8s events were mapped to a single `k8s_event` type, losing semantic signal.
2. **Loose crash loop detection.** `container_crash_loop_backoff` triggered on any pod with restartCount > 0, not just actual CrashLoopBackOff status.
3. **Missing counter signals.** Healthy pods produced no counter-balancing evidence.

The fix was a three-layer approach:

1. **Semantic event mapping.** `KubernetesEvidenceMapper.mapEventsToSemanticEvidence()` now classifies events by reason — `k8s_event_unhealthy`, `k8s_event_failed_scheduling`, `k8s_event_killing`, `k8s_event_normal` — instead of generic `k8s_event`.

2. **Precise anomaly detection.** `container_crash_loop_backoff` only fires when `pod.status.reason == "CrashLoopBackOff"`. `pod_not_ready` requires actual readiness failure, not just non-zero restart count.

3. **Counter evidence for healthy pods.** `k8s_runtime_healthy` and `pod_ready` provide counter-balancing signals. `VerificationEngine` filters non-diagnostic types (NONE, k8s_no_signal, k8s_runtime_healthy, restart_count_observed) from scoring.

**Live E2E result:** After the fix, `downstream_dependency_latency` ranked #1 (0.50) and `pod_crash_loop` dropped to #4 (0.09) in the latency scenario. The fix was verified against a real Kind cluster with live Prometheus/Loki/K8s data.

This demonstrates a key SRE principle: **evidence quality determines RCA quality.** No amount of scoring algorithm sophistication can compensate for imprecise evidence classification.

---

## 中文版

## Q1: 这和一个日志聊天机器人有什么区别？

日志聊天机器人接收非结构化的日志，然后问 LLM "出了什么问题"。这是一个单次推理，没有验证、没有结构化证据、也没有审计追踪。

本 Agent 遵循结构化的工作流：收集证据 → 生成假设 → 逐一验证 → 评分 confidence score → 比较 → 决策。每一步都有记录，每个分数都可追溯。当证据不足以支持唯一结论时，Agent 会明确说明。

区别在于：**聊天机器人靠猜，本 Agent 靠调查。**

---

## Q2: 为什么不让 LLM 直接推断根因？

三个原因：

1. **幻觉风险。** LLM 可能生成看似合理但错误的根因。在 SRE 场景中，错误的根因会导致错误的修复操作——比如回滚错误的部署或重启错误的服务。

2. **非确定性。** 相同的告警 + 相同的证据，每次都应该得出相同的结论。LLM 的输出在不同运行之间会变化。值班工程师需要可复现的结果。

3. **不可审计。** 你无法追溯 LLM 为什么选择根因 A 而不是 B。在本 Agent 中，你可以清楚地看到哪些证据支持每个假设，confidence weight 是多少。

LLM 将在后续加入——但仅用于报告综合，不参与决策。

---

## Q3: confidence score 是怎么来的？

`ConfidenceScorer` 使用确定性公式：

```
rawScore = pattern.baseScore
         + Σ(matched supporting evidence types 的权重)
         - Σ(matched counter evidence types 的 |weight|)
         - missingPenalty
```

权重在每个 `DiagnosticPattern` 中定义。例如，部署回退模式的 `deploy_event_near_alert_window` 权重为 0.12，`retry_timeout_config_change` 权重为 0.12。

这些权重是**基于 SRE 诊断经验手动设定的**，不是从数据中学习的。其价值在于每个分数都是可解释的——你可以精确追溯为什么部署回退得分为 0.64。

---

## Q4: 权重是从历史事件中学习来的吗？

不是。在 MVP 阶段，权重是基于 SRE 领域知识手动设定的。这一点已明确记录为局限性。

生产环境的演进路径：
- 从手动权重开始（当前状态）
- 每次调查后加入人工反馈（校准循环）
- 最终通过标签传播从历史事件数据中学习权重
- 或者使用 LLM 辅助权重建议，经人工审批后生效

架构已为此做好准备——权重存储在 `DiagnosticPattern.confidenceWeights()` 中，可以从数据库或配置文件加载，而非硬编码。

---

## Q5: 为什么使用 Java 和 Spring Boot 而不是 Python？

四个原因：

1. **企业级 SRE 语境。** 大多数大规模 SRE 平台（在 Kubernetes 大规模运行的公司）使用 Java 或 Go 构建。Spring Boot 是企业级 Java 的主导框架。

2. **类型安全。** Java record 提供带有编译时类型检查的不可变领域对象。在一个错误结论会产生运维影响的系统中，类型安全很重要。

3. **模块架构。** Maven 多模块能干净地强制执行 core 的零 Spring 依赖约束。Python 的包模型使这个边界更难执行。

4. **面试信号。** 对于使用 Java 的公司的 SRE 和平台工程岗位，这展示了 Java 21、Spring Boot 3.x 和 Maven 多模块的实际技能。

---

## Q6: 为什么保持 core 零 Spring 依赖？

三个原因：

1. **测试速度。** Core 单元测试在毫秒内完成，因为无需启动 Spring 上下文。使用 Spring 的服务端集成测试则较慢。分离意味着我可以立即运行 70+ 个 core 测试。

2. **可复用性。** 同一个 `InvestigationWorkflow` 在 CLI 和 server 中运行。将来可以在 Lambda 函数、Kafka consumer 或 K8s operator 中运行——无需引入 Spring Boot。

3. **架构清晰。** 零 Spring 依赖约束强制保持干净的边界。领域逻辑在 core 中，框架粘合代码在 adapters 中。这是六边形架构的实践。

---

## Q7: 为什么 Scenario E 输出 `competing_hypotheses`？

因为证据没有明确指向单一根因。

- 部署回退得分 0.64——有部署事件、错误飙升、超时日志和 git 中的配置变更作为支持。
- 下游依赖延迟得分 0.58——有超时日志、延迟飙升和服务拓扑作为支持。
- 得分差距仅为 0.06——远低于 0.10 的决定性结论阈值。

Agent 的决策策略是：如果排名前二的假设得分都高于 0.50，且差距低于 0.10，则输出 `competing_hypotheses`。

**在证据不明确时强制给出单一答案，比承认不确定性更糟糕。** 在真实事件中，这告诉值班工程师："两个方向都要调查——比较部署前后的超时错误，并按端点检查 payment-service 的延迟。"

---

## Q8: 当证据相互矛盾时怎么处理？

`VerificationEngine` 明确处理矛盾。每个 `VerificationResult` 包含：
- `supportingEvidenceIds`——支持假设的证据
- `counterEvidenceIds`——与假设矛盾的证据
- `missingEvidence`——缺失的预期证据类型
- `contradictions`——冲突证据的人类可读描述

在 Scenario E 中，部署回退有一个矛盾项："部署前就存在超时日志，因此部署可能不是唯一原因。" 这个矛盾通过 counter evidence 权重降低了 confidence score。

`MarkdownReporter` 在报告中包含一个**矛盾项**章节，让值班工程师可以清楚看到证据冲突的地方。

---

## Q9: 如何对接真实的 Prometheus / Loki / Kubernetes？

当前的 `StaticEvidenceProvider` 从 JSON 文件加载证据。扩展路径是：

1. **在 core 中定义 `EvidenceProvider` 接口：**
   ```java
   public interface EvidenceProvider {
       List<Evidence> collect(IncidentTask incident);
   }
   ```

2. **实现各 provider：**
   - `PrometheusEvidenceProvider`——查询 Prometheus API 获取指标类证据
   - `LokiEvidenceProvider`——查询 Loki 获取日志类证据
   - `KubernetesEvidenceProvider`——查询 K8s API 获取部署事件、Pod 状态、资源指标

3. **组合 provider：** `CompositeEvidenceProvider` 调用多个 provider 并合并结果

4. **配置驱动：** 每个环境配置使用哪些 provider

Core 工作流无需修改——`InvestigationWorkflow.run()` 已接受 `List<Evidence>`。各 provider 只是替代 JSON 文件加载。

---

## Q10: 什么时候加入 LLM？

**已经完成。** Phase 4（commit `cdaecac`）通过 `OpenAiCompatibleLlmClient` 接入了真实 LLM。

原来的顺序是：
1. 首先，对接真实证据 provider（Prometheus、Loki、K8s）——Step H/I ✅
2. 验证评分和决策逻辑在真实事件上产生正确结果 ✅
3. 然后加入 LLM 做报告综合 + 假设提议——Step G / Phase 4 ✅

这个顺序的原因：**你必须先信任结构化输出，然后才能让 LLM 去叙述它。** 如果评分是错的，写得再好的 LLM 报告只会让错误的结论听起来更有说服力。

LLM 通过环境变量配置：`LLM_PROVIDER=openai`、`LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`。已通过智谱 glm-4-flash E2E 验证（65s 返回 87KB 中文报告）。

---

## Q11: LLM 被允许做什么？

LLM 现在承担两个角色，都严格限定为 advisory：

1. **假设提议**（`LlmHypothesisProposerImpl`）——根据收集的证据上下文，LLM 提议额外的根因假设，弥补确定性 `DiagnosticPattern` 引擎可能未覆盖的情况。这些提议标记为 `advisoryOnly=true`、`canAffectDecision=false`。

2. **报告综合**（`LlmReportSynthesizer`）——将结构化的 `InvestigationResult` 综合为人类可读的叙述报告，包含：
   - 执行摘要
   - 推理叙事
   - 不确定性评估
   - 后续步骤
   - 局限性说明

额外能力：
- 根据决策和 next probes 建议修复操作
- 用通俗语言总结矛盾项
- 为非技术利益相关者翻译技术发现

LLM 的输入约定是 `InvestigationResult` record——它包含 LLM 所需的一切，且不包含它不应该修改的内容。`OpenAiCompatibleLlmClient` 通过环境变量支持任何 OpenAI 兼容端点（智谱、OpenAI、Ollama、vLLM）。

---

## Q12: LLM 不被允许做什么？

- **决定根因**——这是 `HypothesisComparator` 的工作
- **修改 confidence score**——分数是确定性的
- **编造证据**——证据来自 provider，不是 LLM
- **覆盖 InvestigationDecision**——决策是工作流的输出
- **跳过验证**——每个假设必须在评分前经过验证

这些是防护栏。架构在设计上就执行了这些约束——LLM 消费调查输出，不参与调查过程。

---

## Q13: 如何防止硬编码的演示逻辑？

三种机制：

1. **模式驱动的假设生成。** `HypothesisEngine` 为每个 `DiagnosticPattern` 生成一个假设。添加新模式就自动添加新假设，无需手动连接。

2. **证据驱动的验证。** `VerificationEngine` 按类型分类证据，而非按硬编码的 ID。如果你替换了证据 JSON，验证结果会相应变化。

3. **测试覆盖。** 88 个测试覆盖完整工作流，对分数、决策和证据分类有明确的断言。如果有人硬编码了结果，针对不同输入数据的测试会失败。

Demo 使用静态 JSON 文件是因为还没有真实的 Prometheus/Loki。但工作流代码不知道也不关心证据来自哪里。

---

## Q14: Event Trace 的价值是什么？

四个方面：

1. **可审计性。** 每一步都记录了时间戳和载荷。你可以准确看到发生了什么、按什么顺序、用了什么数据。

2. **可调试性。** 如果 Agent 输出了令人意外的决策，trace 显示了评分在哪里产生了偏差。"哦，下游延迟的 counter evidence 包含了部署事件——这就是为什么它得分更低。"

3. **交接。** 当一个值班工程师运行调查后交接给另一个工程师时，trace 可以讲述完整的故事，无需口头解释。

4. **校准数据。** 随着时间推移，来自多次调查的 Event Trace 成为校准 confidence weight 和评估决策准确性的数据集。

---

## Q15: 如何演进为生产系统？

按优先级排序：

1. **真实证据 provider**——对接 Prometheus、Loki、K8s API
2. **持久化存储**——用数据库替代内存存储
3. **LLM 报告综合**——添加自然语言叙述
4. **人工反馈循环**——值班工程师确认或纠正决策，反馈到权重校准
5. **更多诊断模式**——覆盖更多故障类型（网络分区、证书过期、DNS 问题等）
6. **多服务关联**——处理跨多个服务的事件
7. **修复建议**——不仅是根因分析，还有带风险评估的可操作修复方案

架构支持以上所有扩展，且无需修改 core 工作流——它们都是在边缘的扩展。

---

## Q16: 如果应用没有部署在 Kubernetes 上怎么办？比如 EC2 实例、RDS、ElastiCache？

好问题。当前 MVP 在命名上以 K8s 为中心（`IncidentTask.namespace`、`Evidence.service`、`pod_oom_killed` 模式），但**核心管道已经是平台无关的**。

原因如下：`VerificationEngine` 和 `ConfidenceScorer` 只匹配 `evidenceType` 字符串。它们不检查 `service`、`namespace` 或任何 K8s 特定字段。因此添加 EC2、RDS 或 ElastiCache 的证据类型可以通过完全相同的机制实现——定义新的 `evidenceType` 字符串、新的模式和新的 provider。

**需要变更的部分：**

| 变更 | 原因 |
|---|---|
| `IncidentTask.namespace` → `scope` | EC2 有 AZ/VPC，不是 namespace。RDS 有 region。 |
| 添加 `IncidentTask.platform` | Agent 需要知道是在调查 K8s、EC2 还是托管服务。 |
| `Evidence.service` → `entity` | RDS 实例或 ElastiCache 集群不是"service"。 |
| 添加 `Evidence.entityType` | 支持类型感知推理——区分数据库、缓存和负载均衡器。 |

**核心评分管道无需任何修改。** 这就是基于 `evidenceType` 字符串匹配而非平台特定字段匹配的架构回报。

---

## Q17: 服务拓扑和部署信息从哪里获取——CMDB 还是静态 JSON？

在 MVP 中，使用静态 JSON——Scenario E 中 `service_dependency_match` 证据类型有一个硬编码的 `call_path` 属性。

在生产环境中，正确的答案是**两者都用，以 CMDB 为主要来源**：

1. **CMDB / 服务注册中心**——作为以下信息的真实来源：
   - 服务依赖拓扑（谁调用谁，同步 vs 异步）
   - 部署拓扑（哪个服务运行在哪里——K8s 集群、EC2 ASG、ECS task）
   - 服务负责人和值班轮值
   - 容量基线（该服务是否在正常负载水平？）
   - 变更记录（不仅是 git commit，还包括人工发起的变更）

2. **实时发现**作为补充：
   - Service mesh 遥测（Istio/Envoy、AWS App Mesh）获取实际调用路径
   - 分布式追踪（Jaeger、X-Ray）获取观测到的依赖关系

3. **用于测试的 JSON mock**——当前方式变为测试夹具格式，而非生产路径。

`EvidenceProvider` 接口可以干净地处理这个——`CmdbTopologyProvider` 实现了与 `StaticEvidenceProvider` 相同的接口。工作流不关心拓扑证据来自哪里。

---

## Q18: 如何处理跨 K8s 和非 K8s 基础设施的事件？

这是一个真实场景——K8s 上的 order-service 调用 RDS 做持久化、ElastiCache 做会话缓存。一个事件可能同时涉及三者。

**关键设计决策：** 每条 Evidence 记录上的 `entityType` 告诉 Agent 它在处理什么类型的资源。`IncidentTask` 上的 `platform` 字段决定激活哪些证据 provider。

```
CompositeEvidenceProvider
  ├── KubernetesEvidenceProvider   → Pod 事件、资源指标
  ├── Ec2EvidenceProvider          → 实例状态、CPU steal
  ├── AwsManagedServiceEvidenceProvider → RDS、ElastiCache 指标
  └── CmdbTopologyProvider         → 跨平台依赖图
  ↓
合并后的 List<Evidence>，每条记录带有 entityType
```

每条证据都标记了其 `entityType`（`"service"`、`"database"`、`"cache"`），因此模式可以跨平台边界推理。例如，`rds_connection_exhaustion` 模式会同时查找来自调用服务（超时日志）和数据库（达到 max_connections）的证据——无论各自运行在什么平台上。

**这就是为什么 `evidenceType` 匹配设计很重要。** 如果管道绑定在 K8s 特定字段上，跨平台推理就需要特殊处理。使用 `evidenceType` 字符串，一切自然生效。

---

## Q19: Step G 添加了什么，为什么 LLM 只是 advisory-only？

Step G 添加了一个 LLM 集成层，包含四个组件：`LlmClient`（接口）、`MockLlmClient`（空操作实现）、`LlmPromptBuilder`（带防护栏的系统提示词）和 `LlmReportSynthesizer`（编排调用）。LLM 生成自然语言叙述摘要和建议的修复方案——它**不**触及决策、confidence score 或证据。

LLM 仅作为顾问角色，因为确定性管道已经产生经过验证、可审计的结果。让 LLM 修改分数或决策会在调查中引入非确定性和幻觉风险——这恰恰是本 Agent 设计时要避免的问题。LLM 增强呈现效果，不参与推理。

---

## Q20: LLM 提示词约定如何执行防护栏？

`LlmPromptBuilder` 构建一个系统提示词，明确约束 LLM：

- 它**只能**总结提供的 `InvestigationResult`——不得编造证据、不得质疑分数、不得覆盖决策。
- 它必须将其输出标记为建议性质。
- 它接收完整的结构化结果（假设、分数、证据、决策、矛盾项）作为上下文，因此它拥有所需的一切而无需推理。

该约定通过架构执行：LLM 永远看不到原始证据或工作流内部——只能看到最终的 `InvestigationResult` record。它无法回调系统。即使 LLM 在叙述中幻觉了一个不同的决策，结构化的 `InvestigationResult.decision()` 仍然是权威答案。

---

## Q21: MockLlmClient 是什么，为什么需要它？

`MockLlmClient` 是 `LlmClient` 的默认实现。它返回一个预设的响应字符串，不调用任何外部 API。它的存在使得系统可以端到端运行，无需 API key、网络访问或 LLM provider 配置。

这在三个方面很重要：

1. **开发速度**——包括 UI 在内的完整管道无需外部依赖即可正确渲染。
2. **CI/CD**——测试和构建无需管理密钥或速率限制即可通过。
3. **Demo 就绪**——任何人都可以克隆并立即运行项目。

切换到真实 provider 只需一个 Spring `@Profile` 或配置开关——无需修改 synthesizer 或工作流代码。

---

## Q22: 如何切换到真实的 OpenAI 兼容 LLM？

**已经实现。** `OpenAiCompatibleLlmClient` 是真实实现，通过环境变量激活：

```bash
export LLM_PROVIDER=openai
export LLM_API_KEY=your-api-key
export LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4  # 智谱
export LLM_MODEL=glm-4-flash
```

客户端具有智能端点 URL 构建：
- 如果 base URL 以 `/chat/completions` 结尾 → 直接使用
- 如果 base URL 包含 `/v4` 或 `/v3` → 拼接 `/chat/completions`（智谱风格）
- 否则 → 拼接 `/v1/chat/completions`（标准 OpenAI 风格）

兼容智谱、OpenAI、Azure OpenAI、Ollama 和 vLLM，无需代码修改。`MockLlmClient` 在 `LLM_PROVIDER` 未设置或设为 `mock` 时激活，确保全部 1194 个测试保持确定性。

**E2E 验证通过：** 智谱 glm-4-flash 通过 `GET /api/live-scenario/simulate?runLlm=true` 返回 87KB 中文 LLM 增强报告，耗时 ~65s。

---

## Q23: LlmEnhancedReport 是什么，为什么同时包含 `base*` 字段和 LLM 字段？

`LlmEnhancedReport` 是一个 record，封装了原始的 `InvestigationResult` 和 LLM 生成的内容。它包含：

- **`baseResult`**——完整的 `InvestigationResult`（决策、假设、分数、证据、矛盾项、trace）。这是权威的确定性输出。
- **LLM 字段**——`llmNarrative`（自然语言摘要）、`llmRemediation`（建议的操作）、`llmProvider`（使用的 provider，例如 "Mock Provider"）。

将两者放在一个对象中为 UI 和 API 消费者提供了统一的响应信封。这种分离明确标识了哪些字段是确定性的、哪些是建议性的——你始终可以忽略 LLM 字段，完全依赖 `baseResult`。

---

## Q24: UI 如何区分确定性结果和 LLM 辅助结果？

UI 使用不同的视觉徽章：

- **Authoritative** 徽章——显示在确定性部分（决策、分数、证据、假设）。此内容由经过验证的管道产生，完全可审计。
- **Advisory Only** 徽章——显示在 LLM 叙述和修复建议部分。这标识内容为 AI 生成，应视为建议而非结论。
- **Mock Provider** 徽章——在 `MockLlmClient` 激活时显示，让审阅者知道 LLM 文本是占位符，不是真正的 AI 输出。

LLM 部分底部还显示一个防护栏提示，再次强调 LLM 不能修改决策或分数。这种三徽章方式确保阅读报告的人不会将综合文本与经验证的发现混淆。

---

## Q25: Probe Execution Framework 是什么，有哪些防护栏？

Step S 引入了 Probe Execution Framework（`sre-agent-probe-executor`）。LLM Hypothesis Proposer 生成假设的同时，也会输出 **ProbeIntents** —— 结构化的探测建议，如"检查 p95 延迟"或"检查错误率"。Probe executor 将这些意图路由到对应的 evidence provider（Prometheus、Loki、Trace、Kubernetes、Alertmanager），并收集返回的 Evidence。

**关键防护栏：**
- `canAffectDecision` 始终为 `false` —— probe evidence 仅供参考，不能改变 RCA 决策。
- 通过 **编译时强制**：`ProbeExecutionResult` 构造器在 `canAffectDecision=true` 时抛出 `IllegalArgumentException`。
- Step S 仅支持 `FIXTURE` 模式 —— 无真实后端探测。
- `ProbeExecutionPolicy` 在执行前验证每个 plan：最大 probes 数限制、模式检查、canAffectDecision 检查。

---

## Q26: 为什么 probe execution 和 LLM 假设生成分属不同模块？

关注点分离：
- `sre-agent-llm` 负责假设生成 —— 提出"调查什么"。
- `sre-agent-probe-executor` 负责"怎么调查" —— 路由意图、执行 provider 调用、收集 Evidence。
- 核心 RCA 管道（`sre-agent-core`）完全不感知 probe 的存在。

这意味着可以独立替换或升级 probe 执行逻辑，不影响 LLM 模块，反之亦然。同时也保持了 `sre-agent-core` 零依赖（零 LLM、零 Spring、零 provider 特定 import）。

---

## Q27: `canAffectDecision` 防护栏是什么，为什么要编译时强制？

Probe evidence 补充 RCA 但绝不能覆盖确定性管道的结论。如果 probe evidence 能翻转决策，整个可审计性保证就崩溃了 —— 工程师将无法仅凭原始 evidence 复现决策。

编译时强制是因为运行时检查可以被绕过或遗忘。`ProbeExecutionResult` record 构造器在 `canAffectDecision=true` 时抛出 `IllegalArgumentException`，确保没有任何代码路径 —— 无论有意还是无意 —— 能创建声称影响决策的 result。

在未来的 Step W（post-probe RCA re-run policy）中，这个限制会在严格条件下放宽，但在 Step S 中：probes 只观察，不做决策。

---

## Q28：可观测性状态服务如何在不依赖活跃端点的情况下检查端点健康状态？

`EndpointHealthChecker` 是一个**接口**，只有一个方法：`check(url) → EndpointStatus`。默认实现（`HttpEndpointHealthChecker`）通过 HTTP GET 请求检查连通性并测量延迟。在测试中，这个接口被模拟（mock）——不需要运行任何真实的 Prometheus、Loki 或 Tempo 实例。

这与 `LlmClient` / `MockLlmClient` 的模式相同：接口定义契约，测试友好的实现始终可用。23 个新测试（ObservabilityEndpointStatusTest、ObservabilityStatusServiceTest、ObservabilityEndpointConfigTest、HttpEndpointHealthCheckerTest）都使用模拟的健康检查器，因此 `mvn test` 在没有运行任何可观测性栈的情况下也能通过。

端点 URL 本身来自 `application.properties`，而非硬编码常量。这意味着不同环境（本地 kind、staging、生产）可以配置不同的端点列表，无需修改代码。

---

## Q29：为什么 `EndpointHealthChecker` 是接口而不是直接使用 `RestTemplate`？

三个原因：

1. **可测试性。** 在单元测试中发起 HTTP 调用意味着要么启动真实服务器，要么使用模拟 HTTP 服务器。通过提取接口，测试可以注入一个即时返回预定结果的模拟对象。无网络、无超时、无脆弱测试。

2. **可扩展性。** 并非每个可观测性端点都以相同方式使用 HTTP。Prometheus 使用 `/-/healthy`，Grafana 使用 `/api/health`，某些系统可能需要 gRPC 或 TCP 检查。接口允许每种端点类型有不同的实现，而无需更改服务层。

3. **与项目模式一致。** 本项目中的每个外部依赖都在接口之后：`LlmClient`、`PrometheusQueryClient`、`LokiQueryClient`、`TraceQueryClient`、`KubernetesResourceReader`。`EndpointHealthChecker` 遵循相同的六边形架构原则——基础设施关注点在 SPI 边界之后，而非嵌入在业务逻辑中。

---

## Q30：Live Lab Status 页面显示什么？为什么它对演示很重要？

Live Lab Status 页面（通过页面头部的 "Lab Status" 按钮切换）显示所有已配置可观测性端点的实时健康状态——Prometheus、Loki、Tempo、Alertmanager 和 Grafana。对于每个端点，它显示：名称、URL、状态（UP/DOWN/UNKNOWN）、响应延迟和最后检查时间戳。

这很重要的三个原因：

1. **演示可信度。** 在向面试官展示 SRE agent 时，能够展示"所有可观测性端点都在运行且健康"是一个具体信号，表明这不是一个玩具项目——背后有真实的基础设施。

2. **开发者体验。** 在本地开发期间，该页面在运行场景之前提供一目了然的确认：可观测性栈已启动。如果 Prometheus 宕机，你可以立即看到，而不是在之后遇到晦涩的连接错误。

3. **连接基础设施与应用。** Step T 的脚本（`scripts/observability/`）管理基础设施（Helm 安装、端口转发），状态页面将基础设施状态反映回应用程序中。这展示了 SRE 和平台工程角色所需的全面思考能力。

**重要提示：** Step T 不部署演示服务或注入故障——那是 Step U 和 Step V 的工作。Step T 专注于可观测性栈生命周期管理和健康可见性。

---

## Q31：为什么 `demo-services` 是独立的 Maven 模块，而不是 `sre-agent-server` 的一部分？

三个原因：

1. **关注点分离。** `demo-services` 是 SRE agent 调查的目标服务——它们不是 RCA 管道的一部分。将它们混入 `sre-agent-server` 会混淆"被监控系统"和"执行监控系统"，违反六边形架构边界。

2. **独立生命周期。** 演示服务构建为 Docker 镜像并部署到 kind 集群。SRE agent 作为 Java 进程运行。它们有完全不同的构建、测试和部署周期。独立的 Maven 模块意味着 `mvn -pl demo-services package docker:build` 不会触发整个 agent 构建。

3. **面试信号。** 这展示了对基础设施/验证工具应与生产代码隔离的理解。在真实的 SRE 组织中，合成测试服务和金丝雀部署遵循相同的模式——独立的仓库或模块。

---

## Q32：演示服务中的故障注入如何工作？为什么是运行时控制的？

故障注入通过 REST API 调用 SRE agent server 触发：

- `POST /api/demo-services/fault/payment-latency` — 在 `payment-service` 端点注入 1500ms 人工延迟
- `POST /api/demo-services/fault/clear` — 清除所有注入的故障

内部实现上，`payment-service` 有一个 `FaultInjectionController`，切换线程局部布尔标志。当故障标志激活时，服务的 `PaymentController` 在处理前添加 `Thread.sleep()`。Prometheus 抓取到升高的延迟，SRE agent 的 `PrometheusEvidenceProvider` 将其作为真实的指标证据收集。

采用运行时控制（而非代码修改）的原因：

1. **演示流程。** 在面试演示中，你可以展示正常流量 → 注入故障 → agent 检测异常 → 清除故障 → 恢复，全程无需重启任何东西。
2. **可重复性。** 测试可以编程方式注入和清除故障，实现确定性的端到端 RCA 验证。
3. **不修改源码。** agent 代码库保持不变——故障注入是外部刺激，就像真实的生产事故一样。

---

## Q33：演示服务如何连接到 SRE agent 的证据管道？

演示服务形成三层微服务拓扑：`order-service` 同时调用 `payment-service` 和 `inventory-service`。所有三个服务通过 `/actuator/prometheus` 暴露 Micrometer 指标，由 kind 集群中的 Prometheus 实例抓取。

连接分三步工作：

1. **指标收集。** Prometheus 每 15 秒抓取所有演示服务。当 `payment-service` 有注入延迟时，`http_server_requests_seconds` 指标显示升高的 p99 值。

2. **证据提取。** SRE agent 的 `PrometheusEvidenceProvider` 通过 `PrometheusQueryClient` 使用 PromQL 查询 Prometheus，如 `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{service="payment-service"}[5m]))`。这返回真实的指标证据——而非 fixture。

3. **RCA 管道。** 真实的指标证据进入标准的 `InvestigationWorkflow`：收集 → 对照诊断模式评分 → 比较 → 做出决策。agent 不知道也不关心指标来自演示服务还是生产服务——`EvidenceProvider` 接口抽象了来源。

这是关键的桥梁：Step T 提供了可观测性栈（Prometheus、Grafana），Step U 提供了生成可观测遥测数据的目标服务。两者结合实现了使用真实指标（而非静态 JSON fixture）的端到端 RCA 验证。

---

## Q34：真实 LLM 集成是如何工作的？

Phase 4 新增了 `OpenAiCompatibleLlmClient`——一个生产级 LLM 客户端，通过环境变量（`LLM_PROVIDER`、`LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`）连接任何 OpenAI 兼容端点。它具有智能 URL 构建，自动处理智谱、OpenAI、Azure、Ollama 和 vLLM 的端点差异。

集成有两个路径：
1. **LLM 假设提议**（`LlmHypothesisProposerImpl`）——LLM 根据收集的证据提议额外的根因假设。这些提议标记为 `advisoryOnly=true`、`canAffectDecision=false`——它们不能影响确定性决策。
2. **报告综合**（`LlmReportSynthesizer`）——LLM 从确定性 `InvestigationResult` 生成结构化叙述报告（执行摘要、推理叙事、不确定性、后续步骤、局限性）。

如果 LLM 不可用或失败，两个路径自动降级为 `MockLlmClient` 行为。

---

## Q35：LLM 不可用时怎么办？

系统设计为无 LLM 也完全可用：

1. **自动降级。** 如果 `LLM_PROVIDER` 未设置，或真实 LLM 客户端抛出异常，`LlmHypothesisProposerImpl` 捕获失败并返回空提议列表。调查仅使用确定性假设继续。
2. **测试用 MockLlmClient。** 全部 1194 个测试使用 `MockLlmClient`——零外部 API 依赖，完全确定且可重复。
3. **优雅降级。** API 响应包含 `llmProposal` 字段，要么有值要么为 null。UI 显示"LLM 不可用"而非崩溃。

核心设计原则：**确定性引擎始终工作，LLM 是锦上添花。**

---

## Q36：如何验证 LLM 输出不会污染调查？

三层防护栏，在不同层面执行：

1. **编译时。** `canAffectDecision` 是 `UnverifiedHypothesisProposal` 上的 `boolean` 字段——对 LLM 提议始终为 `false`，没有代码路径可以将其设为 `true`。
2. **运行时。** `HypothesisComparator` 在计算最终 `InvestigationDecision` 时忽略 `advisoryOnly=true` 的提议。只有确定性假设影响决策。
3. **结构性。** `LlmEnhancedReport` 严格分离 `base*` 字段（确定性引擎输出）和 LLM 字段（advisory 内容）。API 消费者始终可以完全忽略 LLM 字段。

---

## Q37：如何防止 Kubernetes 证据的误报？

RCA 排名中的误报通常来自不精确的证据分类。在 V.2-UI-4.1 中，我遇到了一个真实案例：`pod_crash_loop` 在一个**延迟场景**中排到第一（分数 0.25），但它应该排到最后。

根因有三个：
1. **通用事件映射。** 所有 K8s 事件被映射为单一的 `k8s_event` 类型，丢失了语义信号。
2. **宽松的崩溃循环检测。** `container_crash_loop_backoff` 在任何 restartCount > 0 的 pod 上都会触发，而不只是真正的 CrashLoopBackOff 状态。
3. **缺少反例信号。** 健康的 pod 不产生对抗性证据。

修复是三层方案：

1. **语义事件映射。** `KubernetesEvidenceMapper.mapEventsToSemanticEvidence()` 现在按 reason 分类事件——`k8s_event_unhealthy`、`k8s_event_failed_scheduling`、`k8s_event_killing`、`k8s_event_normal`——而非通用的 `k8s_event`。

2. **精确异常检测。** `container_crash_loop_backoff` 只在 `pod.status.reason == "CrashLoopBackOff"` 时触发。`pod_not_ready` 需要真实的就绪检查失败，不只是非零重启次数。

3. **健康 pod 的反例证据。** `k8s_runtime_healthy` 和 `pod_ready` 提供对抗性信号。`VerificationEngine` 过滤掉非诊断类型（NONE、k8s_no_signal、k8s_runtime_healthy、restart_count_observed）不参与评分。

**Live E2E 结果：** 修复后，`downstream_dependency_latency` 排到第一（0.50），`pod_crash_loop` 降到第四（0.09）。修复在真实的 Kind 集群上用实时 Prometheus/Loki/K8s 数据验证过。

这展示了一个关键 SRE 原则：**证据质量决定 RCA 质量。** 评分算法再精妙，也无法弥补不精确的证据分类。
