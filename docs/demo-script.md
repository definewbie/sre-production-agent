# Demo Script

## Goal

Show the interviewer a working verification-first RCA agent in under 5 minutes.

The demo must demonstrate:
1. The agent processes a real Kubernetes-style alert
2. Multiple hypotheses are generated and scored
3. Competing hypotheses are preserved (not forced to a single answer)
4. Every step is auditable via Event Trace
5. The workflow runs identically in CLI and REST API

---

## Pre-Demo Setup

Run these **before the interview** to avoid live debugging:

```bash
# 1. Verify tests pass
cd ~/work/projects/sre-production-agent
source ~/.zshrc
mvn test
# Expected: 1194 tests passing

# 2. Build CLI jar
mvn -pl sre-agent-cli package -DskipTests
# Should complete without errors

# 2.1 Install LLM jar
mvn -pl sre-agent-llm install -DskipTests

# 2.2 (Optional) Set LLM environment variables for real LLM
# export LLM_PROVIDER=openai-compatible
# export LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4
# export LLM_API_KEY=your-api-key
# export LLM_MODEL=glm-4-flash

# 3. Verify JSON data files
python3 -m json.tool examples/alerts/competing_hypotheses.json > /dev/null
python3 -m json.tool examples/evidence/competing_hypotheses.json > /dev/null
# Should produce no errors

# 4. Start server (in a separate terminal)
mvn -pl sre-agent-server spring-boot:run
# Wait for "Started SreAgentApplication" log line

# 5. Verify health
curl -s http://localhost:8080/health
# Expected: {"status":"UP"}
```

---

## Demo Part 1: CLI (2 minutes)

### Step 1: Show the input files

```bash
cat examples/alerts/competing_hypotheses.json
```

**Say:** "This is a Kubernetes-style alert. Order-service error rate exceeded 5%. In a real system, this would come from Prometheus AlertManager."

```bash
cat examples/evidence/competing_hypotheses.json | python3 -m json.tool | head -30
```

**Say:** "Here's the evidence — 8 items collected from deploy logs, metrics, git diff, and service topology. Each item has a type, source, and strength score."

### Step 2: Run the CLI

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

### Step 3: Highlight the output

**Say:** "Notice three hypotheses were generated. Deployment regression scored 0.64, downstream dependency latency scored 0.58. The gap is only 0.06."

**Key point:** "The agent outputs `competing_hypotheses` instead of forcing a single root cause. This is the verification-first design — the agent knows when it doesn't know."

### Step 4: Show the event trace

The `--show-trace` flag prints the full investigation audit log.

**Say:** "Every step is recorded — evidence loading, hypothesis generation, verification, scoring, comparison, and decision. This is the Event Trace."

### Step 5: Show the Markdown report

```bash
cat /tmp/rca-report.md | head -50
```

**Say:** "The report shows why each hypothesis leads, what counter evidence exists, contradictions, and suggested next probes."

---

## Demo Part 2: REST API (2 minutes)

### Step 1: Run Scenario E via API

```bash
curl -s -X POST http://localhost:8080/api/investigations/scenario-e | python3 -m json.tool
```

**Say:** "Same workflow, different interface. The REST API returns a structured JSON response with decision type, confidence scores, and competing hypotheses."

**Highlight:**
```json
{
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06
}
```

### Step 2: Get the Markdown report

```bash
INCIDENT_ID=$(curl -s -X POST http://localhost:8080/api/investigations/scenario-e | python3 -c "import sys,json; print(json.load(sys.stdin)['incidentId'])")
curl -s http://localhost:8080/api/investigations/$INCIDENT_ID/report | head -30
```

### Step 3: Get the event trace

```bash
curl -s http://localhost:8080/api/investigations/$INCIDENT_ID/trace | python3 -m json.tool | head -40
```

---

## Demo Part 2.5: LLM-Enhanced Report Demo — 真实 LLM 接入 (1 minute)

### Step 1: Generate LLM summary via curl (真实 LLM)

