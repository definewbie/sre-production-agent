# LLM Positioning

## Why LLM Was Deferred — and Is Now Integrated

The LLM was deliberately deferred to Step G. This was not an oversight — it was a design decision that has now been fulfilled.

### Reason 1: Determinism First

In incident investigation, the same input must produce the same output. If the same alert fires twice at 3 AM, the on-call engineer needs the same conclusion both times. LLMs are non-deterministic by nature.

The deterministic workflow remains 100% deterministic. Same alert + same evidence = same hypotheses, same scores, same decision. Every time. The LLM layer sits **above** this — it receives the deterministic result as read-only input and produces advisory narrative only.

### Reason 2: Auditability Before Narrative

An LLM can write a beautiful, convincing RCA narrative. But if the underlying reasoning is wrong, the narrative just makes the wrong conclusion harder to question.

The system produces a structured `InvestigationResult` — you can see exactly which evidence supported each hypothesis, what the confidence weights are, and why the decision was made. The auditability is solid, and now the narrative layer is built on top of it.

### Reason 3: Correctness Validation

Before letting an LLM narrate findings, the deterministic workflow was validated against real incidents. The scoring logic was validated first; the LLM layer was added second.

The completed sequence:
1. ~~Validate deterministic workflow with real data~~ ✓
2. ~~Tune confidence weights based on human feedback~~ ✓
3. ~~Add LLM for narrative synthesis~~ ✓ (Step G — completed)

---

## What LLM Can Do

The LLM is positioned **after** the structured investigation:

```
Structured Investigation (deterministic)
  InvestigationResult
      ↓
LlmPromptBuilder (system prompt + structured user prompt)
      ↓
LlmClient (MockLlmClient or future real provider)
      ↓
LlmReportSynthesizer (orchestration + section extraction)
      ↓
LlmEnhancedReport (deterministic fields + advisory narrative)
```

Specifically, the LLM:

1. **Synthesize narrative.** Takes the structured `InvestigationResult` and produces a coherent, readable RCA report with sections: Executive Summary, Reasoning Narrative, Uncertainty Explanation, Next Steps, Limitations, and Unverified Proposals.

2. **Explain reasoning.** Translates why the selected hypothesis leads, why competing hypotheses remain plausible, and what contradictions exist — all grounded in the provided evidence.

3. **Preserve uncertainty.** When the deterministic decision is `competing_hypotheses`, the LLM narrative reflects this and does not claim certainty.

4. **Suggest next probes.** Based on the decision's `nextProbes`, the LLM suggests concrete investigation steps — labeled as **unverified proposals** to distinguish them from verified findings.

5. **Document limitations.** The LLM output explicitly states what evidence is missing and what future providers (K8s, Prometheus, Loki, etc.) may add.

---

## What LLM Cannot Do

These are hard boundaries, enforced by architecture:

1. **Cannot decide root cause.** Root cause selection is the `HypothesisComparator`'s job. The LLM does not vote.

2. **Cannot modify confidence scores.** Scores are computed deterministically from evidence weights. The LLM cannot adjust them.

3. **Cannot invent evidence.** Evidence comes from providers (Prometheus, Loki, K8s API). The LLM cannot fabricate evidence items.

4. **Cannot override InvestigationDecision.** The decision is the output of the workflow. The LLM receives it as input, it cannot change it.

5. **Cannot skip verification.** Every hypothesis must be verified before scoring. The LLM cannot bypass verification to jump to a conclusion.

6. **Cannot execute remediation.** The agent is read-only by design. The LLM can suggest actions, but never execute them.

---

## Guardrails

The architecture enforces LLM boundaries through **data flow** and **prompt constraints**:

### Data Flow Guardrails

```
InvestigationResult (output of deterministic workflow)
      ↓
LlmPromptBuilder wraps result in a constrained prompt
      ↓
LlmClient.complete() returns raw text
      ↓
LlmReportSynthesizer extracts sections
      ↓
LlmEnhancedReport — base* fields come from InvestigationResult, NEVER from LLM output
```

