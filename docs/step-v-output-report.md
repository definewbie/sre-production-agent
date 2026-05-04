# Step V Output Report: Complex Live RCA Scenario + Chinese Investigation Console

**Date:** 2026-05-05
**Commits:** `723d4c5` (main) → `90076ce` (validation fixes)
**Branch:** main

---

## 1. Files Created (8 files)

| # | File | Purpose |
|---|------|---------|
| 1 | `sre-agent-server/.../live/LiveScenarioService.java` | Scenario G 编排核心：故障注入 → 证据收集 → RCA → 重置 |
| 2 | `sre-agent-server/.../live/LiveEvidenceCollector.java` | 5-provider 证据收集器（Prometheus/Loki/Jaeger + fixture fallback） |
| 3 | `sre-agent-server/.../live/LiveScenarioResult.java` | 场景结果 DTO（scenarioId, status, evidenceReport, baseRca） |
| 4 | `sre-agent-server/.../live/LiveEvidenceReport.java` | 证据报告 DTO（totalEvidenceCount, sources, warnings） |
| 5 | `sre-agent-server/.../controller/LiveScenarioController.java` | REST API: simulate/run/latest/{id}/list/reset |
| 6 | `sre-agent-server/.../controller/LiveScenarioControllerTest.java` | 8 tests: simulate, run-live, run-fixture, latest, getById, list, reset, error |
| 7 | `sre-agent-server/.../live/LiveEvidenceReportTest.java` | 2 tests: serialization, empty report |
| 8 | `sre-agent-server/.../live/LiveScenarioResultTest.java` | 2 tests: serialization, round-trip |

## 2. Files Modified (7 files)

| # | File | Change |
|---|------|--------|
| 1 | `sre-agent-core/.../workflow/InvestigationWorkflow.java` | +39 行: 新增 `runFromMemory()` 避免 temp file I/O |
| 2 | `sre-agent-server/src/main/resources/static/index.html` | +379 行: 实时排查 Console UI tab（中文） |
| 3 | `sre-agent-server/pom.xml` | +21 行: 依赖 sre-agent-loki-provider, sre-agent-trace-provider |
| 4 | `Makefile` | +19 行: live-scenario-simulate/run/reset/latest targets |
| 5 | `README.md` | Step V section, API 端点文档, test count 更新 |
| 6 | `docs/future-roadmap.md` | Step V 详细记录, API 路径修正 |
| 7 | `docs/demo-services-observability.md` | 端口配置更新 |

**Total delta:** +1,531 lines, −39 lines

## 3. Scenario G Summary

**场景名称:** Payment Latency → Order Error Spike

**业务模型:**
- Order Service → Payment Service → Inventory Service 调用链
- 注入 payment-service 延迟故障 → order-service 出现超时/错误

**两种运行模式:**

| Mode | 故障注入 | 证据来源 | 适用场景 |
|------|----------|----------|----------|
| `simulate` (GET) | 无 | Fixture clients | CI/CD, 演示 |
| `live` (POST) | HTTP POST 到 payment-service `/fault-config` | HTTP clients + fixture fallback | 本地开发 |

**支持的故障模式 (`faultMode`):**
- `latency` — 支付延迟 2s
- `error` — 支付返回 500
- `timeout` — 支付超时
- `normal` — 无故障

**RCA 流程 (7 phases):**
1. Inject fault (live mode only)
2. Wait for propagation
3. Collect evidence (5 providers)
4. Run RCA workflow
5. LLM report synthesis
6. Reset fault (live mode only)
7. Assemble result

## 4. Live Evidence Collection Summary

**LiveEvidenceCollector** 聚合 3 个 observability provider:

| Provider | Queries | Fixture Evidence | HTTP Fallback |
|----------|---------|------------------|---------------|
| **Prometheus** | ERROR_RATE, LATENCY_P95, DOWNSTREAM_LATENCY_P95, MEMORY_USAGE, CPU_USAGE, RESTART_RATE | 12 | `http://localhost:9090` |
| **Loki** | HTTP_5XX_LOGS, DOWNSTREAM_TIMEOUT, PAYMENT_ERRORS, ORDER_ERRORS | 8 | `http://localhost:3100` |
| **Jaeger/Trace** | DOWNSTREAM_SLOW_SPAN, ERROR_SPAN, TIMEOUT_SPAN, HIGH_LATENCY_ROOT | 14 | `http://localhost:16686` |

**Fallback 策略:** 尝试 HTTP 连接 → 失败则 log warning + 使用 fixture client → 零停机。

**收集结果（fixture mode）:**
- Total evidence: **34 条**
- Prometheus: 12, Loki: 8, Jaeger: 14
- Source report 按来源分别统计