```bash
# 使用真实 LLM（需要设置 LLM 环境变量，见 Pre-Demo Setup #2.2）
# runLlm=true 参数触发 OpenAiCompatibleLlmClient 调用真实 LLM API
curl -s -X POST "http://localhost:8080/api/investigations/scenario-e/llm-summary?runLlm=true" | python3 -m json.tool
```

**Say:** "Now we trigger the LLM-assisted explanation with a **real LLM backend**. The `runLlm=true` parameter activates `OpenAiCompatibleLlmClient`, which calls the configured LLM API (e.g., GLM-4-flash via BigModel). The system prompt enforces guardrails — the LLM can only narrate, never decide. Notice it returns structured fields — executive summary, reasoning narrative, uncertainty explanation, next steps, and limitations."

**Highlight key fields in the response:**
```json
{
  "executiveSummary": "Two competing hypotheses remain unresolved...",
  "reasoningNarrative": "The deployment regression hypothesis is supported by...",
  "uncertaintyExplanation": "Score gap of 0.06 indicates insufficient evidence...",
  "nextSteps": ["Collect pre-deployment baseline metrics...", "Check downstream service health..."],
  "limitations": ["LLM did not verify claims against raw evidence...", "Proposed next steps are unverified..."],
  "status": "ADVISORY",
  "modelProvider": "glm-4-flash"
}
```

**Say:** "The `status` field is `ADVISORY` — not `AUTHORATIVE` — because the LLM did not independently verify these claims against raw evidence. The `modelProvider` field shows which LLM generated this output — in this case, `glm-4-flash` via the OpenAI-compatible API."

### Step 2: Fallback to mock if LLM unavailable

```bash
# 如果 LLM API 不可用，不带 runLlm 参数使用 MockLlmClient
curl -s -X POST "http://localhost:8080/api/investigations/scenario-e/llm-summary" | python3 -m json.tool
```

**Say:** "Without `runLlm=true`, the system falls back to `MockLlmClient` — a deterministic response. This graceful degradation ensures the demo works even without an LLM API key."

### Step 3: Show the LLM section in the Web UI

1. Navigate to http://localhost:8080/
2. Click **"Run Scenario E"** if not already done
3. Click the **"Generate LLM-assisted Explanation"** button in the investigation detail view

**Say:** "In the UI, click the 'Generate LLM-assisted Explanation' button. The LLM section appears below the deterministic scores and event trace."

**Point out these UI elements:**
- **Authoritative / Advisory badge** — clearly labels the LLM output as advisory-only
- **Executive Summary card** — plain-language overview of the incident
- **Reasoning Narrative** — walks through why each hypothesis scored the way it did
- **Uncertainty Explanation** — explicitly states what the agent does NOT know
- **Next Steps** — actionable suggestions, each marked as an unverified proposal
- **Limitations** — honest disclosure of what the LLM can and cannot guarantee

### Step 4: Explain the guardrails

**Say (key talking points):**

1. **Advisory-only design:** "The LLM never modifies scores, never selects a root cause, and never overrides the deterministic pipeline. It only synthesizes the existing results into human-readable narrative."

2. **Deterministic baseline preserved:** "The core scoring engine remains fully deterministic — same input, same output, every step auditable. The LLM layer is additive, not replacement."

3. **MockLlmClient for demo:** "Right now we're using a MockLlmClient that returns a deterministic response. In production, this would call a real LLM. Phase 4 (cdaecac) added `OpenAiCompatibleLlmClient` — a real LLM client supporting any OpenAI-compatible API. With `runLlm=true`, the system calls GLM-4-flash for real-time synthesis. The guardrails — advisory badges, limitations section, no score modification — remain the same regardless of which client is active."

4. **Two-tier trust model:** "The investigation report has two tiers: the **Authoritative** tier (deterministic scores, evidence, event trace) and the **Advisory** tier (LLM narrative, next steps, explanations). The UI makes this distinction visually clear with badges."

### Step 5: Also show per-investigation LLM endpoint

```bash
# If you have a specific investigation ID:
curl -s -X POST http://localhost:8080/api/investigations/{id}/llm-summary | python3 -m json.tool
```

**Say:** "You can also request an LLM summary for any previously completed investigation by ID. This means on-call engineers can retroactively generate explanations for historical incidents."