The LLM never touches:
- `HypothesisEngine` — hypothesis generation is pattern-driven
- `VerificationEngine` — evidence classification is rule-based
- `ConfidenceScorer` — scoring is formula-based
- `HypothesisComparator` — comparison is policy-based
- `InvestigationDecision` — decision is deterministic

### Prompt Guardrails (Enforced in System Prompt)

The `LlmPromptBuilder.SYSTEM_PROMPT` includes these constraints:

| Constraint | Enforcement |
|---|---|
| Must not infer a new final root cause | System prompt instruction |
| Must not change the decision | System prompt instruction |
| Must not change confidence scores | System prompt instruction |
| Must not invent evidence | System prompt instruction |
| Must not hide counter evidence | System prompt instruction |
| Must not claim certainty when decision is `competing_hypotheses` | System prompt instruction |
| Must preserve uncertainty | System prompt instruction |
| Must not invent K8s, EC2, RDS, ElastiCache, ALB, CMDB, or topology facts | System prompt instruction |
| Unverified proposals must be labeled separately | System prompt + output format |
| LLM can assist, but cannot adjudicate | System prompt instruction |

### Structural Guardrails

- `LlmEnhancedReport.baseDecisionType` — always from `InvestigationResult.decision()`, never parsed from LLM output
- `LlmEnhancedReport.baseSelectedHypothesisId` — always from deterministic result
- `LlmEnhancedReport.baseConfidenceScore` — always from deterministic result
- `LlmEnhancedReport.baseScoreGap` — always from deterministic result
- `LlmEnhancedReport.advisoryOnly` — always `true`
- `LlmEnhancedReport.evidenceScopeNote` — static text, not LLM-generated

---

## Step G Implementation (Completed)

### Module: `sre-agent-llm`

Step G is implemented in the `sre-agent-llm` module, which depends on `sre-agent-core` but has no upstream dependencies on it.

### Components

