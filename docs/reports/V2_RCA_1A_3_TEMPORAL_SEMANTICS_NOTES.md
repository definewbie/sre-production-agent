# V.2-RCA-1A.3 时间对齐语义边界说明

**版本：** V.2-RCA-1A.3b
**日期：** 2026-05-10
**作者：** Aegis（基于 Kris 的语义边界要求）
**关联文档：** `docs/architecture/rca-causal-model.md`

---

## 1. 核心定位

Temporal Alignment 是 RCA 评分体系中的 **supportive evidence**，而非 **sufficient evidence**。

它在 ConfidenceScorer v2 评分公式中贡献一个 bounded 的 `temporalScore`（范围 `[-0.15, +0.15]`），作为对 `faultModeEvidence` 和 `corroboratingBonus` 等主证据维度的补充调整。

> **Temporal alignment 回答的是「候选异常的时间顺序是否支持因果假设」。**
> **它不能单独回答「这个假设是不是根因」。**

---

## 2. 评分边界

### 2.1 分量

| 分量 | 权重 | 含义 |
|------|------|------|
| candidate before impacted | +0.10 | 候选异常早于受影响的异常 — 符合因果顺序 |
| candidate = impacted | +0.05 | 同时出现 — 无法区分因果，给小幅正向 |
| candidate after impacted | **-0.10** | 反向因果 — 候选异常晚于受影响异常，不符合因果 |
| evidence inside window | 最多 +0.05 | 证据在 Problem Window 内比例 |
| evidence outside window | 最多 -0.05 | 证据在 Problem Window 外比例 |

### 2.2 总边界

```
temporalScore ∈ [-0.15, +0.15]
```

- **解释**：即使 candidate anomaly 比 impacted anomaly 出现得早、且所有证据都在 Problem Window 内，temporalScore 也是 **+0.15 而不是 +0.80**。
- **对比**：fault mode evidence 主维度可贡献到 1.0。temporal 维度天然是小权重调整维度。

---

## 3. 语义边界：什么可以被接受，什么需要谨慎

### 3.1 ✅ 可以接受

| 场景 | 理由 |
|------|------|
| **deployment / configuration / release change 类 evidence 早于 ProblemWindow start** | 部署变更本来就是触发因子，expected to happen before the incident。对 deployment_regression 模式，candidateFirstSeen 早于 ProblemWindow 是完全合理的。 |
| **拓扑/依赖缺失时，正向 temporalScore 仍能给假设加分** | 即使没有 Topology Graph，时间顺序一致也能提供支撑——虽然不够充分。 |
| **多个假设获得相同的 positive temporalScore** | 基于同一批 evidence 时间戳，多个假设可能有相同的 temporal 信号。这是合理的（它们都在同一时间窗口内竞争），最终区分靠 fault mode evidence + counter evidence。 |

### 3.2 ⚠️ 需要谨慎

| 场景 | 风险 | 当前处理 | 未来方向 |
|------|------|----------|----------|
| **latency / error / timeout / resource pressure 类 runtime anomaly 远早于 ProblemWindow** | 可能是 **stale anomaly**（早已存在、与当前 incident 无关的持续异常），不应该因为有早于 impacted 的时间戳就获得正向 temporalScore | 当前 TemporalAligner 对所有证据类型一视同仁：只看时间顺序不看证据语义 | V.2-RCA-1A.5 Fault Mode Evidence Contract 将引入 fault-mode-specific temporal rules：对 runtime anomaly，要求 candidateFirstSeen 在 ProblemWindow 的 lookback 范围内或 alert 触发前不久，否则视为 stale anomaly 并降低 temporal 置信度 |
| **candidateFirstSeen 早于 ProblemWindow 超过 30 分钟** | 时间距离过大可能削弱因果关联 | 当前没有时间距离衰减 | 未来增加 time-distance decay factor |
| **所有证据都在 ProblemWindow 外，但时间顺序正确** | 可能得到 +0.05（outside penalty 抵消 causality bonus），但实际应标记为 PARTIAL | 当前通过 LOW/UNKNOWN 置信度标记 | 当前即可在报告层识别 |

### 3.3 ❌ 不应发生

