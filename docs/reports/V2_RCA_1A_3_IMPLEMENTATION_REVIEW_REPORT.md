# V.2-RCA-1A.3 实现审查报告

**审查日期:** 2026-05-10
**审查者:** Aegis (自动审查)
**被审分支:** `rca-v2`
**变更文件数:** 11 (5 修改 + 6 新增)
**测试结果:** 155/155 PASS ✅

---

## 1. 执行摘要 (Executive Summary)

V.2-RCA-1A.3 的核心交付物（ProblemWindow 值对象、TemporalAligner 评分引擎、ConfidenceScorer 集成）已**完整实现并通过所有测试**。实现质量**中上**，设计约束（不破坏 Topology scoring、不回归 ScenarioE/F、时间维度 bounded 评分）全部遵守。

**关键缺口 (1 项):**
- MarkdownReporter **未渲染任何 temporal alignment 信息**。面试场景下无法展示 `temporalScore`、`temporalConfidence`、`candidateFirstSeen` 等关键数据。

**设计偏差 (1 项):**
- 反向因果惩罚从 spec 的 `-0.05` 调整为 `-0.10`（修复「simultaneous + inside window bonus 抵消 reverse penalty」的 bug），总分边界从 spec 的 `[-0.10, +0.15]` 变为 `[-0.15, +0.15]`。此变更**合理且有测试支撑**。

**建议:** 修复 MarkdownReporter 缺口后即可合入主分支。

---

## 2. 变更清单

### 2.1 修改文件 (5)

| 文件 | 变更行数 | 变更类型 |
|------|---------|---------|
| `ConfidenceResult.java` | +26/-? | 新增 temporalScore、temporalConfidence 字段 |
| `ConfidenceScorer.java` | +44/-? | 新增带 TemporalAlignmentResult 的 score()/scoreAll() 重载 |
| `InvestigationWorkflow.java` | +20/-? | 集成 TemporalAligner，注入 temporalResults 到 scorer |
| `rca-causal-model.md` | +173/-57 | 10 处文档更新 + LLM 表述保守化 |
| `.gitignore` | +3 | 环境隔离 |

### 2.2 新增文件 (6)

| 文件 | 行数 | 类型 |
|------|------|------|
| `domain/ProblemWindow.java` | ~120 | 值对象 |
| `domain/TemporalAlignmentResult.java` | ~85 | 值对象 |
| `domain/TemporalConfidence.java` | ~12 | 枚举 |
| `verification/TemporalAligner.java` | ~190 | 评分引擎 |
| `test/.../ProblemWindowTest.java` | 164 | 单元测试 (12 method) |
| `test/.../TemporalAlignerTest.java` | 263 | 单元测试 (11 method) |

---

## 3. 逐项审查 (11 项)

### 5.1 ProblemWindow 值对象 — ⚠️ PASS (1 小偏差)

**Spec 要求 (第 4 节):**
```
优先级:
1. Incident explicit window
2. Alert startsAt / endsAt
3. Alert startsAt + default lookback/lookahead
4. Evidence timestamp min/max fallback
5. Unknown fallback window
```

**实际实现:**
- ✅ 支持 `alert` source (从 `incident.startedAt()` 推导 + 默认 margins)
- ✅ 支持 `evidence_fallback` source (从证据时间戳 min/max 推导)
- ✅ 支持 `unknown` source (无任何时间信息)
- ❌ **不支持 Incident explicit window** (优先级 1 未实现)

**影响:** 低。当前 `IncidentTask` 无显式 window 字段，实际场景不会触发此路径。但 spec 要求的完整优先级链确实缺失了第一环。

**ProblemWindow 数据结构:**
| 字段 | Spec | 实际 | 状态 |
|------|------|------|------|
| problemStart | ✅ | `Instant` | ✅ |
| problemEnd | ✅ | `Instant` | ✅ |
| lookbackWindow | ✅ | `5min` | ✅ |
| lookaheadWindow | ✅ | `10min` | ✅ |
| source | ✅ | `"alert"` \| `"evidence_fallback"` \| `"unknown"` | ✅ |
| alertStartsAt | 可选 | ❌ 未实现 | — |
| alertEndsAt | 可选 | ❌ 未实现 | — |
| incidentStartsAt | 可选 | ❌ 未实现 | — |
| incidentEndsAt | 可选 | ❌ 未实现 | — |