#### `LlmClient` Interface

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
```

- `LlmRequest` — record with `systemPrompt`, `userPrompt`, and optional `metadata` map
- `LlmResponse` — record with `content`, `provider`, and `mock` flag
- `MockLlmClient` — deterministic implementation that returns a pre-built Scenario E narrative without network access. Used as default when no real LLM is configured.
- `OpenAiCompatibleLlmClient` — **Phase 4 addition.** Production LLM client that connects to any OpenAI-compatible endpoint via environment variables (`LLM_PROVIDER`, `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`). Features smart URL construction that handles Zhipu, OpenAI, Azure, Ollama, and vLLM endpoints automatically. E2E verified with Zhipu glm-4-flash.

#### `LlmPromptBuilder`

Builds the constrained prompt from a deterministic `InvestigationResult`. The prompt has two parts:

**System prompt** — defines the LLM's role as "RCA reasoning and report synthesis assistant" with 10 constraint rules (see Guardrails table above).

**User prompt** — structured investigation data in 10 sections:

| Section | Content |
|---|---|
| Investigation Result for Synthesis | Incident ID, alert name, service, severity |
| Deterministic Decision | Decision type, selected hypothesis, confidence score, rationale, competing hypotheses |
| Hypothesis Scores | Per-hypothesis: ID, score, level, decision |
| Hypothesis Comparison | Leading hypothesis, score gap, near-tie flag, comparison summary |
| Verification Results | Per-hypothesis: supporting evidence IDs, counter evidence IDs, missing evidence, contradictions, explanation |
| Evidence Items | Per-item: ID, type, service, strength, content |
| Suggested Next Probes | From decision's `nextProbes` list |
| Event Trace Summary | Total event count, per-event: ID and type |
| Constraints | Reminder rules repeated in user prompt |
| Output Format | Required sections: Executive Summary, Reasoning Narrative, Uncertainty Explanation, Next Steps, Limitations, Unverified Proposals |

#### `LlmReportSynthesizer`

Orchestrates the synthesis flow:

1. `LlmPromptBuilder.build(result)` → `LlmRequest`
2. `LlmClient.complete(request)` → `LlmResponse`
3. Parse LLM response into sections (regex-based markdown heading extraction)
4. Build `LlmEnhancedReport` — deterministic fields from `InvestigationResult`, narrative fields from LLM output

#### `LlmEnhancedReport`

```java
public record LlmEnhancedReport(
    String incidentId,           // from InvestigationResult
    String baseDecisionType,     // from InvestigationResult — never from LLM
    String baseSelectedHypothesisId,  // from InvestigationResult — never from LLM
    double baseConfidenceScore,  // from InvestigationResult — never from LLM
    double baseScoreGap,         // from InvestigationResult — never from LLM
    String executiveSummary,     // from LLM output
    String reasoningNarrative,   // from LLM output
    String uncertaintyExplanation, // from LLM output
    String nextStepsExplanation, // from LLM output
    String limitations,          // from LLM output
    List<String> unverifiedProposals, // from LLM output
    String evidenceScopeNote,    // static — not from LLM
    String modelProvider,        // from LlmResponse
    boolean advisoryOnly         // always true
) {}
```

**Field descriptions:**

| Field | Source | Description |
|---|---|---|
| `incidentId` | Deterministic | Unique investigation identifier |
| `baseDecisionType` | Deterministic | The investigation decision type (e.g. `competing_hypotheses`) |
| `baseSelectedHypothesisId` | Deterministic | ID of the highest-scoring hypothesis |
| `baseConfidenceScore` | Deterministic | Confidence score of the selected hypothesis |
| `baseScoreGap` | Deterministic | Score difference between top two hypotheses |
| `executiveSummary` | LLM | 2-3 sentence overview of the incident and conclusion |
| `reasoningNarrative` | LLM | Detailed evidence-based explanation of why hypotheses scored as they did |
| `uncertaintyExplanation` | LLM | Plain-language explanation of why certainty cannot be claimed |
| `nextStepsExplanation` | LLM | Recommended investigation probes |
| `limitations` | LLM | Known gaps in current evidence and analysis |
| `unverifiedProposals` | LLM | Additional hypotheses or checks — explicitly labeled as unverified |
| `evidenceScopeNote` | Static | Disclaimer about evidence scope and future providers |
| `modelProvider` | LLM client | Which LLM provider generated the narrative (e.g. "mock") |
| `advisoryOnly` | Constant | Always `true` — LLM output does not override deterministic results |

### API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/investigations/scenario-e/llm-summary` | Run Scenario E investigation (if not cached), then synthesize LLM report |
| `POST` | `/api/investigations/{incidentId}/llm-summary` | Synthesize LLM report for a specific cached investigation |

Both endpoints return `LlmEnhancedReport` as JSON.

### Server Integration

`LlmSynthesisService` (in `sre-agent-server`) wires the components together:
- Defaults to `MockLlmClient` when `LLM_PROVIDER` env var is unset or "mock"
- **Phase 4: `OpenAiCompatibleLlmClient` activates when `LLM_PROVIDER=openai`** — supports Zhipu, OpenAI, Azure, Ollama, vLLM
- Configured via `LLM_PROVIDER`, `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` env vars
- Auto-runs Scenario E investigation if no cached result exists

`LiveScenarioService` (Phase 4 addition) adds two LLM integration paths:
1. `runLlmProposal()` — LLM proposes additional root cause hypotheses (advisoryOnly=true)
2. `runLlmReportSynthesis()` — LLM generates enhanced narrative report with Chinese support

### LLM Hypothesis Proposal (Phase 4)

`LlmHypothesisProposerImpl` is a new Phase 4 component that:
- Takes the collected evidence as context
- Sends it to the LLM via `LlmHypothesisProposalPromptBuilder` (Chinese SYSTEM_PROMPT)
- Parses LLM response into `UnverifiedHypothesisProposal` objects
- All proposals are marked `advisoryOnly=true` and `canAffectDecision=false`
- Falls back to `MockLlmHypothesisProposer` if LLM is unavailable or fails

### Test Coverage

1194 tests covering the full LLM layer: `LlmPromptBuilder`, `LlmReportSynthesizer`, `MockLlmClient`, `OpenAiCompatibleLlmClient`, `LlmHypothesisProposerImpl`, server integration, and API endpoint tests.

---

## Architecture Diagram (Updated)

