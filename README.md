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
LLM Synthesis (optional) ── MockLlmClient → LlmPromptBuilder → LlmReportSynthesizer → LlmEnhancedReport
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

## Architecture Overview

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
│  ┌──── sre-agent-k8s-provider ────┐  ┌──── sre-agent-llm ────────┐
│  │                                │  │                            │
│  │  Fixture-based K8s evidence    │  │  MockLlmClient → LlmPrompt │
│  │  provider (pod status,         │  │       ↓                    │
│  │  container restarts, exit      │  │  LlmReportSynthesizer →    │
│  │  codes)                        │  │  LlmEnhancedReport         │
│  │                                │  │                            │
│  │  Zero Spring dependency        │  │  Guardrails: no auto-act.  │
│  │  Zero K8s client dependency    │  │  Zero Spring dependency    │
│  └────────────────────────────────┘  └────────────────────────────┘
└──────────────────────────────────────────────────────────┘
```

### Module Structure

| Module | Purpose | Spring Dependency |
|---|---|---|
| `sre-agent-core` | Deterministic RCA workflow engine | **None** |
| `sre-agent-k8s-provider` | Fixture-based K8s evidence provider (pod status, restarts, exit codes) | **None** |
| `sre-agent-llm` | LLM report synthesis (MockLlmClient, prompt building, report enhancement) | **None** |
| `sre-agent-cli` | Command-line adapter (Picocli) | None |
| `sre-agent-server` | Spring Boot REST API + Web UI | Spring Boot 3.x |

**Key constraint:** `sre-agent-core`, `sre-agent-k8s-provider`, and `sre-agent-llm` have zero Spring dependency. `sre-agent-k8s-provider` also has zero K8s client library dependency — it uses fixture-based evidence. The workflow is pure Java. CLI and Server are thin adapters that call the same `InvestigationWorkflow`. The LLM module is optional — it enhances reports with AI-synthesized narratives while respecting guardrails (no auto-action, no data exfiltration).

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+

### Build and Test

```bash
mvn test
```

Expected: 162 tests passing.

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

> **Note:** The current implementation uses `MockLlmClient` which returns deterministic placeholder summaries. Swap in a real LLM provider (OpenAI, Anthropic, etc.) by implementing the `LlmClient` interface.

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
- JUnit 5 + AssertJ (162 tests)
- Static HTML + vanilla JS (minimal Web UI)

---

## Current Limitations

- **Static evidence** — Scenario E uses pre-loaded JSON, not real Prometheus/Loki/K8s
- **Manual confidence weights** — weights are based on SRE diagnostic experience, not learned from data
- **In-memory store** — investigation results are not persisted across server restarts
- **No real LLM provider** — current LLM integration uses MockLlmClient; swap in a real provider by implementing the LlmClient interface
- **4 diagnostic patterns** — covers deployment regression, dependency latency, resource pressure, and CrashLoopBackOff
- **2 demo scenarios** — Scenario E (competing hypotheses) and Scenario F (CrashLoopBackOff)

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
│   └── future-roadmap.md
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
├── sre-agent-k8s-provider/          # K8s evidence provider (fixture-based)
│   ├── pom.xml
│   └── src/main/java/ai/sreagent/k8s/
│       └── K8sEvidenceProvider.java
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
        ├── controller/              # HealthController, InvestigationController
        └── service/                 # InvestigationService, InvestigationResponse, InMemoryInvestigationStore
```

---

## Roadmap

See [docs/future-roadmap.md](docs/future-roadmap.md) for the full plan.

| Step | Scope | Status |
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
LLM Synthesis (optional) ── MockLlmClient → LlmPromptBuilder → LlmReportSynthesizer → LlmEnhancedReport
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
│  ┌──── sre-agent-k8s-provider ────┐  ┌──── sre-agent-llm ────────┐
│  │                                │  │                            │
│  │  Fixture-based K8s evidence    │  │  MockLlmClient → LlmPrompt │
│  │  provider (pod status,         │  │       ↓                    │
│  │  container restarts, exit      │  │  LlmReportSynthesizer →    │
│  │  codes)                        │  │  LlmEnhancedReport         │
│  │                                │  │                            │
│  │  Zero Spring dependency        │  │  Guardrails: no auto-act.  │
│  │  Zero K8s client dependency    │  │  Zero Spring dependency    │
│  └────────────────────────────────┘  └────────────────────────────┘
└──────────────────────────────────────────────────────────┘
```

### 模块结构

| 模块 | 用途 | Spring 依赖 |
|---|---|---|
| `sre-agent-core` | 确定性根因分析工作流引擎 | **无** |
| `sre-agent-k8s-provider` | 基于 Fixture 的 K8s 证据提供器（Pod 状态、重启、退出码） | **无** |
| `sre-agent-llm` | LLM 报告综合（MockLlmClient、提示词构建、报告增强） | **无** |
| `sre-agent-cli` | 命令行适配器（Picocli） | 无 |
| `sre-agent-server` | Spring Boot REST API + Web UI | Spring Boot 3.x |

**关键约束：** `sre-agent-core`、`sre-agent-k8s-provider` 和 `sre-agent-llm` 零 Spring 依赖。`sre-agent-k8s-provider` 也零 K8s 客户端库依赖——使用基于 Fixture 的证据。工作流是纯 Java 实现。CLI 和 Server 是薄适配层，调用同一个 `InvestigationWorkflow`。LLM 模块是可选的——它在遵守护栏（不自动执行操作、不泄露数据）的前提下，用 AI 综合叙述来增强报告。

---

## 快速开始

### 前置条件

- Java 21+
- Maven 3.8+

### 构建与测试

```bash
mvn test
```

预期结果：162 个测试全部通过。

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

> **注意：** 当前实现使用 `MockLlmClient`，返回确定性的占位摘要。可通过实现 `LlmClient` 接口替换为真实的 LLM 提供商（OpenAI、Anthropic 等）。

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
- JUnit 5 + AssertJ（162 个测试）
- 静态 HTML + 原生 JS（轻量 Web UI）

---

## 当前限制

- **静态证据** —— 场景 E 使用预加载的 JSON，而非真实的 Prometheus / Loki / K8s 数据
- **手动置信度权重** —— 权重基于 SRE 诊断经验设定，未通过数据学习
- **内存存储** —— 排查结果不会在服务器重启后持久化
- **无真实 LLM 提供商** —— 当前 LLM 集成使用 MockLlmClient；可通过实现 LlmClient 接口替换为真实提供商
- **4 种诊断模式** —— 覆盖部署回退、依赖延迟、资源压力和 CrashLoopBackOff
- **2 个演示场景** —— 场景 E（竞争性假设）和场景 F（CrashLoopBackOff）

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
│   └── future-roadmap.md
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
│       └── K8sEvidenceProvider.java
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
        ├── controller/              # HealthController, InvestigationController
        └── service/                 # InvestigationService, InvestigationResponse, InMemoryInvestigationStore
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
