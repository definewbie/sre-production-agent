# Resume Bullets

---

## 中文简洁版

构建 verification-first AI SRE RCA Agent：以告警为入口，基于 evidence 和 diagnostic patterns 生成并验证多个根因假设，通过确定性置信度评分和假设比较输出可解释的 competing hypotheses decision、Markdown RCA report 和 Event Trace；采用 Java 21 + Spring Boot 3.x + Maven multi-module（core / llm / server / cli）实现，核心 RCA workflow 保持纯 Java、零框架依赖，同时支持 CLI、REST API 和 Web UI。

新增 sre-agent-llm 模块实现 advisory-only LLM 合成层：LlmPromptBuilder 构建带 guardrails 的提示词，LlmReportSynthesizer 将确定性 RCA 结果交由 LLM 生成可读摘要，LlmEnhancedReport 严格分离 base（确定性）与 LLM（ advisory）字段；MockLlmClient 保证 111 个测试全部可重复、无外部依赖。LLM 仅用于叙事合成，不参与决策。

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
- Maven multi-module（core / llm / server / cli），核心模块零 Spring 依赖
- CLI 和 REST API 共享同一 InvestigationWorkflow，确保行为一致性
- 所有领域对象使用 Java 21 record，不可变且类型安全
- 111 个单元测试覆盖完整 workflow

技术栈：Java 21, Spring Boot 3.3.6, Maven, Jackson, Picocli, JUnit 5, AssertJ
LLM 集成（sre-agent-llm 模块）：
- Advisory-only 设计：LLM 不参与根因决策，仅对确定性 RCA 结果进行叙事合成（report synthesis）
- LlmPromptBuilder 构建结构化提示词，内置 prompt guardrails 限制 LLM 输出范围
- LlmReportSynthesizer 调用 LLM 生成可读摘要，输出写入 LlmEnhancedReport
- LlmEnhancedReport 严格分离 base* 字段（确定性引擎输出）与 LLM 字段（advisory 合成内容），确保审计链完整
- MockLlmClient 实现确定性行为，保证 111 个测试全部可重复运行、无外部 API 依赖
- 项目现为 4 模块架构（core / llm / server / cli），模块间依赖清晰

---

## English Concise

Built a verification-first AI SRE RCA Agent (Java 21 + Spring Boot + Maven multi-module: core/llm/server/cli) that generates, verifies, and scores competing root cause hypotheses from alert-driven evidence, outputting auditable Markdown reports and Event Traces. Core workflow has zero framework dependency and runs identically via CLI, REST API, and Web UI.

Added sre-agent-llm module providing advisory-only LLM synthesis: LlmPromptBuilder with prompt guardrails, LlmReportSynthesizer for narrative generation, and LlmEnhancedReport cleanly separating base (deterministic) vs LLM (advisory) fields. MockLlmClient enables 111 fully deterministic, no-network tests. LLM is strictly forbidden from altering investigation decisions.

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
- Maven multi-module (core / llm / server / cli) with zero Spring dependency in the core RCA engine
- CLI (Picocli) and REST API (Spring Boot) share the same `InvestigationWorkflow`, ensuring behavioral consistency
- All domain objects are Java 21 records — immutable and type-safe
- 111 unit tests covering the full workflow pipeline

**Tech Stack:** Java 21, Spring Boot 3.3.6, Maven multi-module, Jackson, Picocli, JUnit 5, AssertJ
**LLM Integration (sre-agent-llm module):**
- Advisory-only design: LLM never participates in root cause decisions — it only synthesizes narrative summaries from deterministic RCA output
- `LlmPromptBuilder` constructs structured prompts with built-in prompt guardrails to constrain LLM output scope
- `LlmReportSynthesizer` invokes the LLM client to produce human-readable report synthesis
- `LlmEnhancedReport` cleanly separates `base*` fields (deterministic engine output) from LLM-populated fields (advisory content), preserving full audit chain
- `MockLlmClient` provides deterministic responses, ensuring all 111 tests are repeatable with zero external API dependency
- Project is now a 4-module architecture (core / llm / server / cli) with clear dependency boundaries

---

## Interview Oral Version