```
┌─────────────────────────────────────────────────────────┐
│                    sre-agent-server                      │
│  ┌──────────────────────┐  ┌──────────────────────────┐ │
│  │ InvestigationController│  │ LlmSynthesisService      │ │
│  │  POST /scenario-e     │  │  resolves LlmClient      │ │
│  │  POST /scenario-e/    │──│  orchestrates synthesis   │ │
│  │    llm-summary        │  │  delegates to synthesizer │ │
│  │  GET  /{id}/report    │  └────────────┬─────────────┘ │
│  └──────────┬────────────┘               │               │
│             │                            │               │
└─────────────┼────────────────────────────┼───────────────┘
              │                            │
              ▼                            ▼
┌─────────────────────────┐  ┌─────────────────────────────┐
│    sre-agent-core       │  │      sre-agent-llm           │
│  (deterministic)        │  │  (advisory narrative layer)  │
│                         │  │                               │
│  InvestigationWorkflow  │  │  LlmClient (interface)        │
│    ├─ HypothesisEngine  │  │    ├─ MockLlmClient           │
│    ├─ VerificationEngine│  │    └─ OpenAiCompatible* ✅     │
│    ├─ ConfidenceScorer  │  │  LlmPromptBuilder             │
│    ├─ HypothesisCompar. │  │    └─ User prompt (structured)│
│    └─ MarkdownReporter  │  │  LlmReportSynthesizer         │
│                         │  │    └─ Section extraction       │
│                         │  │  LlmHypothesisProposerImpl ✅  │
│  InvestigationResult ───┼──▶  LlmEnhancedReport            │
│  (deterministic output) │  │    ├─ base* (from workflow)   │
│                         │  │    └─ narrative (from LLM)    │
└─────────────────────────┘  └─────────────────────────────┘
```

**Key invariant:** The arrow from `sre-agent-core` to `sre-agent-llm` is one-directional. The LLM module consumes `InvestigationResult` as read-only input. It never feeds data back into the deterministic workflow.

---

## When NOT to Use LLM

- **Real-time incident response** — latency budget doesn't allow an LLM call during active incident
- **Regulated environments** — where every conclusion must be fully deterministic and traceable
- **Low-confidence investigations** — when the decision is already `insufficient_evidence`, an LLM narrative won't help
- **Batch analysis** — when processing many historical incidents, deterministic scoring is faster and cheaper

The LLM adds value in **communication**, not in **investigation**. The investigation must be solid before the communication layer is added. Step G preserves this principle by making the LLM layer strictly advisory — it cannot change the investigation outcome, only narrate it.

---
## 中文版

# LLM 定位

## 为什么 LLM 被延后引入 — 以及现在如何集成的

LLM 被有意延后到 Step G 才引入。这不是疏忽 — 这是一个设计决策，现已实现。

### 原因一：确定性优先

在事件调查中，相同的输入必须产生相同的输出。如果同一个告警在凌晨 3 点触发两次，值班工程师需要两次都得到相同的结论。LLM 本质上是非确定性的。

确定性工作流保持 100% 确定性。相同的告警 + 相同的证据 = 相同的假设、相同的分数、相同的决策。每次都一样。LLM 层位于其**之上** — 它将确定性结果作为只读输入接收，仅生成咨询性叙述。

### 原因二：可审计性先于叙述

LLM 可以写出漂亮、有说服力的 RCA 叙述。但如果底层推理是错误的，叙述只会让错误结论更难被质疑。

系统产生结构化的 `InvestigationResult` — 你可以准确看到哪些证据支持了哪个假设、置信权重是什么，以及为什么做出这个决策。可审计性已经扎实，现在叙述层建立在它之上。

### 原因三：正确性验证

在让 LLM 叙述发现之前，确定性工作流先经过真实事件的验证。评分逻辑先被验证；LLM 层随后添加。

完成的顺序：
1. ~~用真实数据验证确定性工作流~~ ✓
2. ~~根据人工反馈调整置信权重~~ ✓
3. ~~添加 LLM 用于叙述综合~~ ✓（Step G — 已完成）

---

## LLM 能做什么

LLM 被定位在结构化调查**之后**：

