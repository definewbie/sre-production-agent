# Interview Walkthrough: 10-Minute Guide

## Timing Overview

```
0:00–0:30   Project positioning (30-second pitch)
0:30–2:30   Architecture explanation (2 minutes)
2:30–5:30   Demo: Scenario E (3 minutes)
5:30–8:30   Deep dive: Verification / Confidence / Comparison (3 minutes)
8:30–9:30   LLM positioning (1 minute)
9:30–10:00  Roadmap and close (30 seconds)
```

---

## 0:00–0:30: 30-Second Pitch

> "I built a verification-first SRE RCA Agent. When a Kubernetes alert fires, it collects evidence, generates multiple root cause hypotheses, verifies each one against supporting and counter evidence, scores them with an explainable formula, and outputs an auditable investigation with a full event trace.
>
> This is **not** a log chatbot. It does not ask an LLM 'what went wrong.' It treats every conclusion as a hypothesis to be verified. And when two hypotheses are too close to call — like in the demo — it preserves both instead of forcing a single answer."

### Key Framing

- **Not a chatbot** — structured workflow, not free-form Q&A
- **Verification-first** — every claim is tested against evidence
- **Preserves uncertainty** — `competing_hypotheses` is a valid outcome
- **Fully auditable** — Event Trace records every step

---

## 0:30–2:30: Architecture Explanation

### Module Structure (30 seconds)

> "The project is a Maven multi-module build with three modules. `sre-agent-core` is the pure Java RCA engine — zero Spring dependency. `sre-agent-cli` is a Picocli command-line adapter. `sre-agent-server` is a Spring Boot REST API and minimal Web UI. Both adapters call the same `InvestigationWorkflow`."

### Core Workflow (1 minute)

> "The workflow has 10 steps. Load alert, load evidence, match diagnostic patterns, generate hypotheses — one per pattern — then verify each hypothesis against evidence, score confidence with a deterministic formula, compare hypotheses to find the leader and any competitors, generate a decision, write a Markdown report, and collect the full event trace."

Draw or point to:

```
Alert → Evidence → Patterns → Hypotheses → Verification
  → Confidence Scoring → Comparison → Decision → Report + Trace
```

### Why Zero-Spring Core (30 seconds)

> "Core has no Spring dependency because I want the RCA engine to be testable without a framework context, reusable across CLI and server and potentially Lambda, and the architecture demonstrates clean separation between domain logic and delivery mechanism."

### Key Domain Objects (30 seconds)

> "All domain objects are Java 21 records — immutable by default. `Evidence` has a type, source, and strength. `DiagnosticPattern` defines what evidence supports or counters a hypothesis, plus confidence weights. `VerificationResult` classifies evidence into supporting, counter, and missing. `ConfidenceResult` is the score with traceable factors. `InvestigationDecision` is the final call."

---

## 2:30–5:30: Demo Scenario E (3 minutes)

### Setup the Scenario (30 seconds)

> "Scenario E: order-service error rate spiked to 8.7% after a deployment. Payment-service P95 latency also increased from 120ms to 450ms. The question is — did the deployment cause it, or is the downstream dependency the problem?"

### Run CLI Demo (1 minute)

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

### Highlight Results (1 minute)

> "Three hypotheses generated. Deployment regression scores 0.64, downstream dependency latency scores 0.58, pod OOM killed scores 0.05.
>
> The gap between the top two is only 0.06. The agent outputs `competing_hypotheses` — it selected deployment regression as the leader but explicitly preserves downstream latency as a competing explanation.
>
> The report shows exactly what evidence supports each hypothesis, what contradicts each, and suggests next probes."

### Show Event Trace (30 seconds)

> "Every step is recorded — you can see when evidence was loaded, which hypotheses were generated, how many supporting and counter items each has, and the final decision. This is the audit trail."

---

## 5:30–8:30: Deep Dive (3 minutes)

### Verification Chain (1 minute)

> "Let me explain how verification works. Each `DiagnosticPattern` defines supporting evidence types and counter evidence types. `VerificationEngine` takes a hypothesis and classifies every evidence item as supporting, counter, or missing.
>
> For deployment regression, the supporting evidence is: deploy event near the alert window, error rate spike after deploy, timeout logs, and the git config change. Counter evidence is: downstream latency also spiked, and the same timeout errors existed before the deployment."

### Confidence Scoring (1 minute)