> "I built an SRE RCA agent that takes a different approach from log chatbots. When a Kubernetes alert fires, it collects structured evidence, generates multiple root cause hypotheses, verifies each one, and scores them with a deterministic formula. The key design decision: when two hypotheses are too close to call, it preserves both instead of forcing a single answer.
>
> The architecture is a Maven multi-module build — core engine has zero Spring dependency, with CLI and REST API as thin adapters calling the same workflow. All domain objects are Java 21 records. I have 111 tests covering the full pipeline.
>
> The LLM integration is deliberately advisory-only. I added a dedicated sre-agent-llm module with prompt guardrails and a MockLlmClient for deterministic testing. The LlmEnhancedReport cleanly separates deterministic base fields from LLM advisory fields — so the LLM can narrate findings, but it can never change a root cause decision."

---

## 面试口述版

> "我做了一个 SRE RCA Agent，和常见的日志聊天机器人思路不同。Kubernetes 告警触发后，它会收集结构化证据，生成多个根因假设，逐个验证，然后用确定性公式打分。关键设计决策是：当两个假设分数太接近时，保留两个结果，而不是强制选一个。
>
> 架构是 Maven 多模块——核心引擎零 Spring 依赖，CLI 和 REST API 都是薄适配层，调用同一个 workflow。所有领域对象都是 Java 21 record。111 个测试覆盖完整流水线。
>
> LLM 集成严格限定为 advisory-only。我加了专门的 sre-agent-llm 模块，内置 prompt guardrails 和 MockLlmClient 保证确定性测试。LlmEnhancedReport 把确定性 base 字段和 LLM advisory 字段严格分开——所以 LLM 可以叙述发现，但永远不能改变根因决策。"

---

## Bullet Variants for Different Resume Contexts

### For SRE / Platform Role

- Designed verification-first RCA workflow: evidence → hypothesis → verification → confidence scoring → competing hypothesis comparison
- Built Java 21 Maven multi-module architecture (core/llm/server/cli) with zero-framework core and CLI/REST/Web UI adapters
- Implemented deterministic confidence scoring with 5-level decision policy and full Event Trace auditability
- Added sre-agent-llm advisory-only LLM synthesis layer — LLM generates narrative summaries but cannot alter investigation decisions

### SRE / 平台方向

- 设计 verification-first RCA 工作流：证据 → 假设 → 验证 → 置信度评分 → 竞争假设比较
- 构建 Java 21 Maven 多模块架构（core/llm/server/cli），核心零框架依赖，CLI/REST/Web UI 作为适配层
- 实现确定性置信度评分，5 级决策策略，完整 Event Trace 审计链
- 新增 sre-agent-llm advisory-only LLM 合成层——LLM 生成叙事摘要但不参与调查决策

### For AI / Agent Role

- Built SRE Agent with structured investigation workflow as alternative to LLM log chatbot approach
- Demonstrates principled LLM integration: deterministic workflow produces structured output, sre-agent-llm consumes it for synthesis only
- Evidence-driven hypothesis verification with explainable confidence scores and competing hypothesis preservation
- Implemented LlmPromptBuilder with prompt guardrails, LlmEnhancedReport separating base* vs LLM fields, and MockLlmClient for deterministic testing across 111 tests

### AI / Agent 方向

- 构建 SRE Agent，以结构化调查工作流替代 LLM 日志聊天机器人方案
- 体现原则性 LLM 集成：确定性工作流产出结构化输出，sre-agent-llm 仅用于合成
- 基于证据的假设验证，可解释置信度评分，保留竞争假设
- 实现 LlmPromptBuilder（含 prompt guardrails）、LlmEnhancedReport（分离 base* 与 LLM 字段）和 MockLlmClient（保证 111 个测试确定性运行）

### For Architect / Engineering Manager Role

- Designed hexagonal architecture: pure Java core with zero framework dependency, framework glue isolated in adapter modules
- Demonstrates separation of concerns: domain logic, LLM synthesis, delivery mechanism, and infrastructure concerns across 4 modules (core/llm/server/cli)
- Decision framework: 5-level investigation policy that preserves uncertainty when evidence is ambiguous
- LlmEnhancedReport enforces base vs advisory field separation — architectural guarantee that LLM output never contaminates deterministic decision data

### 架构师 / 工程经理方向

- 设计六边形架构：纯 Java 核心零框架依赖，框架胶水代码隔离在适配器模块中
- 体现关注点分离：4 个模块（core/llm/server/cli）分别承载领域逻辑、LLM 合成、交付机制和基础设施
- 决策框架：5 级调查策略，证据模糊时保留不确定性而非强制结论
- LlmEnhancedReport 强制分离 base 与 advisory 字段——架构层面保证 LLM 输出不会污染确定性决策数据