```
Structured Investigation (deterministic)
  InvestigationResult
      ↓
LlmPromptBuilder (system prompt + structured user prompt)
      ↓
LlmClient (MockLlmClient or future real provider)
      ↓
LlmReportSynthesizer (orchestration + section extraction)
      ↓
LlmEnhancedReport (deterministic fields + advisory narrative)
```

具体来说，LLM 可以：

1. **综合叙述。** 将结构化的 `InvestigationResult` 转化为连贯、可读的 RCA 报告，包含以下章节：执行摘要、推理叙述、不确定性说明、后续步骤、局限性和未验证建议。

2. **解释推理过程。** 阐释为什么选中的假设领先、为什么竞争假设仍然合理、以及存在哪些矛盾 — 全部基于所提供的证据。

3. **保留不确定性。** 当确定性决策为 `competing_hypotheses` 时，LLM 叙述会如实反映这一点，不会声称确定。

4. **建议后续探测。** 基于决策的 `nextProbes`，LLM 建议具体的调查步骤 — 标记为**未验证建议**，以区别于已验证的发现。

5. **记录局限性。** LLM 输出明确指出缺少哪些证据，以及未来接入的 Provider（K8s、Prometheus、Loki 等）可能补充什么。

---

## LLM 不能做什么

这些是硬性边界，由架构强制执行：

1. **不能决定根因。** 根因选择是 `HypothesisComparator` 的工作。LLM 不参与投票。

2. **不能修改置信分数。** 分数由证据权重确定性计算得出。LLM 无法调整它们。

3. **不能捏造证据。** 证据来自 Provider（Prometheus、Loki、K8s API）。LLM 不能伪造证据条目。

4. **不能覆盖 InvestigationDecision。** 决策是工作流的输出。LLM 将其作为输入接收，无法更改。

5. **不能跳过验证。** 每个假设必须在评分前经过验证。LLM 不能绕过验证直接得出结论。

6. **不能执行修复。** Agent 按设计为只读。LLM 可以建议操作，但绝不执行。

---

## 护栏

架构通过**数据流**和**提示词约束**来执行 LLM 边界：

### 数据流护栏

```
InvestigationResult (output of deterministic workflow)
      ↓
LlmPromptBuilder wraps result in a constrained prompt
      ↓
LlmClient.complete() returns raw text
      ↓
LlmReportSynthesizer extracts sections
      ↓
LlmEnhancedReport — base* fields come from InvestigationResult, NEVER from LLM output
```

LLM 永远不会触及：
- `HypothesisEngine` — 假设生成基于模式驱动
- `VerificationEngine` — 证据分类基于规则
- `ConfidenceScorer` — 评分基于公式
- `HypothesisComparator` — 比较基于策略
- `InvestigationDecision` — 决策是确定性的

### 提示词护栏（在系统提示词中强制执行）

`LlmPromptBuilder.SYSTEM_PROMPT` 包含以下约束：

| 约束 | 执行方式 |
|---|---|
| 不得推断新的最终根因 | 系统提示词指令 |
| 不得更改决策 | 系统提示词指令 |
| 不得更改置信分数 | 系统提示词指令 |
| 不得捏造证据 | 系统提示词指令 |
| 不得隐瞒反证 | 系统提示词指令 |
| 当决策为 `competing_hypotheses` 时不得声称确定 | 系统提示词指令 |
| 必须保留不确定性 | 系统提示词指令 |
| 不得捏造 K8s、EC2、RDS、ElastiCache、ALB、CMDB 或拓扑事实 | 系统提示词指令 |
| 未验证建议必须单独标注 | 系统提示词 + 输出格式 |
| LLM 可以辅助，但不能裁决 | 系统提示词指令 |

### 结构性护栏

- `LlmEnhancedReport.baseDecisionType` — 始终来自 `InvestigationResult.decision()`，绝不从 LLM 输出解析
- `LlmEnhancedReport.baseSelectedHypothesisId` — 始终来自确定性结果
- `LlmEnhancedReport.baseConfidenceScore` — 始终来自确定性结果
- `LlmEnhancedReport.baseScoreGap` — 始终来自确定性结果
- `LlmEnhancedReport.advisoryOnly` — 始终为 `true`
- `LlmEnhancedReport.evidenceScopeNote` — 静态文本，非 LLM 生成