| 场景 | 说明 |
|------|------|
| **no topology + positive temporalScore → LIKELY_ROOT_CAUSE** | 这是错误的。temporal 维度权重上限 0.15，即使加上 faultModeEvidence 的中等分数，没有 topology 确认 causal path，不应到达 LIKELY_ROOT_CAUSE。当前 scoring 公式对此有天然约束（temporalScore 小权重），但应在文档中明确此语义。 |
| **仅凭 temporalScore +0.15 就区分 competing hypotheses** | 两个假设 temporal 分数差距 ≤ 0.05 时，不应仅凭 temporal 就判胜负。HypothesisComparator 有 gap 阈值机制防止此情况。 |

---

## 4. 当前代码中的体现

### 4.1 TemporalAligner

- `align()` 方法对 **所有证据类型** 使用同一套因果顺序判断 + window coverage 计算
- **不做 fault-mode-specific temporal rules**（这是 V.2-RCA-1A.5 的设计范围）
- `candidateFirstSeen` 和 `impactedFirstSeen` 是由 `min(Evidence.timestamp)` 直接计算，不做语义过滤

### 4.2 ConfidenceScorer

- `scoreAll()` 将 `TemporalAlignmentResult.score` 写入 `ConfidenceResult.temporalAlignmentScore`
- `temporalScore` 在总分中作为 **加法项**（不参与 ratio-based 分母），天然权重可控
- 最终 confidence 由多个维度（faultModeEvidence + counterPenalty + corroboratingBonus + temporalScore）综合，单个维度的天花板效应有限

### 4.3 MarkdownReporter

- 报告中 temporal section 渲染真实数据（temporalScore、temporalConfidence、candidateFirstSeen、impactedFirstSeen、explanation）
- 当 `ProblemWindow` 为 null 或 invalid 时 fallback 到 PARTIAL 声明
- 不隐藏 N/A（V.2-RCA-1A.3a 修复后已消除 N/A 占位）

---

## 5. 与评分公式的关系

```
confidence = f(
    faultModeEvidence,    // 主维度：证据覆盖
    counterPenalty,       // 反证据惩罚
    corroboratingBonus,   // 佐证证据加分
    temporalScore         // 时间对齐调整 [-0.15, +0.15]
)
```

**temporalScore 的权重上限是设计选择，不是实现缺陷。** 

如果 temporalScore 权重超过 +0.15（例如 +0.30），则可能出现「一个时间顺序完美但没有实质性 fault mode evidence 的假设得分高于一个有大量 evidence but 时序稍差的假设」的问题——这是错误的因果推理。

---

## 6. 未来收敛路径

| 阶段 | 内容 |
|------|------|
| V.2-RCA-1A.4 | Topology Graph 独立维度 — temporal + topology 联合可提供更强的 causality 信号，但前提是有 topology 数据 |
| V.2-RCA-1A.5 | **Fault Mode Evidence Contract** — 引入 fault-mode-specific temporal rules：runtime anomaly 要求 candidateFirstSeen 在 lookback scope 内；deployment/change event 允许更远的 temporal distance |
| V.2-RCA-1A.6 | Regression Scenario Matrix — 覆盖 fault mode × temporal 组合的边界场景 |
| V.2-RCA-1B | LLM 离线分析识别 temporal ambiguity gap |

---

## 7. 结论

1. **Temporal alignment 是 supportive evidence，不是 sufficient evidence。**
2. **当前 scoring 公式通过 bounded weight [-0.15, +0.15] 对此有天然约束。**
3. **对 deployment/change 类 evidence，candidateFirstSeen 早于 ProblemWindow 是合理的，不应被视为异常。**
4. **对 runtime anomaly 类 evidence，需要 fault-mode-specific temporal rules（V.2-RCA-1A.5 设计范围），当前不做区分。**
5. **no topology + positive temporalScore 在当前公式下不能单独产生 LIKELY_ROOT_CAUSE。**
6. **当前阶段（V.2-RCA-1A.3）不修改 scoring 公式、不修改 TemporalAligner、不修改 ProblemWindow。语义边界以文档形式记录，留待 V.2-RCA-1A.5 在 Fault Mode Evidence Contract 中细化。**
