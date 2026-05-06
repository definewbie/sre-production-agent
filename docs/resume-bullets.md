# Resume Bullets

---

## 中文简洁版

构建 verification-first AI SRE RCA Agent：以告警为入口，基于 evidence 和 diagnostic patterns 生成并验证多个根因假设，通过确定性置信度评分和假设比较输出可解释的 competing hypotheses decision、Markdown RCA report 和 Event Trace；采用 Java 21 + Spring Boot 3.x + Maven multi-module（15 modules: 11 agent + 4 demo-services）实现，核心 RCA workflow 保持纯 Java、零框架依赖，同时支持 CLI、REST API、Live Scenario 和 Web UI。

新增 sre-agent-llm 模块实现 advisory-only LLM 合成层：LlmPromptBuilder 构建带 guardrails 的提示词，LlmReportSynthesizer 将确定性 RCA 结果交由 LLM 生成可读摘要，LlmEnhancedReport 严格分离 base（确定性）与 LLM（ advisory）字段。Phase 4 新增 OpenAiCompatibleLlmClient 接入真实 LLM（已通过智谱 glm-4-flash E2E 验证），同时 LlmHypothesisProposerImpl 允许 LLM 提议额外根因假设（advisoryOnly=true，不影响确定性决策）。MockLlmClient 保证 1194 个测试全部可重复、无外部依赖。

---

## 中文详细版

设计并实现 verification-first SRE RCA Agent，解决 Kubernetes 微服务告警根因分析中的两个痛点：手工 RCA 效率低且不一致，LLM log chatbot 不可审计且容易产生幻觉。

核心设计：
- 以告警触发调查流程，收集 8 类 evidence（部署事件、指标异常、日志超时、Git 配置变更、服务拓扑等）
- 基于 DiagnosticPattern 生成多个根因假设，每个假设经过 VerificationEngine 分类为 supporting / counter / missing / contradiction
- 通过确定性置信度评分公式（baseScore + supporting weights - counter weights）输出可解释分数
- HypothesisComparator 实现 5 级决策策略，当两个假设分数差距 < 0.10 时输出 competing_hypotheses 而非强制单一结论
- 完整 Event Trace 记录调查全过程，支持审计和 handoff

架构设计：
- Maven multi-module（15 modules: core/llm/server/cli + 7 agent 模块 + 4 demo-services），核心模块零 Spring 依赖
- CLI 和 REST API 共享同一 InvestigationWorkflow，确保行为一致性
- Live Scenario 支持：故障注入→证据收集→RCA→LLM 报告→重置，5-provider 实时证据收集
- 所有领域对象使用 Java 21 record，不可变且类型安全
- 1194 个单元测试覆盖完整 workflow

技术栈：Java 21, Spring Boot 3.3.6, Maven, Jackson, Picocli, JUnit 5, AssertJ
LLM 集成（sre-agent-llm 模块）：
- Advisory-only 设计：LLM 不参与根因决策，仅对确定性 RCA 结果进行叙事合成（report synthesis）
- LlmPromptBuilder 构建结构化提示词（中文），内置 prompt guardrails 限制 LLM 输出范围
- LlmReportSynthesizer 调用 LLM 生成可读摘要，输出写入 LlmEnhancedReport
- LlmHypothesisProposerImpl（Phase 4）：LLM 根据证据提议额外根因假设（advisoryOnly=true, canAffectDecision=false）
- OpenAiCompatibleLlmClient（Phase 4）：生产级 LLM 客户端，支持智谱/OpenAI/Azure/Ollama/vLLM
- LlmEnhancedReport 严格分离 base* 字段（确定性引擎输出）与 LLM 字段（advisory 合成内容），确保审计链完整
- MockLlmClient 实现确定性行为，保证 1194 个测试全部可重复运行、无外部 API 依赖
- 项目现为 15 模块架构（11 agent + 4 demo-services），模块间依赖清晰

---

## English Concise

Built a verification-first AI SRE RCA Agent (Java 21 + Spring Boot + Maven multi-module: 15 modules) that generates, verifies, and scores competing root cause hypotheses from alert-driven evidence, outputting auditable Markdown reports and Event Traces. Core workflow has zero framework dependency and runs via CLI, REST API, Live Scenario, and Web UI. 1194 tests, BUILD SUCCESS.