> "The scorer uses a deterministic formula. Start with the pattern's base score — 0.30 for deployment regression. Add confidence weights for each matched supporting evidence type — deploy event adds 0.12, error spike adds 0.10, timeout logs add 0.08, config change adds 0.12. Then subtract counter evidence weights. The result is clamped to 0.0–1.0.
>
> This is not ML. Every weight is manually assigned and explainable. The value is that you can trace exactly why a hypothesis scored 0.64 instead of 0.80."

### Hypothesis Comparison (1 minute)

> "The comparator has a decision policy. If the top hypothesis scores above 0.80 and the gap is above 0.15, it's a `likely_root_cause`. If both top hypotheses score above 0.50 and the gap is below 0.10, it's `competing_hypotheses`.
>
> In Scenario E, 0.64 vs 0.58 with gap 0.06 — that triggers competing hypotheses. The agent does not force a conclusion because the evidence doesn't support one."

---

## 8:30–9:30: LLM Positioning (1 minute)

> "The LLM is deliberately deferred to Step G. Right now, the entire workflow is deterministic — same input, same output, every step auditable. That's a feature, not a limitation.
>
> When I add LLM, it will only be for report synthesis — taking the structured `InvestigationResult` and turning it into a human-readable narrative. The LLM will not decide root cause, modify scores, invent evidence, or override the `InvestigationDecision`.
>
> The architecture already has the right boundary: the LLM consumes the investigation output. It doesn't participate in the investigation."

---

## 9:30–10:00: Roadmap and Close (30 seconds)

> "Next steps depend on the role. For an AI Agent position, I'd add LLM synthesis first. For an SRE role, I'd connect real Prometheus and Loki evidence providers. For an architect role, I'd focus on the trade-off narrative and the extension points.
>
> The architecture is designed for controlled extension — new evidence providers, new diagnostic patterns, new adapters — without modifying the core workflow."

### Closing Statement

> "The project demonstrates SRE domain knowledge, clean Java architecture, and a principled approach to AI integration. The key insight is that in incident investigation, preserving uncertainty is more valuable than forcing a confident wrong answer."

---

## 中文版

# 面试演示指南：10 分钟 walkthrough

## 时间概览

```
0:00–0:30   项目定位（30 秒 pitch）
0:30–2:30   架构讲解（2 分钟）
2:30–5:30   演示：场景 E（3 分钟）
5:30–8:30   深入解析：验证 / 置信度 / 对比（3 分钟）
8:30–9:30   LLM 定位（1 分钟）
9:30–10:00  路线图与总结（30 秒）
```

---

## 0:00–0:30：30 秒电梯演讲

> "我构建了一个验证优先的 SRE 根因分析 Agent。当 Kubernetes 告警触发时，它会收集证据，生成多个根因假设，针对支持证据和反面证据逐一验证，用可解释的公式进行评分，并输出一份附带完整事件追踪的可审计调查报告。
>
> 这**不是**一个日志聊天机器人。它不会问 LLM '出了什么问题'。它把每一条结论都当作需要验证的假设。当两个假设难以区分时——就像演示中那样——它会同时保留两者，而不是强行给出唯一答案。"

### 核心定位

- **不是聊天机器人**——结构化工作流，而非自由问答
- **验证优先**——每一条声明都经过证据检验
- **保留不确定性**——`competing_hypotheses` 是合法的输出结果
- **完全可审计**——Event Trace 记录每一步操作

---

## 0:30–2:30：架构讲解

### 模块结构（30 秒）

> "项目采用 Maven 多模块构建，包含三个模块。`sre-agent-core` 是纯 Java 的 RCA 引擎——零 Spring 依赖。`sre-agent-cli` 是基于 Picocli 的命令行适配器。`sre-agent-server` 是 Spring Boot REST API 和简易 Web UI。两个适配器都调用同一个 `InvestigationWorkflow`。"

### 核心工作流（1 分钟）

> "工作流共 10 个步骤：加载告警、加载证据、匹配诊断模式、生成假设（每个模式一个）、针对证据逐一验证假设、用确定性公式进行置信度评分、比较假设以找出领先者和竞争对手、生成决策、输出 Markdown 报告，以及收集完整的事件追踪。"

画图或指向：

```
Alert → Evidence → Patterns → Hypotheses → Verification
  → Confidence Scoring → Comparison → Decision → Report + Trace
```

### 为什么核心模块零 Spring 依赖（30 秒）