### LLM Demo Fallback Plan

If the LLM endpoint is unavailable or returns an error:

1. **Show the static mock response** — run: `cat docs/llm-example-response.json` (pre-generated example)
2. **Explain the architecture** — point out `OpenAiCompatibleLlmClient` in the codebase: `cat sre-agent-llm/src/main/java/ai/sreagent/llm/client/OpenAiCompatibleLlmClient.java`
3. **Focus on the design** — "The important thing isn't the LLM output itself, but the guardrails around it — advisory badges, no score modification, explicit limitations. The real LLM client (`OpenAiCompatibleLlmClient`) uses the same guardrails as `MockLlmClient`."
4. **Fall back to the core demo** — "The deterministic pipeline is the star of the show. The LLM layer is an optional enhancement that degrades gracefully — `MockLlmClient` kicks in automatically."

---

## Demo Part 2.6: Live E2E — Latency Scenario with Real K8s Evidence (1 minute)

> **Prerequisite:** Kind cluster running with demo services deployed, observability stack active (see `scripts/observability/`).

### Step 1: Inject latency fault

```bash
curl -s -X POST http://localhost:8080/api/demo-services/fault/payment-latency
```

**Say:** "I inject a 1500ms latency fault into payment-service. Prometheus will start scraping elevated p99 values."

Wait ~30 seconds for Prometheus to scrape.

### Step 2: Run live RCA

```bash
curl -s -X POST "http://localhost:8080/api/live-scenario/run?scenarioId=latency" | python3 -c "
import json,sys
d = json.load(sys.stdin)
rca = d.get('baseRca', d)
scores = rca.get('hypothesisScores', {})
for h, s in sorted(scores.items(), key=lambda x: -x[1]):
    print(f'  {s:.2f}  {h}')
"
```

**Say:** "The agent collected real-time evidence from Prometheus, Loki, and Kubernetes. Look at the ranking:

  0.50  downstream_dependency_latency   ← correct root cause
  0.19  pod_oom_killed
  0.12  deployment_regression
  0.09  pod_crash_loop                  ← correctly ranked last

`downstream_dependency_latency` ranks #1 at 0.50. `pod_crash_loop` is last at 0.09. This is the correct result.

Before the semantic typing fix, `pod_crash_loop` incorrectly ranked #1 because Kubernetes evidence was generically typed. The fix: events are now classified by reason, crash loop only fires on real CrashLoopBackOff, and healthy pods produce counter signals. No scoring algorithm changes were needed — just better evidence precision."

### Step 3: Clear fault

```bash
curl -s -X POST http://localhost:8080/api/demo-services/fault/clear
```

**Key talking point:** "This demonstrates a critical SRE principle: **evidence quality determines RCA quality.** The fix didn't touch any scoring constants — it only improved how Kubernetes evidence is classified."

---

## Demo Part 3: Web UI — 中文报告 + LLM Proposal 卡片 + 真实 LLM（1.5 分钟）

### 步骤 1：打开浏览器

导航到 http://localhost:8080/

### 步骤 2：点击 "Run Scenario E"

**讲解：** "Web UI 展示了相同的结果——注意决策类型现在显示为中文'竞争假设'，假设标题也是中文。"

### 步骤 3：指出 Phase 4 新增 UI 元素

- **决策卡片**——显示 **"竞争假设"**（Phase 4 中文化映射），分数差距 = 0.06
- **评分柱状图**——"近期部署引入了回归缺陷"（绿色/领先）、"下游依赖延迟导致服务降级"（黄色/竞争）、"Pod OOMKilled 或资源超限"（灰色/证据不足）
- **Markdown 报告**——现在输出**中文报告**（"竞争假设分析报告"），包含概要/调查时间线/假设评分/调查决策
- **LLM Proposal 卡片**——如果触发了 LLM 假设提案，会在下方显示卡片：提案标题、推理过程、观测信号、验证计划、置信度、状态
- **LLM 真实响应标识**——使用真实 LLM 时，LLM 区域显示 `modelProvider` 标签（如 "glm-4-flash"），与 Mock Provider 明确区分