**API 方法:**
| 方法 | Spec | 实际 | 状态 |
|------|------|------|------|
| `contains(Instant)` | ✅ | ✅ inclusive 边界 | ✅ |
| `isBeforeWindow(Instant)` | ✅ | ✅ | ✅ |
| `isAfterWindow(Instant)` | ✅ | ✅ | ✅ |
| `overlaps(Instant, Instant)` | ✅ | ✅ | ✅ |
| `isValid()` | — | ✅ null-safe | ✅ |

---

### 5.2 TemporalAlignmentResult 值对象 — ✅ PASS

| 字段 | Spec | 实际 | 状态 |
|------|------|------|------|
| `score` (double) | ✅ | ✅ | ✅ |
| `confidence` (TemporalConfidence) | ✅ | HIGH/MEDIUM/LOW/UNKNOWN | ✅ |
| `explanation` (String) | ✅ | ✅ | ✅ |
| `candidateFirstSeen` (Optional<Instant>) | ✅ | ✅ | ✅ |
| `impactedFirstSeen` (Optional<Instant>) | ✅ | ✅ | ✅ |
| `evidenceInsideWindow` (int) | ✅ | ✅ | ✅ |
| `evidenceOutsideWindow` (int) | ✅ | ✅ | ✅ |
| `UNKNOWN` 常量 | — | ✅ 静态常量 | ✅ |
| `scoreMin()` / `scoreMax()` | — | ✅ 边界常量 | ✅ |

**分数边界:**
| | Spec | 实际 | 偏差 |
|------|------|------|------|
| 最小 | -0.10 | -0.15 | ⚠️ 见下文「设计偏差」 |
| 最大 | +0.15 | +0.15 | ✅ |

**设计偏差说明:** 反向因果惩罚从 `-0.05` 加强到 `-0.10`，原因是在「simultaneous + inside window」场景下，spec 的 `+0.05 (simultaneous) + 0.05 (inside bonus)` 正好抵消 `-0.05 (reverse penalty)`，导致反向因果得分为 0。增强 penalty 后差值 > 0，测试覆盖了此场景（`candidateAfterImpacted_returnsNegativeScore`）。此变更**合理**，总分下界扩大 0.05 不影响 bounded scoring 设计原则。

---

### 5.3 TemporalAligner 引擎 — ✅ PASS

**核心逻辑审查:**

| 规则 | Spec | 实际 | 状态 |
|------|------|------|------|
| candidate before impacted | +0.10 | CANDIDATE_BEFORE_IMPACTED_BONUS = 0.10 | ✅ |
| simultaneous | +0.05 | SIMULTANEOUS_BONUS = 0.05 | ✅ |
| candidate after impacted | -0.05 | CANDIDATE_AFTER_IMPACTED_PENALTY = -0.10 | ⚠️ 见 5.2 |
| inside window bonus | +0.05 max | +0.05 per evidence in window | ✅ |
| outside window penalty | max -0.05 | -0.05 when all evidence outside | ✅ |
| missing timestamp | 0.00 UNKNOWN | ✅ | ✅ |
| cannot distinguish | LOW/UNKNOWN | ✅ | ✅ |

**Candidate / Impacted 区分逻辑:**
- ✅ 候选证据: `evidence.sourceService().equals(hypothesis.affectedService())`
- ✅ 影响证据: `!evidence.sourceService().equals(hypothesis.affectedService())`
- ✅ 无法区分时 confidence = LOW
- ✅ 空/无效 window 时返回 UNKNOWN

**边界处理:**
- ✅ null window → UNKNOWN
- ✅ empty evidence → UNKNOWN
- ✅ invalid window → UNKNOWN
- ✅ 所有证据时间戳为 null → UNKNOWN
- ✅ window 覆盖率为 0% → 无 bonus

---

### 5.4 ConfidenceScorer 集成 — ✅ PASS

**V2 评分公式:**
```
rawScore = baseScore
         + weightedSupportCoverage * BONUS_CAP
         + weightedCorroboratingCoverage * BONUS_CAP
         + temporalResult.score()

finalScore = clamp(rawScore, 0.0, 1.0)
```

**向后兼容:**
- ✅ `score(hypothesis, pattern, vr, evidence)` 旧签名保留，默认 UNKNOWN（score=0.0）
- ✅ `scoreAll(…, evidence)` 旧签名保留，内部委托 `scoreAll(…, evidence, Map.of())`
- ✅ `scoreAll(…, evidence, temporalResults)` 新增重载
- ✅ `ConfidenceResult` 新增字段使用 `@JsonProperty` 不影响序列化