**Validation fix:** `mergeSourceReports()` 替代 `putAll()`，正确累加多 service 的 evidenceCount 和 evidenceTypes（修复 payment 覆盖 order 统计的 bug）。

## 5. RCA Workflow Summary

**Workflow:** `InvestigationWorkflow.runFromMemory()`

- 新增 in-memory 执行路径，避免临时文件 I/O
- 接收预构建的 `List<Evidence>` 直接输入
- 走标准 pipeline: hypotheses → verification → confidence scoring → comparison → decision

**Scenario G 4 个假设:**
1. `deployment_regression` — 部署变更引起
2. `downstream_dependency_latency` — 下游依赖延迟（**正确根因**）
3. `pod_oom_killed` — Pod OOM
4. `resource_saturation` — 资源饱和

**Decision output:**
- `decision_type`: `insufficient_evidence`（MVP 阶段，阈值保守）
- `confidence_score`: 0.05
- `confidenceResults`: 4 条（每个假设的评分）
- `verificationResults`: 4 条（每个假设的验证证据）

**设计约束:** Decision 是确定性的，LLM 不影响分数。

## 6. LLM Proposal Summary

Step V 复用已有的 `LlmReportSynthesizer`（Step S 建立）：
- LLM 仅用于 **报告润色**，不参与假设生成或评分
- `InvestigationDecision` 和 `ConfidenceResult` 完全由确定性算法产出
- `HypothesisComparison.scoreGap` 由 `HypothesisComparator` 计算

**LLM 不参与的环节:**
- ❌ 假设生成
- ❌ 证据评分
- ❌ 决策判定
- ❌ 置信度计算

**LLM 参与的环节:**
- ✅ Markdown 报告生成（advisory）
- ✅ 假设解释文本（advisory）

## 7. Probe Preview Summary

Step V **不执行** probe。Scenario G 使用预定义的 fixture evidence。

Probe Execution Framework（Step R）已就绪：
- `ProbeExecutor` + `ProbeIntentRouter` + 5 个 `ProbeMapper`
- 可以接受 LLM proposer 生成的 probe intent 并执行
- Step W 将用 live evidence 重新运行 RCA

**已注册的 probe 类型:**
- Prometheus: `CHECK_ERROR_RATE`, `CHECK_LATENCY`, `CHECK_MEMORY`, `CHECK_CPU`, `CHECK_RESTART_RATE`
- Loki: `CHECK_HTTP_5XX`, `CHECK_DOWNSTREAM_TIMEOUT`, `CHECK_ERRORS`
- Trace: `CHECK_DOWNSTREAM_SLOW`, `CHECK_ERROR_SPAN`, `CHECK_TIMEOUT`
- Kubernetes: `CHECK_POD_STATUS`, `CHECK_EVENTS`, `CHECK_RESTARTS`
- Alertmanager: `CHECK_ACTIVE_ALERTS`

## 8. Chinese UI Changes Summary

**新增 Tab:** "实时排查" — 完整中文调查控制台

**UI 组件:**

| 组件 | 功能 |
|------|------|
| 故障模式选择器 | latency / error / timeout 三种故障模式 |
| 运行按钮 | "模拟运行 (Fixture)" + "实时运行 (Live)" |
| 证据收集面板 | 按来源显示证据数量 + 状态标签 |
| 假设分析表格 | 4 个假设的名称、分类、信号、分数 |
| 决策结果卡 | 决策类型 + 置信度 + 分数差距 + 竞争说明 |
| 结果列表 | 历史场景列表 |
| 进度条 | 7 阶段执行进度 |
| 重置按钮 | 一键重置故障 |

**文案:** 全中文，技术字段名保留英文（如 `LATENCY_P95`）。

**Validation fix:** JS 字段名适配：
- `rca.confidence_results` → `rca.confidenceResults`（`InvestigationResult` 无 `@JsonProperty`）
- `rca.verification_results` → `rca.verificationResults`
- `dec.decision_type`, `dec.confidence_score` 保持 snake_case（`InvestigationDecision` 有 `@JsonProperty`）

## 9. API / Makefile Summary

**REST API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/live-scenario/simulate` | Fixture 模拟运行 |
| `POST` | `/api/live-scenario/run` | Live/Simulate 运行（可选 body: `{mode, faultMode, waitSeconds, enableLlm}`） |
| `GET` | `/api/live-scenario/latest` | 获取最近一次结果 |
| `GET` | `/api/live-scenario/{scenarioId}` | 按 ID 获取结果 |
| `GET` | `/api/live-scenario` | 列出所有结果 |
| `POST` | `/api/live-scenario/reset` | 重置所有 demo service 故障 |

**Makefile Targets:**

| Target | Command |
|--------|---------|
| `make live-scenario-simulate` | `curl -s .../simulate` |
| `make live-scenario-run` | `curl -s -X POST .../run` (live mode, latency fault) |
| `make live-scenario-latest` | `curl -s .../latest` |
| `make live-scenario-reset` | `curl -s -X POST .../reset` |

## 10. Test Command and Result

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn test
```