### 步骤 4：展示实时排查控制台（可选）

点击右上角 **"🔍 实时排查"** 按钮：

**讲解：** "这是 Step V 新增的中文调查控制台。可以选择故障模式（延迟/错误/超时/混合），选择运行模式（模拟/实时），然后一键触发多信号 RCA。"

**API 演示：**
```bash
# 模拟模式（无需 live 端点）
curl -s http://localhost:8080/api/live-scenario/simulate | python3 -m json.tool

# 实时模式（需要 kind + demo services）
curl -s -X POST http://localhost:8080/api/live-scenario/run | python3 -m json.tool
```

---

## What to Say While Showing Scenario E

### The Setup (30 seconds)

"This is a verification-first RCA agent. When an alert fires, it collects evidence, generates hypotheses, verifies each one, and scores them. The key design decision: it preserves uncertainty."

### The Competing Hypotheses (30 seconds)

"In Scenario E, two hypotheses score very close — 0.64 and 0.58. The gap is only 0.06. Instead of forcing a single root cause, the agent outputs `competing_hypotheses` and suggests next probes. This is what a real on-call engineer needs — not a confident wrong answer."

### The Verification Chain (30 seconds)

"Every hypothesis is verified against supporting and counter evidence. The deployment regression hypothesis has 4 supporting items — deploy event, error spike, timeout logs, and a config change in git. But it also has counter evidence — the same timeout logs existed before the deployment, and downstream latency also spiked."

### The LLM Layer (30 seconds)

"The LLM layer is now integrated for report synthesis. It narrates the findings in plain language — executive summary, reasoning, uncertainty, next steps — but it never decides root cause and never modifies scores. The deterministic scoring pipeline remains the authoritative source of truth. The LLM output is always labeled **Advisory**, and the UI shows explicit limitations and unverified-proposal markers."

---

## Expected Outputs

### CLI

```
Incident created
Evidence loaded
Hypotheses generated: 3
...
Decision: 竞争假设
Selected: hyp_deployment_regression (0.64) — "近期部署引入了回归缺陷"
Competing: hyp_downstream_dependency_latency (0.58) — "下游依赖延迟导致服务降级"
Score gap: 0.06
Report written to: /tmp/rca-report.md
LLM Summary: available (modelProvider: glm-4-flash)
```

### REST API

```json
{
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06,
  "scores": {
    "hyp_deployment_regression": 0.64,
    "hyp_downstream_dependency_latency": 0.58,
    "hyp_pod_oom_killed": 0.05
  }
}
```

---

## Fallback Plan

If the Spring Boot server fails to start:

1. **Skip the API and UI demo** — the CLI demo covers the same workflow
2. Show the pre-generated report: `cat examples/reports/competing_hypotheses_report.md`
3. Walk through the architecture diagram on paper/whiteboard

If Java/build fails:

1. Show the pre-generated report from `examples/reports/`
2. Walk through source code structure in an IDE
3. Focus the conversation on architecture and design decisions

If the real LLM API is unavailable:

1. Run the demo without `runLlm=true` — `MockLlmClient` provides deterministic fallback
2. Show `OpenAiCompatibleLlmClient` source code as proof of real LLM integration
3. Explain the guardrails are identical for both mock and real clients
4. Focus on the deterministic pipeline — "The LLM is advisory-only; the core scoring is fully deterministic"

If nothing works:

1. Open `docs/architecture.md` and walk through the diagrams
2. Discuss the design principles and trade-offs
3. The documentation alone tells the story

---

## 中文版

## 演示目标

在 5 分钟内向面试官展示一个可工作的验证优先型 RCA（根因分析）Agent。

演示必须展示以下要点：
1. Agent 能够处理真实的 Kubernetes 风格告警
2. 生成多个假设并对其进行评分
3. 保留竞争性假设（不强制给出唯一答案）
4. 每一步均可通过事件追踪（Event Trace）进行审计
5. 工作流在 CLI 和 REST API 中运行方式完全一致

---

## 演示前准备

请在**面试前**执行以下操作，避免现场调试：