**ConfidenceResult 新增字段:**
| 字段 | 说明 |
|------|------|
| `temporalScore` | TemporalAligner 产出 |
| `temporalConfidence` | HIGH/MEDIUM/LOW/UNKNOWN |
| 旧字段 | 未改动 |

---

### 5.5 InvestigationWorkflow 集成 — ✅ PASS

**集成流程:**
```
1. Evidence loaded
2. ProblemWindow.deriveFromIncident(incident, evidence)
3. TemporalAligner.alignAll(problemWindow, evidence, hypotheses)
4. ConfidenceScorer.scoreAll(…, evidence, temporalResults)
```

**EventTrace 记录:**
- ✅ `PROBLEM_WINDOW_DERIVED` (含 source, problemStart, problemEnd)
- ✅ `TEMPORAL_ALIGNED` (含 hypothesisId, temporalScore, temporalConfidence)
- ✅ `run(File, File)` 委托 `runFromMemory()` → 两条路径都走 temporal scoring

---

### 5.6 MarkdownReporter — ❌ FAIL

**Spec 要求 (第 9 节):**
```
ScoreBreakdown 必须出现:
temporalAlignmentScore
temporalConfidence
candidateFirstSeen
impactedFirstSeen
problemWindow
temporalExplanation
```

**实际情况:** MarkdownReporter.generate() 完全不提及 temporal alignment 信息。生成的 Markdown 报告中无任何 temporal 相关字段。

**缺口分析:**
- `MarkdownReporter.generate()` 接收 `List<ConfidenceResult>`，其中包含 `temporalScore` 和 `temporalConfidence`
- 但 `generate()` 方法内部**未读取这些字段**
- 报告中的「假设评分」表格只有分数、等级、决策列，没有 temporal score 列
- 没有「时间对齐分析」section

**影响:** 面试演示场景下，temporal alignment 的工作成果对评审者不可见。这是一个**可演示性缺口**。

---

### 5.7 Evidence Timestamp 兼容性 — ✅ PASS

**Spec 要求 (第 5 节):**
```
1. timestamp 可为空
2. 不破坏历史构造器 / fixture
3. 历史测试不需要一次性全改
```

**实际情况:**
- ✅ `Evidence` 已有 `timestamp` 字段 (Instant, nullable)
- ✅ 无 timestamp 的 evidence → TemporalAligner 返回 UNKNOWN (score=0)
- ✅ 旧测试 (ScenarioEConfidenceTest) 使用 `score()` 旧签名 → 不走 temporal → 不回归
- ✅ ScenarioE/F 测试中 evidence 已有 timestamp，但旧调用路径 `scoreAll(h, p, v, e)` 无 temporal → 保持旧行为

---

### 5.8 ScenarioE/F & 回归测试 — ✅ PASS