Added sre-agent-llm module providing advisory-only LLM synthesis. Phase 4 added OpenAiCompatibleLlmClient (E2E verified with Zhipu glm-4-flash) and LlmHypothesisProposerImpl for LLM-proposed root cause hypotheses (advisoryOnly=true). LlmEnhancedReport cleanly separates base (deterministic) vs LLM (advisory) fields. MockLlmClient enables 1194 fully deterministic, no-network tests. LLM is strictly forbidden from altering investigation decisions.

---

## English Detailed

Designed and implemented a verification-first SRE Root Cause Analysis Agent for Kubernetes microservice incidents, addressing two shortcomings of existing approaches: manual RCA is slow and inconsistent, while LLM log chatbots are unauditable and prone to hallucination.

**Core Workflow:**
- Collects structured evidence (deploy events, metric anomalies, log errors, git config changes, service topology) from alert-triggered investigation
- Generates one hypothesis per DiagnosticPattern, then verifies each against supporting and counter evidence
- Scores hypotheses with a deterministic, explainable confidence formula (baseScore + supporting weights - counter weights)
- Compares hypotheses and applies a 5-level decision policy — outputs `competing_hypotheses` when the top two scores are within 0.10, preserving uncertainty instead of forcing a single conclusion
- Records every workflow step in an Event Trace for full auditability

**Architecture:**
- Maven multi-module (15 modules: 11 agent + 4 demo-services) with zero Spring dependency in the core RCA engine
- CLI (Picocli) and REST API (Spring Boot) share the same `InvestigationWorkflow`, ensuring behavioral consistency
- Live Scenario support: fault injection → evidence collection → RCA → LLM report → reset, 5-provider real-time evidence
- All domain objects are Java 21 records — immutable and type-safe
- 1194 unit tests covering the full workflow pipeline

**Tech Stack:** Java 21, Spring Boot 3.3.6, Maven multi-module, Jackson, Picocli, JUnit 5, AssertJ
**LLM Integration (sre-agent-llm module):**
- Advisory-only design: LLM never participates in root cause decisions — it only synthesizes narrative summaries from deterministic RCA output
- `LlmPromptBuilder` constructs structured prompts (Chinese) with built-in prompt guardrails to constrain LLM output scope
- `LlmReportSynthesizer` invokes the LLM client to produce human-readable report synthesis
- `LlmHypothesisProposerImpl` (Phase 4): LLM proposes additional root cause hypotheses based on collected evidence (advisoryOnly=true, canAffectDecision=false)
- `OpenAiCompatibleLlmClient` (Phase 4): Production LLM client supporting Zhipu/OpenAI/Azure/Ollama/vLLM via env vars. E2E verified with Zhipu glm-4-flash (~65s, 87KB Chinese report)
- `LlmEnhancedReport` cleanly separates `base*` fields (deterministic engine output) from LLM-populated fields (advisory content), preserving full audit chain
- `MockLlmClient` provides deterministic responses, ensuring all 1194 tests are repeatable with zero external API dependency
- Project is now a 15-module architecture (11 agent + 4 demo-services) with clear dependency boundaries

---

## Interview Oral Version

> "I built an SRE RCA agent that takes a different approach from log chatbots. When a Kubernetes alert fires, it collects structured evidence from 5 providers (Prometheus, Loki, K8s API, deployment events, service topology), generates multiple root cause hypotheses, verifies each one, and scores them with a deterministic formula. The key design decision: when two hypotheses are too close to call, it preserves both instead of forcing a single answer.
>
> The architecture is a Maven multi-module build with 15 modules — core engine has zero Spring dependency, with CLI, REST API, and Live Scenario as thin adapters calling the same workflow. All domain objects are Java 21 records. I have 1194 tests covering the full pipeline.
>
> The LLM integration is deliberately advisory-only. Phase 4 added a real OpenAI-compatible client — I verified it with Zhipu glm-4-flash. The LLM can propose additional hypotheses, but they're always marked advisoryOnly=true and canAffectDecision=false. The LlmEnhancedReport cleanly separates deterministic base fields from LLM advisory fields — so the LLM can narrate findings, but it can never change a root cause decision."

---

## 面试口述版