```bash
# 1. 验证测试通过
cd ~/work/projects/sre-production-agent
source ~/.zshrc
mvn test
# 预期结果：1194 个测试全部通过

# 2. 构建 CLI jar 包
mvn -pl sre-agent-cli package -DskipTests
# 应无错误完成

# 2.1 安装 LLM jar 包
mvn -pl sre-agent-llm install -DskipTests

# 2.2 （可选）设置真实 LLM 环境变量
# export LLM_PROVIDER=openai-compatible
# export LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4
# export LLM_API_KEY=your-api-key
# export LLM_MODEL=glm-4-flash

# 3. 验证 JSON 数据文件
python3 -m json.tool examples/alerts/competing_hypotheses.json > /dev/null
python3 -m json.tool examples/evidence/competing_hypotheses.json > /dev/null
# 应无错误输出

# 4. 启动服务器（在单独的终端中）
mvn -pl sre-agent-server spring-boot:run
# 等待出现 "Started SreAgentApplication" 日志行

# 5. 验证健康状态
curl -s http://localhost:8080/health
# 预期结果：{"status":"UP"}
```

---

## 演示第一部分：CLI（2 分钟）

### 步骤 1：展示输入文件

```bash
cat examples/alerts/competing_hypotheses.json
```

**讲解：** "这是一个 Kubernetes 风格的告警。Order-service 的错误率超过了 5%。在实际系统中，这个告警会来自 Prometheus AlertManager。"

```bash
cat examples/evidence/competing_hypotheses.json | python3 -m json.tool | head -30
```

**讲解：** "这里是证据——共 8 项，收集自部署日志、指标、git diff 和服务拓扑。每一项都有类型、来源和强度评分。"

### 步骤 2：运行 CLI

```bash
java -jar sre-agent-cli/target/sre-agent-cli-0.1.0-SNAPSHOT.jar \
  investigate \
  --alert examples/alerts/competing_hypotheses.json \
  --evidence examples/evidence/competing_hypotheses.json \
  --output /tmp/rca-report.md \
  --show-trace
```

### 步骤 3：重点讲解输出

**讲解：** "注意生成了三个假设。部署回归（deployment regression）得分 0.64，下游依赖延迟得分 0.58。差距仅为 0.06。"

**关键要点：** "Agent 输出了 `competing_hypotheses` 而不是强制给出单一根因。这就是验证优先的设计理念——Agent 知道自己何时不确定。"

### 步骤 4：展示事件追踪

`--show-trace` 参数会打印完整的调查审计日志。

**讲解：** "每一步都被记录——证据加载、假设生成、验证、评分、比较和决策。这就是事件追踪（Event Trace）。"

### 步骤 5：展示 Markdown 报告

```bash
cat /tmp/rca-report.md | head -50
```

**讲解：** "报告展示了每个假设领先的原因、存在的反证、矛盾之处，以及建议的下一步探测方向。"

---

## 演示第二部分：REST API（2 分钟）

### 步骤 1：通过 API 运行场景 E

```bash
curl -s -X POST http://localhost:8080/api/investigations/scenario-e | python3 -m json.tool
```

**讲解：** "同样的工作流，不同的接口。REST API 返回结构化的 JSON 响应，包含决策类型、置信度分数和竞争性假设。"

**重点展示：**
```json
{
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06
}
```

### 步骤 2：获取 Markdown 报告

```bash
INCIDENT_ID=$(curl -s -X POST http://localhost:8080/api/investigations/scenario-e | python3 -c "import sys,json; print(json.load(sys.stdin)['incidentId'])")
curl -s http://localhost:8080/api/investigations/$INCIDENT_ID/report | head -30
```

### 步骤 3：获取事件追踪

```bash
curl -s http://localhost:8080/api/investigations/$INCIDENT_ID/trace | python3 -m json.tool | head -40
```

---

## 演示第二部分（补充）：LLM 增强报告演示 — 真实 LLM 接入（1 分钟）

### 步骤 1：通过 curl 生成 LLM 摘要（真实 LLM）