---

## Step G 实现（已完成）

### 模块：`sre-agent-llm`

Step G 在 `sre-agent-llm` 模块中实现，该模块依赖于 `sre-agent-core`，但对其没有上游依赖。

### 组件

#### `LlmClient` 接口

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
```

- `LlmRequest` — 包含 `systemPrompt`、`userPrompt` 和可选 `metadata` 映射的 record
- `LlmResponse` — 包含 `content`、`provider` 和 `mock` 标志的 record
- `MockLlmClient` — 确定性实现，返回预构建的 Scenario E 叙述，无需网络访问。当未配置真实 LLM 时作为默认使用。

#### `LlmPromptBuilder`

从确定性 `InvestigationResult` 构建受约束的提示词。提示词包含两部分：

**系统提示词** — 将 LLM 角色定义为"RCA 推理与报告综合助手"，包含 10 条约束规则（见上方护栏表格）。

**用户提示词** — 包含 10 个章节的结构化调查数据：

| 章节 | 内容 |
|---|---|
| Investigation Result for Synthesis | 事件 ID、告警名称、服务、严重程度 |
| Deterministic Decision | 决策类型、选中的假设、置信分数、理由、竞争假设 |
| Hypothesis Scores | 每个假设：ID、分数、等级、决策 |
| Hypothesis Comparison | 领先假设、分数差距、接近平局标志、比较摘要 |
| Verification Results | 每个假设：支持证据 ID、反证 ID、缺失证据、矛盾、说明 |
| Evidence Items | 每条证据：ID、类型、服务、强度、内容 |
| Suggested Next Probes | 来自决策的 `nextProbes` 列表 |
| Event Trace Summary | 事件总数、每个事件：ID 和类型 |
| Constraints | 在用户提示词中重复的提醒规则 |
| Output Format | 必需章节：执行摘要、推理叙述、不确定性说明、后续步骤、局限性、未验证建议 |

#### `LlmReportSynthesizer`

编排综合流程：

1. `LlmPromptBuilder.build(result)` → `LlmRequest`
2. `LlmClient.complete(request)` → `LlmResponse`
3. 将 LLM 响应解析为章节（基于正则的 Markdown 标题提取）
4. 构建 `LlmEnhancedReport` — 确定性字段来自 `InvestigationResult`，叙述字段来自 LLM 输出

#### `LlmEnhancedReport`

```java
public record LlmEnhancedReport(
    String incidentId,           // from InvestigationResult
    String baseDecisionType,     // from InvestigationResult — never from LLM
    String baseSelectedHypothesisId,  // from InvestigationResult — never from LLM
    double baseConfidenceScore,  // from InvestigationResult — never from LLM
    double baseScoreGap,         // from InvestigationResult — never from LLM
    String executiveSummary,     // from LLM output
    String reasoningNarrative,   // from LLM output
    String uncertaintyExplanation, // from LLM output
    String nextStepsExplanation, // from LLM output
    String limitations,          // from LLM output
    List<String> unverifiedProposals, // from LLM output
    String evidenceScopeNote,    // static — not from LLM
    String modelProvider,        // from LlmResponse
    boolean advisoryOnly         // always true
) {}
```

**字段说明：**

| 字段 | 来源 | 说明 |
|---|---|---|
| `incidentId` | 确定性 | 唯一调查标识符 |
| `baseDecisionType` | 确定性 | 调查决策类型（例如 `competing_hypotheses`） |
| `baseSelectedHypothesisId` | 确定性 | 最高分假设的 ID |
| `baseConfidenceScore` | 确定性 | 选中假设的置信分数 |
| `baseScoreGap` | 确定性 | 前两名假设之间的分数差距 |
| `executiveSummary` | LLM | 2-3 句话的事件和结论概述 |
| `reasoningNarrative` | LLM | 基于证据的详细解释，说明假设为何获得相应分数 |
| `uncertaintyExplanation` | LLM | 用通俗语言解释为何无法声称确定性 |
| `nextStepsExplanation` | LLM | 推荐的调查探测 |
| `limitations` | LLM | 当前证据和分析中的已知不足 |
| `unverifiedProposals` | LLM | 额外的假设或检查 — 明确标注为未验证 |
| `evidenceScopeNote` | 静态 | 关于证据范围和未来 Provider 的免责声明 |
| `modelProvider` | LLM 客户端 | 哪个 LLM 提供者生成了叙述（例如 "mock"） |
| `advisoryOnly` | 常量 | 始终为 `true` — LLM 输出不覆盖确定性结果 |

### API 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/investigations/scenario-e/llm-summary` | 运行 Scenario E 调查（如未缓存），然后综合 LLM 报告 |
| `POST` | `/api/investigations/{incidentId}/llm-summary` | 为特定已缓存的调查综合 LLM 报告 |