**Result:**
```
Modules: 15 (11 + demo-services 4)
Tests: 560 total, 0 failures, 0 errors
Time: ~26s
BUILD SUCCESS
```

**Per-module breakdown:**

| Module | Tests |
|--------|-------|
| sre-agent-core | 124 |
| sre-agent-llm | 51 |
| sre-agent-k8s-provider | 48 |
| sre-agent-prometheus-provider | 43 |
| sre-agent-loki-provider | 30 |
| sre-agent-alertmanager-provider | 45 |
| sre-agent-trace-provider | 66 |
| sre-agent-probe-executor | 46 |
| sre-agent-server | 66 |
| sre-agent-cli | 15 |
| demo-services (4 sub-modules) | 7 + 10 + 9 = 26 |

**Step V new tests:** 12 (LiveScenarioControllerTest: 8, LiveEvidenceReportTest: 2, LiveScenarioResultTest: 2)

**No tests require live Prometheus/Loki/Jaeger/Kind.**

## 11. Manual Local Validation Result

**Server start:**
```bash
java --enable-preview -jar sre-agent-server/target/sre-agent-server-0.1.0-SNAPSHOT.jar
```

**API 验证结果:**

| Endpoint | Status | Detail |
|----------|--------|--------|
| `GET /simulate` | ✅ | COMPLETED, 34 evidence, 4 hypotheses, 4 confidenceResults, 4 verificationResults |
| `POST /run` | ✅ | COMPLETED, prometheus=12, loki=8, jaeger=14, decision=insufficient_evidence |
| `GET /latest` | ✅ | 返回最近场景结果 |
| `GET /api/live-scenario` | ✅ | 返回历史列表 (count: 3) |
| `POST /reset` | ✅ | `{"message":"All faults reset to normal","status":"ok"}` |

**Kind cluster 验证:**
- 3 demo services (order/payment/inventory) 全部 Running
- Port-forward: 18081/18082/18083 + 9090/3100/3200/16686
- Fault injection: `POST /fault-config` → `{"status":"ok"}`

**UI 验证:**
- `http://localhost:8080` → 实时排查 tab 可点击
- 模拟运行和实时运行按钮可用
- 证据收集、假设分析、决策结果渲染正确

## 12. Known Limitations

| # | Limitation | Reason |
|---|-----------|--------|
| 1 | `GET /simulate` 每次生成新 scenarioId，无法复现 | UUID-based ID，无持久化 |
| 2 | Loki/Jaeger unreachable 时 console 显示 warning | Feature: 未做 port-forward 时走 fixture 降级 |
| 3 | `decision_type = insufficient_evidence` | MVP 阈值保守，需要 Step W probe evidence 补充 |
| 4 | 无持久化存储 | `InMemoryInvestigationStore`，重启丢失 |
| 5 | 无 auth/TLS | Step V scope 不含安全层 |
| 6 | `runFromMemory()` 不触发 probe | Probe 需要显式调用，Step W 会实现 |
| 7 | Evidence 端口硬编码在 `LiveEvidenceCollector` | 配置来自 `application.properties`，但 fixture 模式不读配置 |
| 8 | Scenario G 只有一条调用链 | 不支持自定义拓扑 |

## 13. Recommended Next Step

**Step W: Post-Probe RCA Re-run / Decision Update Policy**

**Scope:**
- 用 live/probe evidence 重新运行 RCA
- Base decision 保持 immutable
- Before/after comparison
- 定义 approval/guardrail policy
- UI: RCA 前后对比、新增证据列表、置信度变化、人工确认入口

**依赖:** Step V (已完成) + Step R (Probe Execution Framework, 已完成)

---

## Known Constraints To Preserve

- ✅ No post-probe RCA re-run in Step V
- ✅ No silent decision mutation
- ✅ No auto remediation
- ✅ No production auth/TLS/multi-tenant work
- ✅ No tests requiring live backend
- ✅ Existing Scenario E/F continue to work (124 + 48 core/k8s tests)
- ✅ Step T/U UI continues to work (observability status, demo topology)

## Next Step Preview

> Do not implement next step yet.

**Step W: Post-Probe RCA Re-run / Decision Update Policy**

Backend:
- Explicitly re-run RCA with probe/live evidence
- Keep base decision immutable
- Produce before/after comparison
- Define approval/guardrail policy

UI:
- RCA 前后对比
- 新增证据列表
- 置信度变化
- 为什么结论发生变化
- 人工确认入口，可选