```bash
# 使用真实 LLM（需要设置 LLM 环境变量，见演示前准备 #2.2）
# runLlm=true 参数触发 OpenAiCompatibleLlmClient 调用真实 LLM API
curl -s -X POST "http://localhost:8080/api/investigations/scenario-e/llm-summary?runLlm=true" | python3 -m json.tool
```

**讲解：** "现在我们使用**真实 LLM 后端**触发 LLM 辅助解释。`runLlm=true` 参数激活 `OpenAiCompatibleLlmClient`，调用配置的 LLM API（如通过 BigModel 的 GLM-4-flash）。系统提示词强制执行安全防护——LLM 只能叙述，不能决策。注意它返回的是结构化字段——执行摘要、推理叙述、不确定性解释、后续步骤和局限性说明。"

**重点展示响应中的关键字段：**
```json
{
  "executiveSummary": "Two competing hypotheses remain unresolved...",
  "reasoningNarrative": "The deployment regression hypothesis is supported by...",
  "uncertaintyExplanation": "Score gap of 0.06 indicates insufficient evidence...",
  "nextSteps": ["Collect pre-deployment baseline metrics...", "Check downstream service health..."],
  "limitations": ["LLM did not verify claims against raw evidence...", "Proposed next steps are unverified..."],
  "status": "ADVISORY",
  "modelProvider": "glm-4-flash"
}
```

**讲解：** "`status` 字段是 `ADVISORY`（建议性的）——而非 `AUTHORATIVE`（权威性的）——因为 LLM 没有独立根据原始证据验证这些声明。`modelProvider` 字段显示是哪个 LLM 生成了此输出——在本例中是 `glm-4-flash`，通过 OpenAI 兼容 API 调用。"

### 步骤 2：LLM 不可用时回退到 Mock

```bash
# 如果 LLM API 不可用，不带 runLlm 参数使用 MockLlmClient
curl -s -X POST "http://localhost:8080/api/investigations/scenario-e/llm-summary" | python3 -m json.tool
```

**讲解：** "不带 `runLlm=true` 时，系统回退到 `MockLlmClient`——返回确定性响应。这种优雅降级确保即使没有 LLM API Key，演示也能正常运行。"

### 步骤 3：在 Web UI 中展示 LLM 部分

1. 导航到 http://localhost:8080/
2. 如尚未运行，点击 **"Run Scenario E"**
3. 在调查详情视图中点击 **"Generate LLM-assisted Explanation"** 按钮

**讲解：** "在 UI 中，点击 'Generate LLM-assisted Explanation' 按钮。LLM 部分会出现在确定性评分和事件追踪的下方。"

**指出以下 UI 元素：**
- **权威/建议标签（Authoritative / Advisory badge）**——清晰标注 LLM 输出仅为建议性质
- **执行摘要卡片（Executive Summary card）**——用通俗语言概述事件
- **推理叙述（Reasoning Narrative）**——逐步解释每个假设为何得到相应评分
- **不确定性解释（Uncertainty Explanation）**——明确说明 Agent 不知道的内容
- **后续步骤（Next Steps）**——可操作的建议，每条都标记为未经验证的提案
- **局限性说明（Limitations）**——坦诚披露 LLM 能做什么和不能做什么

### 步骤 4：解释安全防护机制

**讲解（关键谈话要点）：**

1. **仅建议性设计：** "LLM 永远不会修改分数，永远不会选择根因，也永远不会覆盖确定性流程。它只是将现有结果综合成人类可读的叙述。"

2. **确定性基线完整保留：** "核心评分引擎保持完全确定性——相同输入，相同输出，每一步可审计。LLM 层是附加的，而非替代的。"

3. **演示使用 MockLlmClient 和真实 LLM：** "目前默认使用 MockLlmClient 返回确定性响应。Phase 4 (cdaecac) 新增了 `OpenAiCompatibleLlmClient`——一个真实 LLM 客户端，支持任何 OpenAI 兼容 API。通过 `runLlm=true` 参数，系统调用 GLM-4-flash 进行实时综合。无论使用哪个客户端，安全防护机制——建议标签、局限性部分、不修改分数——完全一致。"