两个端点均返回 `LlmEnhancedReport` 的 JSON。

### 服务器集成

`LlmSynthesisService`（位于 `sre-agent-server`）将各组件连接在一起：
- 当 `LLM_PROVIDER` 环境变量未设置或为 "mock" 时，默认使用 `MockLlmClient`
- 通过 `LLM_PROVIDER`、`LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL` 环境变量支持未来的真实 Provider
- 如果没有缓存结果，自动运行 Scenario E 调查

### 测试覆盖

111 个测试覆盖完整的 LLM 层：`LlmPromptBuilder`、`LlmReportSynthesizer`、`MockLlmClient`、服务器集成和 API 端点测试。

---

## 架构图（已更新）

```
┌─────────────────────────────────────────────────────────┐
│                    sre-agent-server                      │
│  ┌──────────────────────┐  ┌──────────────────────────┐ │
│  │ InvestigationController│  │ LlmSynthesisService      │ │
│  │  POST /scenario-e     │  │  resolves LlmClient      │ │
│  │  POST /scenario-e/    │──│  orchestrates synthesis   │ │
│  │    llm-summary        │  │  delegates to synthesizer │ │
│  │  GET  /{id}/report    │  └────────────┬─────────────┘ │
│  └──────────┬────────────┘               │               │
│             │                            │               │
└─────────────┼────────────────────────────┼───────────────┘
              │                            │
              ▼                            ▼
┌─────────────────────────┐  ┌─────────────────────────────┐
│    sre-agent-core       │  │      sre-agent-llm           │
│  (deterministic)        │  │  (advisory narrative layer)  │
│                         │  │                               │
│  InvestigationWorkflow  │  │  LlmClient (interface)        │
│    ├─ HypothesisEngine  │  │    ├─ MockLlmClient           │
│    ├─ VerificationEngine│  │    └─ OpenAiCompatible* ✅     │
│    ├─ ConfidenceScorer  │  │  LlmPromptBuilder             │
│    ├─ HypothesisCompar. │  │    └─ User prompt (structured)│
│    └─ MarkdownReporter  │  │  LlmReportSynthesizer         │
│                         │  │    └─ Section extraction       │
│                         │  │  LlmHypothesisProposerImpl ✅  │
│  InvestigationResult ───┼──▶  LlmEnhancedReport            │
│  (deterministic output) │  │    ├─ base* (from workflow)   │
│                         │  │    └─ narrative (from LLM)    │
└─────────────────────────┘  └─────────────────────────────┘
```

**关键不变量：** 从 `sre-agent-core` 到 `sre-agent-llm` 的箭头是单向的。LLM 模块将 `InvestigationResult` 作为只读输入消费，永远不会将数据反馈回确定性工作流。

---

## 何时不使用 LLM

- **实时事件响应** — 活跃事件期间的延迟预算不允许 LLM 调用
- **受监管环境** — 每个结论都必须完全确定且可追溯的环境
- **低置信度调查** — 当决策已经是 `insufficient_evidence` 时，LLM 叙述不会有帮助
- **批量分析** — 处理大量历史事件时，确定性评分更快且更经济

LLM 在**沟通**中增加价值，而非在**调查**中。在添加沟通层之前，调查必须扎实。Step G 通过使 LLM 层严格为咨询性质来保持这一原则 — 它不能改变调查结果，只能叙述结果。
