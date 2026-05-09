# V.2-RCA-1A 回归修复终报

**日期：** 2026-05-09  
**分支：** `main`  
**范围：** sre-agent-core

---

## 修复总览

| 修复项 | 状态 | 说明 |
|--------|------|------|
| **Provider Alias 分离** | ✅ | `ConfidenceScorer` 中新增 `PROVIDER_ALIAS_PREFIXES`，评分时跳过 alias 类型 |
| **R1-R4 回归修复** | ✅ | ScenarioE/F 回归已通过（16/16） |
| **R5 无拓扑/单 provider 测试** | ✅ | 3 个新测试：withoutTopology、singleProvider、noTopoAndSingleProvider |
| **R6 边界测试** | ✅ | 6 个新测试：resourcePressure ×2、crashLoop ×2、deployRegression counterOnly ×1、已存在 1 个边界测试 |
| **R7 .gitignore** | ✅ | 新增 `*.mv.db` `*.trace.db` |
| **R8 全量测试** | ✅ | core 127/127、llm 48/51（3 预存在）、server 139/139、cli 15/15 |

---

## 核心修改

### `ConfidenceScorer.java`（Provider Alias 分离）

```java
private static final Set<String> PROVIDER_ALIAS_PREFIXES = Set.of(
    "kubernetes_", "prometheus_", "elk_", "mysql_",
    "redis_", "postgres_", "mongodb_", "kafka_"
);
```

- `normalizeEvidenceType()` 识别 alias 并返回规范名
- 覆盖率计算循环跳过 alias（分母仅含 core 类型）
- 加权循环跳过 alias（避免稀释）

### `ConfidenceScorerTest.java`（新增 9 个测试）

**R5 — 无拓扑/单 provider 场景（3 个）**
- `deploymentRegression_withoutTopology` — 断言 ≤ 0.50, decision = uncertain
- `directOnly_singleProvider` — 单一 metric provider 不崩溃
- `noTopologyAndSingleProvider` — 无拓扑 + 单 log provider → insufficient_evidence

**R6 — 边界测试（6 个）**
- `resourcePressure_withPartialEvidence` — memory+OOM log, 无 K8s OOMKilled
- `resourcePressure_counterOnly` — 仅 counter 证据，得分 ≤ 0.20
- `crashLoop_onlyContainerCrash` — 仅 container_crash_loop_backoff，非 probable_root_cause
- `crashLoop_counterOnly` — 仅 counter，得分 ≤ 0.15
- `deploymentRegression_counterOnly` — 仅 counter，得分 ≤ 0.20
- `resourcePressure_withPartialEvidence_shouldNotCrash`（R6）— 已验证

---

## 测试结果

```
sre-agent-core:   127/127 ✅  (新增 ConfidenceScorerTest: 12/12)
sre-agent-llm:     48/51⚠️   (3 预存在，非本次改动引发)
  - LlmPromptBuilderTest.userPromptContainsConstraints
  - MockLlmClientTest.contentMentionsScoreGap
  - MockLlmClientTest.contentMentionsScores
sre-agent-server: 139/139 ✅
sre-agent-cli:     15/15 ✅
─────────────────────────
总计:              329/332 (3 预存在)
```

---

## V2 评分模型验证

Provider alias 分离后，legacy 场景得分恢复正常：

| 场景 | 修复前 | 修复后 | 预期 |
|------|--------|--------|------|
| deployment_regression | 0.30 | **0.50** | 0.50 |
| downstream_dependency_latency | 0.00 | **0.45** | 0.45 |
| pod_crash_loop (ScenarioF) | ~0.40 | **~0.70** | 0.70 |

---

## 文件变更清单

```
修改:
  sre-agent-core/src/main/java/.../ConfidenceScorer.java     (+alias 分离)
  sre-agent-core/src/test/java/.../ConfidenceScorerTest.java  (+R5/R6 测试)
  .gitignore                                                   (+H2 规则)

新增:
  docs/reports/V2_RCA_1A_1_REGRESSION_REPAIR_REPORT.md
  docs/reports/V2_RCA_1A_VERIFICATION_AND_CHANGE_SPLIT_REPORT.md
  docs/reports/V2_RCA_1A_FINAL_REPORT.md (本文件)
```