4. **双层信任模型：** "调查报告有两个层级：**权威（Authoritative）**层（确定性评分、证据、事件追踪）和**建议（Advisory）**层（LLM 叙述、后续步骤、解释）。UI 通过标签在视觉上清楚地区分这两层。"

### 步骤 5：同时展示按调查 ID 的 LLM 端点

```bash
# 如果你有特定的调查 ID：
curl -s -X POST http://localhost:8080/api/investigations/{id}/llm-summary | python3 -m json.tool
```

**讲解：** "你也可以通过 ID 为任何已完成的调查请求 LLM 摘要。这意味着值班工程师可以对历史事件追溯生成解释。"

### LLM 演示备用方案

如果 LLM 端点不可用或返回错误：

1. **展示静态模拟响应**——运行：`cat docs/llm-example-response.json`（预生成的示例）
2. **解释架构**——指出代码库中的 `OpenAiCompatibleLlmClient`：`cat sre-agent-llm/src/main/java/ai/sreagent/llm/client/OpenAiCompatibleLlmClient.java`
3. **聚焦设计**——"重要的不是 LLM 输出本身，而是围绕它的安全防护——建议标签、不修改分数、明确的局限性说明。真实 LLM 客户端（`OpenAiCompatibleLlmClient`）使用与 `MockLlmClient` 完全相同的安全防护机制。"
4. **回退到核心演示**——"确定性流程才是主角。LLM 层是一个可选的增强功能，能够优雅降级——`MockLlmClient` 会自动接管。"

---

## 演示第二部分（补充 2）：Live E2E — 延迟场景与真实 K8s 证据（1 分钟）

> **前置条件：** Kind 集群已运行并部署 demo services，可观测性栈已激活（见 `scripts/observability/`）。

### 步骤 1：注入延迟故障

```bash
curl -s -X POST http://localhost:8080/api/demo-services/fault/payment-latency
```

**讲解：** "我向 payment-service 注入 1500ms 延迟故障。Prometheus 将开始采集到升高的 p99 值。"

等待约 30 秒，让 Prometheus 完成采集。

### 步骤 2：运行实时 RCA

```bash
curl -s -X POST "http://localhost:8080/api/live-scenario/run?scenarioId=latency" | python3 -c "
import json,sys
d = json.load(sys.stdin)
rca = d.get('baseRca', d)
scores = rca.get('hypothesisScores', {})
for h, s in sorted(scores.items(), key=lambda x: -x[1]):
    print(f'  {s:.2f}  {h}')
"
```

**讲解：** "Agent 从 Prometheus、Loki 和 Kubernetes 收集了实时证据。看排名：

  0.50  downstream_dependency_latency   ← 正确根因
  0.19  pod_oom_killed
  0.12  deployment_regression
  0.09  pod_crash_loop                  ← 正确排在最后

`downstream_dependency_latency` 以 0.50 排名第一。`pod_crash_loop` 以 0.09 排在最后。这是正确的结果。

在语义分类修复之前，`pod_crash_loop` 错误地排名第一，因为 Kubernetes 证据是通用类型。修复后：事件按 reason 分类，crash loop 仅在真实 CrashLoopBackOff 状态触发，健康 pod 产生反例信号。无需修改任何评分算法——只是提高了证据精度。"

### 步骤 3：清除故障

```bash
curl -s -X POST http://localhost:8080/api/demo-services/fault/clear
```

**关键要点：** "这演示了一个关键 SRE 原则：**证据质量决定 RCA 质量。** 修复没有触碰任何评分常量——只是改进了 Kubernetes 证据的分类方式。"

---

## 演示第三部分：Web UI — 中文报告 + LLM Proposal 卡片 + 真实 LLM（1.5 分钟）

### 步骤 1：打开浏览器

导航到 http://localhost:8080/

### 步骤 2：点击 "Run Scenario E"

**讲解：** "Web UI 展示了相同的结果——注意决策类型现在显示为中文'竞争假设'，假设标题也是中文。"

### 步骤 3：指出 Phase 4 新增 UI 元素