> "我做了一个 SRE RCA Agent，和常见的日志聊天机器人思路不同。Kubernetes 告警触发后，它从 5 个 provider 收集结构化证据（Prometheus、Loki、K8s API、部署事件、服务拓扑），生成多个根因假设，逐个验证，然后用确定性公式打分。关键设计决策是：当两个假设分数太接近时，保留两个结果，而不是强制选一个。
>
> 架构是 Maven 多模块（15 个模块）——核心引擎零 Spring 依赖，CLI、REST API 和 Live Scenario 都是薄适配层，调用同一个 workflow。所有领域对象都是 Java 21 record。1194 个测试覆盖完整流水线。
>
> LLM 集成严格限定为 advisory-only。Phase 4 接入了真实 LLM 客户端（OpenAiCompatibleLlmClient），已用智谱 glm-4-flash 端到端验证。LLM 可以提议额外假设，但始终标记为 advisoryOnly=true、canAffectDecision=false。LlmEnhancedReport 把确定性 base 字段和 LLM advisory 字段严格分开——所以 LLM 可以叙述发现，但永远不能改变根因决策。"

---

## Bullet Variants for Different Resume Contexts

### For SRE / Platform Role

- Designed verification-first RCA workflow: evidence → hypothesis → verification → confidence scoring → competing hypothesis comparison
- Built Java 21 Maven multi-module architecture (15 modules) with zero-framework core and CLI/REST/Live Scenario/Web UI adapters
- Implemented deterministic confidence scoring with 5-level decision policy and full Event Trace auditability
- Added sre-agent-llm advisory-only LLM layer: OpenAiCompatibleLlmClient (E2E verified), LlmHypothesisProposerImpl — LLM proposes hypotheses but cannot alter decisions (advisoryOnly=true)

### SRE / 平台方向

- 设计 verification-first RCA 工作流：证据 → 假设 → 验证 → 置信度评分 → 竞争假设比较
- 构建 Java 21 Maven 多模块架构（15 模块），核心零框架依赖，CLI/REST/Live Scenario/Web UI 作为适配层
- 实现确定性置信度评分，5 级决策策略，完整 Event Trace 审计链
- 新增 sre-agent-llm advisory-only LLM 层：OpenAiCompatibleLlmClient（已 E2E 验证）、LlmHypothesisProposerImpl——LLM 提议假设但不影响决策（advisoryOnly=true）

### For AI / Agent Role

- Built SRE Agent with structured investigation workflow as alternative to LLM log chatbot approach
- Demonstrates principled LLM integration: deterministic workflow produces structured output, sre-agent-llm consumes it for synthesis and hypothesis proposal only
- Evidence-driven hypothesis verification with explainable confidence scores and competing hypothesis preservation
- Implemented OpenAiCompatibleLlmClient (Zhipu E2E verified), LlmHypothesisProposerImpl, LlmPromptBuilder with prompt guardrails, and MockLlmClient for deterministic testing across 1194 tests

### AI / Agent 方向

- 构建 SRE Agent，以结构化调查工作流替代 LLM 日志聊天机器人方案
- 体现原则性 LLM 集成：确定性工作流产出结构化输出，sre-agent-llm 仅用于合成和假设提议
- 基于证据的假设验证，可解释置信度评分，保留竞争假设
- 实现 OpenAiCompatibleLlmClient（智谱 E2E 验证）、LlmHypothesisProposerImpl、LlmPromptBuilder（含 prompt guardrails）和 MockLlmClient（保证 1194 个测试确定性运行）

### For Architect / Engineering Manager Role

- Designed hexagonal architecture: pure Java core with zero framework dependency, framework glue isolated in adapter modules
- Demonstrates separation of concerns: domain logic, LLM synthesis, delivery mechanism, and infrastructure across 15 modules
- Decision framework: 5-level investigation policy that preserves uncertainty when evidence is ambiguous
- LlmEnhancedReport enforces base vs advisory field separation — architectural guarantee that LLM output never contaminates deterministic decision data

### 架构师 / 工程经理方向

- 设计六边形架构：纯 Java 核心零框架依赖，框架胶水代码隔离在适配器模块中
- 体现关注点分离：15 个模块分别承载领域逻辑、LLM 合成、交付机制和基础设施
- 决策框架：5 级调查策略，证据模糊时保留不确定性而非强制结论
- LlmEnhancedReport 强制分离 base 与 advisory 字段——架构层面保证 LLM 输出不会污染确定性决策数据