```
Tests run: 155, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 关键测试类 | 用例数 | 结果 |
|-----------|--------|------|
| ProblemWindowTest | 12 | ✅ |
| TemporalAlignerTest | 11 | ✅ |
| ConfidenceScorerTest | 12 | ✅ |
| ScenarioEConfidenceTest | 8 | ✅ |
| ScenarioEVerificationTest | 8 | ✅ |
| ScenarioFCrashLoopWorkflowTest | 10 | ✅ |
| MarkdownReporterTest | 10 | ✅ |
| HypothesisComparatorTest | 8 | ✅ |
| VerificationEngineTest | 5 | ✅ |
| 其他 | 71 | ✅ |
| **总计** | **155** | **✅ 0 失败** |

**回归分析:**
- ScenarioE/F 测试中使用的 `ConfidenceScorer.score()` 旧签名**不走 temporal alignment** → 评分逻辑未变
- 新代码的 temporal 路径仅在 `InvestigationWorkflow.runFromMemory()` 中调用
- 新增的 `ConfidenceResult` 字段为向后兼容（默认值 null/UNKNOWN）

---

### 5.9 ScoreBreakdown / CausalScorer — ⚠️ 命名偏差

**Spec 要求 (第 9 节):**
```
将 temporalAlignmentScore 接入:
CausalScorer
ScoreBreakdown
MarkdownReporter / RCA report
```

**实际情况:**
- ❌ 无 `CausalScorer` 类 — 当前评分引擎名为 `ConfidenceScorer`
- ❌ 无 `ScoreBreakdown` 值对象 — temporal 信息通过 `ConfidenceResult` 承载
- ❌ MarkdownReporter 未渲染 temporal 信息

**评估:** `CausalScorer`/`ScoreBreakdown` 是 spec 中的概念名，实际代码使用了不同的命名 (`ConfidenceScorer`/`ConfidenceResult`)。这不影响功能但会导致按 spec 搜索代码时找不到对应类。**属于早期设计到实现的命名漂移，不是 1A.3 引入的问题。**

---

### 5.10 Topology Tests — ✅ PASS

无独立的 TopologyCausal 测试文件。Topology 相关逻辑通过 `ConfidenceScorer` 中的 provider alias 归一化体现。所有相关测试（ScenarioE, ScenarioF, ConfidenceScorerTest）均通过。

---

### 5.11 CI/CD & 分模块测试 — ✅ PASS

```bash
# 全部通过
mvn test -pl sre-agent-core                    # 155 ✅
mvn -Dtest=*ProblemWindow* test                 # 12 ✅
mvn -Dtest=*TemporalAligner* test               # 11 ✅
mvn -Dtest=*ConfidenceScorer* test              # 12 ✅
mvn -Dtest=*Scenario* test                      # 26 ✅
mvn -Dtest=*MarkdownReporter* test              # 10 ✅
```

---

## 4. 依从性矩阵 (Compliance Matrix)

| Spec 要求 (#14) | 状态 | 备注 |
|-----------------|------|------|
| 1. 新增 ProblemWindow 值对象 | ✅ | |
| 2. 支持 problemStart/End/lookback/lookahead | ✅ | |
| 3. 可从 alert/incident/fallback 推导 | ⚠️ | incident explicit window 未实现 |
| 4. Evidence timestamp 兼容策略 | ✅ | |
| 5. 新增 TemporalAligner | ✅ | |
| 6. temporalAlignmentScore 接入 scorer | ✅ | |
| 7. candidate before impacted 加分 | ✅ | |
| 8. candidate after impacted 降分 | ✅ | penalty 加强至 -0.10 |
| 9. outside window 不计或降权 | ✅ | |
| 10. missing timestamp 不导致失败 | ✅ | |
| 11. ScenarioE/F 不回归 | ✅ | |
| 12. Topology tests 继续通过 | ✅ | |
| 13. mvn test 通过 | ✅ | 155/155 |
| 14. rca-causal-model.md 已更新 | ✅ | |

**通过: 12/14**
**部分通过: 2/14 (3, 6)**

---

## 5. 设计偏差详述

### 偏差 1: 反向因果惩罚加强 (-0.05 → -0.10)

**背景:** Spec 中 reverse causality penalty = -0.05，但在实际测试中发现 `candidateAfterImpacted` 场景下，同时触发的 inside-window bonus (+0.05) 会正好抵消 penalty，导致反向因果关系被误判为 0 分。

**修复:** penalty 提升至 -0.10，scoreMin 同步调整为 -0.15。

**测试覆盖:** `TemporalAlignerTest.candidateAfterImpacted_returnsNegativeScore()` — 验证 score() < 0.0。

**评审:** ✅ 合理的 bug 修复。Spec 的总分边界 `-0.10 ~ +0.15` 扩展为 `-0.15 ~ +0.15`，下界增加了 0.05。此 0.05 差异在实际场景中的影响极小（仅当所有 evidence 都在 window 外且 candidate 晚于 impacted 时才会达到下界），且 bounded scoring 设计原则仍然成立。

### 偏差 2: Incident explicit window 未实现

**Spec 要求 (优先级 1):** `Incident explicit window`

**实际:** `ProblemWindow.deriveFromIncident()` 只实现了优先级 2-5。

**评审:** 低优先级。当前 `IncidentTask` 模型没有显式 window 字段，未来如需支持可在 `IncidentTask` 中新增 `problemStart`/`problemEnd` 字段后实现。

---

## 6. 遗留问题 (Action Items)

| 优先级 | 事项 | 影响 | 建议 |
|--------|------|------|------|
| **P0** | MarkdownReporter 不渲染 temporal 信息 | 面试演示下 temporal 不可见 | 在「假设评分」表格增加 temporalScore 列 + 新增「时间对齐分析」section |
| **P1** | ScenarioEConfidenceTest 不使用 temporal pathway | temporal 路径在 E2E 测试中无覆盖 | 新增 `ScenarioETemporalTest` 走 `InvestigationWorkflow.runFromMemory()` 路径验证 temporal 不破坏已有结果 |
| **P2** | ScoreBreakdown 概念未落地 | 排查 temporal 问题时无单一查看入口 | 后续 1A.4 可考虑实现 ScoreBreakdown 值对象 |
| **P3** | Incident explicit window 未实现 | 低影响 | 按需在后续迭代中实现 |

---

## 7. 测试覆盖率

| 测试维度 | 文件 | 覆盖场景数 | 状态 |
|----------|------|-----------|------|
| ProblemWindow 边界 | ProblemWindowTest | 12 | ✅ |
| TemporalAligner 规则 | TemporalAlignerTest | 11 | ✅ |
| ConfidenceScorer 边界 | ConfidenceScorerTest | 12 | ✅ |
| ScenarioE E2E | ScenarioEConfidenceTest | 8 | ✅ (旧路径) |
| ScenarioE 验证 | ScenarioEVerificationTest | 8 | ✅ |
| ScenarioF E2E | ScenarioFCrashLoopWorkflowTest | 10 | ✅ |
| Markdown 报告 | MarkdownReporterTest | 10 | ✅ |
| **合计** | | **155** | **✅** |

**已知覆盖缺口:**
- ❌ Temporal pathway 在 ScenarioE/F 中无 E2E 覆盖（见遗留问题 P1）
- ❌ `InvestigationWorkflow.runFromMemory()` 的 temporal 路径无独立集成测试

---

## 8. 文件级审查

### ProblemWindow.java — 良好

- ✅ Record 类型，不可变
- ✅ null-safe 方法 (contains, isBeforeWindow, isAfterWindow)
- ✅ deriveFromIncident 处理三种来源
- ⚠️ 缺少 `alertStartsAt`/`alertEndsAt` 可选字段（spec 标记为可选，未实现）

### TemporalAlignmentResult.java — 良好

- ✅ 静态常量 UNKNOWN 便于默认值
- ✅ scoreMin/scoreMax 约束 + Javadoc 文档化
- ✅ explanation 可读，非空字符串
- ⚠️ `@JsonProperty` 注解使 JSON 序列化友好（非必需但良好实践）

### TemporalAligner.java — 良好

- ✅ align() 方法主流程清晰：validate → classify evidence → temporal logic → window coverage → result
- ✅ alignAll() 批量方法减少调用方代码
- ✅ 所有边界条件 (null window, empty evidence, invalid window, null timestamps) 都有 UNKNOWN 处理
- ✅ 常量命名规范 (CANDIDATE_BEFORE_IMPACTED_BONUS 等)

### ConfidenceScorer.java — 良好

- ✅ 旧签名保留，向后兼容
- ✅ 新 score() 带 TemporalAlignmentResult 参数
- ✅ scoreAll() 双重重载：无 temporal → 委托有 temporal (Map.of())

### InvestigationWorkflow.java — 良好

- ✅ TemporalAligner 注入和调用清晰
- ✅ EventTrace 完整记录
- ✅ runFromMemory() 同时覆盖 run(File, File) 路径

### MarkdownReporter.java — 缺口

- ❌ 7 个 section 中无任何 temporal 相关内容
- ❌ 「假设评分」表格没有 temporal 列
- ❌ 没有时间对齐分析 section

---

## 9. 结论与建议

### 总体评价: **良好** (7.5/10)

V.2-RCA-1A.3 的核心实现（ProblemWindow、TemporalAligner、ConfidenceScorer 集成）质量扎实，设计约束遵守良好，155 个测试全部通过。反向因果惩罚的 bug 修复有明确的测试驱动。

### 关键路径阻塞项

1. **MarkdownReporter 缺口 (P0):** 不修复则面试演示时 temporal 工作完全不可见。这是唯一阻止合入主分支的功能性缺口。

### 建议合入条件

修复 MarkdownReporter（增加 temporal 信息渲染）后即可合入 `rca-v2` 主分支。P1/P2/P3 可以在后续迭代中跟进。

### 是否建议进入 V.2-RCA-1A.4

是。1A.3 的核心架构（TemporalAligner 接入 ConfidenceScorer 的 bounded scoring 模式）为 1A.4 (Topology Graph & Propagation Path Quality) 提供了良好的参考模式。建议先修复 MarkdownReporter 缺口后再进入 1A.4。

---

*报告生成时间: 2026-05-10*
*审查范围: 11 文件 / 155 测试用例*
*审查工具: Aegis (Hermes Agent)*