- **决策卡片**——显示 **"竞争假设"**（Phase 4 中文化映射），分数差距 = 0.06
- **评分柱状图**——"近期部署引入了回归缺陷"（绿色/领先）、"下游依赖延迟导致服务降级"（黄色/竞争）、"Pod OOMKilled 或资源超限"（灰色/证据不足）
- **Markdown 报告**——现在输出**中文报告**（"竞争假设分析报告"），包含概要/调查时间线/假设评分/调查决策
- **LLM Proposal 卡片**——如果触发了 LLM 假设提案，会在下方显示卡片：提案标题、推理过程、观测信号、验证计划、置信度、状态
- **LLM 真实响应标识**——使用真实 LLM 时，LLM 区域显示 `modelProvider` 标签（如 "glm-4-flash"），与 Mock Provider 明确区分

### 步骤 4：展示实时排查控制台（可选）

点击右上角 **"🔍 实时排查"** 按钮：

**讲解：** "这是 Step V 新增的中文调查控制台。可以选择故障模式（延迟/错误/超时/混合），选择运行模式（模拟/实时），然后一键触发多信号 RCA。"

---

## 场景 E 展示时的讲解要点

### 开场介绍（30 秒）

"这是一个验证优先的 RCA Agent。当告警触发时，它会收集证据、生成假设、逐一验证并评分。关键设计决策：它保留不确定性。"

### 竞争性假设（30 秒）

"在场景 E 中，两个假设的评分非常接近——0.64 和 0.58。差距仅为 0.06。Agent 没有强制给出单一根因，而是输出 `competing_hypotheses` 并建议后续探测方向。这才是真正值班工程师需要的信息——而不是一个自信的错误答案。"

### 验证链（30 秒）

"每个假设都会根据支持证据和反证进行验证。部署回归假设有 4 项支持证据——部署事件、错误率飙升、超时日志以及 git 中的配置变更。但同时也有反证——同样的超时日志在部署之前就存在，且下游延迟也出现了飙升。"

### LLM 层（30 秒）

"LLM 层现已集成用于报告综合。它用通俗语言叙述调查结果——执行摘要、推理过程、不确定性说明、后续步骤——但它永远不会决定根因，也永远不会修改分数。确定性评分流程始终是权威的真实来源。LLM 输出始终标注为**建议性（Advisory）**，UI 显示明确的局限性和未验证提案标记。"

---

## 预期输出

### CLI

```
Incident created
Evidence loaded
Hypotheses generated: 3
...
Decision: 竞争假设
Selected: hyp_deployment_regression (0.64) — "近期部署引入了回归缺陷"
Competing: hyp_downstream_dependency_latency (0.58) — "下游依赖延迟导致服务降级"
Score gap: 0.06
Report written to: /tmp/rca-report.md
```

**注意：** Phase 4 之后，CLI 报告输出为中文（"竞争假设分析报告"），但 CLI 控制台日志仍为英文。

### REST API

```json
{
  "decisionType": "competing_hypotheses",
  "selectedHypothesisId": "hyp_deployment_regression",
  "confidenceScore": 0.64,
  "scoreGap": 0.06,
  "scores": {
    "hyp_deployment_regression": 0.64,
    "hyp_downstream_dependency_latency": 0.58,
    "hyp_pod_oom_killed": 0.05
  }
}
```

**注意：** REST API 的 JSON 字段保持英文 key（`decisionType`、`selectedHypothesisId` 等），但 UI 展示会自动映射为中文（"竞争假设"→"高置信根因"等）。Markdown 报告端点返回中文报告。

---

## 备用方案

如果 Spring Boot 服务器启动失败：

1. **跳过 API 和 UI 演示**——CLI 演示涵盖了相同的工作流
2. 展示预生成的报告：`cat examples/reports/competing_hypotheses_report.md`
3. 在纸/白板上走一遍架构图

如果 Java/构建失败：

1. 展示 `examples/reports/` 中的预生成报告
2. 在 IDE 中走一遍源码结构
3. 将对话聚焦于架构和设计决策

如果一切都不管用：

1. 打开 `docs/architecture.md` 并走一遍图表
2. 讨论设计原则和权衡取舍
3. 仅凭文档就能讲清楚整个故事