> "Core 没有 Spring 依赖，因为我希望 RCA 引擎无需框架上下文即可测试，可在 CLI、Server 以及潜在的 Lambda 中复用，而且这种架构体现了领域逻辑与交付机制之间的清晰分离。"

### 关键领域对象（30 秒）

> "所有领域对象都是 Java 21 record——默认不可变。`Evidence` 包含类型、来源和强度。`DiagnosticPattern` 定义哪些证据支持或反驳某个假设，以及置信度权重。`VerificationResult` 将证据分类为支持、反面和缺失。`ConfidenceResult` 是带可追溯因子的评分。`InvestigationDecision` 是最终判断。"

---

## 2:30–5:30：演示场景 E（3 分钟）

### 场景设置（30 秒）

> "场景 E：order-service 在一次部署后错误率飙升至 8.7%。payment-service 的 P95 延迟也从 120ms 上升到 450ms。问题是——是部署导致的，还是下游依赖的问题？"

### 运行 CLI 演示（1 分钟）

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

### 结果亮点（1 分钟）

> "生成了三个假设。部署回退得分 0.64，下游依赖延迟得分 0.58，Pod OOM 被杀得分 0.05。
>
> 前两名差距仅为 0.06。Agent 输出 `competing_hypotheses`——它选择了部署回退作为领先假设，但明确保留了下游延迟作为竞争性解释。
>
> 报告清晰展示了支持每个假设的证据、反驳每个假设的证据，并建议下一步探测方向。"

### 展示 Event Trace（30 秒）

> "每一步都被记录——你可以看到证据何时加载、生成了哪些假设、每个假设有多少支持和反驳项，以及最终决策。这就是审计轨迹。"

---

## 5:30–8:30：深入解析（3 分钟）

### 验证链（1 分钟）

> "让我解释验证的工作原理。每个 `DiagnosticPattern` 定义了支持证据类型和反面证据类型。`VerificationEngine` 接收一个假设，将每条证据分类为支持、反面或缺失。
>
> 对于部署回退假设，支持证据包括：告警窗口附近的部署事件、部署后的错误率飙升、超时日志、以及 git 配置变更。反面证据包括：下游延迟也同时飙升、相同的超时错误在部署前就已存在。"

### 置信度评分（1 分钟）

> "评分器使用确定性公式。从模式的基础分开始——部署回退的基础分是 0.30。加上每个匹配的支持证据类型的置信度权重——部署事件加 0.12、错误飙升加 0.10、超时日志加 0.08、配置变更加 0.12。然后减去反面证据权重。最终结果截断到 0.0–1.0。
>
> 这不是机器学习。每个权重都是手动设定且可解释的。其价值在于你可以精确追溯一个假设为什么得 0.64 而不是 0.80。"

### 假设比较（1 分钟）

> "比较器有一个决策策略。如果最高假设得分超过 0.80 且差距超过 0.15，则为 `likely_root_cause`。如果前两个假设得分都超过 0.50 且差距低于 0.10，则为 `competing_hypotheses`。
>
> 在场景 E 中，0.64 vs 0.58，差距 0.06——触发竞争假设。Agent 不会强行得出结论，因为证据不足以支持唯一结论。"

---

## 8:30–9:30：LLM 定位（1 分钟）

> "LLM 被刻意推迟到步骤 G。目前整个工作流是确定性的——相同输入，相同输出，每一步可审计。这是一个特性，而非局限。
>
> 当我加入 LLM 时，它只会用于报告合成——将结构化的 `InvestigationResult` 转化为人类可读的叙述。LLM 不会决定根因、修改评分、捏造证据或覆盖 `InvestigationDecision`。
>
> 架构已经划定了正确的边界：LLM 消费调查结果，它不参与调查过程。"

---

## 9:30–10:00：路线图与总结（30 秒）

> "下一步取决于职位方向。对于 AI Agent 岗位，我会先加 LLM 合成。对于 SRE 岗位，我会接入真实的 Prometheus 和 Loki 证据提供者。对于架构师岗位，我会聚焦权衡叙事和扩展点。
>
> 架构设计为可控扩展——新的证据提供者、新的诊断模式、新的适配器——无需修改核心工作流。"

### 结束语

> "这个项目展示了 SRE 领域知识、清晰的 Java 架构，以及有原则的 AI 集成方法。核心洞察是：在事件调查中，保留不确定性比强行给出一个自信的错误答案更有价值。"
