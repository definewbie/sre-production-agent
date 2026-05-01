# Future Roadmap

## Priority Depends on Interview Target

| Target Role | Priority Step | Reason |
|---|---|---|
| AI Agent Engineer | ~~Step G: LLM synthesis~~ ✅ Done → Step J: OpenAI provider | Demonstrates principled LLM integration |
| SRE / Platform Engineer | Step H/I: Real evidence providers | Demonstrates K8s observability knowledge |
| Engineering Manager / Architect | Polish architecture narrative | Demonstrates design trade-off reasoning |

---

## Step G: LLM Report Synthesis ✅ COMPLETED

**Status:** Completed. See `docs/llm-positioning.md` for full details.

**What was built:**
- `sre-agent-llm` module with `LlmClient` interface, `MockLlmClient`, `LlmPromptBuilder`, `LlmReportSynthesizer`
- `LlmEnhancedReport` record separating `base*` (deterministic) from LLM-generated fields
- Prompt guardrails: LLM cannot change decision, scores, evidence, or invent K8s/EC2/RDS/CMDB facts
- REST API: `POST /api/investigations/scenario-e/llm-summary`
- UI: LLM-assisted explanation section with Authoritative/Advisory badges and guardrail notice
- 23 new tests (111 total)

---

## Step H: Local Kubernetes Setup

**Goal:** Run a local K8s cluster for realistic demo evidence collection.

**What to build:**
- `kind` (Kubernetes in Docker) cluster configuration
- Deploy order-service and payment-service (simple spring-boot apps or mock servers)
- Inject faults (deployment with config change, latency injection, OOM simulation)
- Script to capture real K8s events, pod status, and resource metrics as evidence JSON

**Why this matters:**
- Moves the demo from static JSON to live K8s
- Demonstrates K8s operational knowledge
- Provides realistic evidence for testing

**Estimated effort:** 2-3 days

---

## Step I: Prometheus / Loki / K8s Evidence Providers

**Goal:** Replace static JSON evidence with real observability data.

**What to build:**
- `EvidenceProvider` interface in core
- `PrometheusEvidenceProvider` — queries PromQL for metric-based evidence
- `LokiEvidenceProvider` — queries LogQL for log-based evidence
- `KubernetesEvidenceProvider` — queries K8s API for deployment events, pod status, resource metrics
- `CompositeEvidenceProvider` — orchestrates multiple providers
- Configuration per environment (which providers to use, connection details)

**Architecture:**
```
InvestigationWorkflow
  ↓
CompositeEvidenceProvider
  ├── PrometheusEvidenceProvider
  ├── LokiEvidenceProvider
  └── KubernetesEvidenceProvider
  ↓
List<Evidence>
```

**Estimated effort:** 3-5 days

---

## Step I+: Multi-Platform Model & Evidence Providers

**Goal:** Remove K8s-centric assumptions from the domain model and support non-K8s deployment targets (EC2 instances, AWS managed services).

**Why:** Real production environments are heterogeneous — a single incident may involve K8s-deployed services calling RDS databases behind ALB, with ElastiCache as a caching layer. The agent must be able to collect and reason about evidence from all these platforms.

### Model Changes (Prerequisite)

| Record | Field Change | Rationale |
|---|---|---|
| `IncidentTask` | `namespace` → `scope` | Not all platforms have namespaces. Use `scope` for namespace / AZ / VPC / region. |
| `IncidentTask` | Add `platform` field | `"kubernetes"` / `"ec2"` / `"managed_service"`. Determines which evidence providers to activate. |
| `Evidence` | `service` → `entity` | RDS instance, ElastiCache cluster, ALB — these aren't "services". `entity` is platform-neutral. |
| `Evidence` | Add `entityType` field | `"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`. Enables type-aware pattern matching. |

**Impact assessment:**
- Core pipeline (VerificationEngine, ConfidenceScorer, HypothesisComparator) — **zero changes**. They operate on `evidenceType` strings only.
- `HypothesisEngine` — minor: reads `incident.service()` for `affectedService`.
- Alert JSON / Evidence JSON — field name updates.
- Tests — update JSON fixtures and field references.
- Estimated test updates: ~15-20 files, mechanical changes.

### New Evidence Providers

| Provider | Source | Evidence Types |
|---|---|---|
| `Ec2EvidenceProvider` | CloudWatch Metrics + EC2 API | `ec2_status_check_impaired`, `ec2_cpu_steal_high`, `ebs_iops_saturation` |
| `AwsManagedServiceEvidenceProvider` | CloudWatch + RDS/ElastiCache API | `rds_connection_exhaustion`, `rds_replica_lag_high`, `elasticache_eviction_spike`, `elasticache_replication_lag` |
| `CmdbTopologyProvider` | Internal CMDB / service registry | `service_dependency_match`, `deployment_topology`, `service_owner`, `capacity_baseline` |

### New Diagnostic Patterns

| Pattern | Root Cause Type | Key Evidence |
|---|---|---|
| `ec2_instance_degradation` | `infra_degradation` | CPU steal time, EBS IOPS saturation, status check impaired |
| `rds_connection_exhaustion` | `resource_exhaustion` | max_connections reached, connection timeout spike, replica lag |
| `elasticache_memory_pressure` | `resource_pressure` | eviction rate spike, swap usage, replication lag |
| `managed_service_failover` | `infra_failover` | multi-AZ failover event, DNS endpoint change, connection reset burst |

### Why This Design Works

The core insight: **the scoring pipeline is already platform-agnostic**. It only cares about `evidenceType` string matching and confidence weights. Adding AWS evidence types and patterns follows the exact same mechanism as the existing K8s patterns — no special casing needed.

**Estimated effort:** 3-4 days (model changes 1 day + providers 2-3 days)

---

## Step J: Human Feedback and Confidence Calibration

**Goal:** Close the feedback loop — on-call engineers confirm or correct decisions.

**What to build:**
- Feedback endpoint: `POST /api/investigations/{id}/feedback`
- Feedback schema: confirmed decision? correct hypothesis? suggested correction?
- Calibration service: adjust confidence weights based on accumulated feedback
- Calibration metrics: accuracy over time, false positive/negative rates

**Why this matters:**
- Transitions from manual weights to data-driven weights
- Demonstrates ML-lite feedback loop design
- Provides measurable improvement over time

**Estimated effort:** 3-5 days

---

## Step K: More Diagnostic Patterns

**Goal:** Expand coverage beyond the current 3 patterns.

**Candidates:**
- DNS resolution failure
- Certificate expiry / TLS handshake errors
- Network partition / connectivity loss
- Database connection pool exhaustion
- Rate limiting / throttling
- Configuration drift
- Hot loop / CPU spike from bad code path
- Disk I/O saturation
- Cascading failure / circuit breaker missing

**Implementation pattern:** Each pattern follows the same structure — define evidence requirements, supporting types, counter types, and confidence weights. Add to `BuiltinPatterns` or load from external configuration.

**Estimated effort:** 1-2 days per pattern (including tests and evidence JSON)

---

## Beyond Step K (Longer Term)

These are not committed — listed for discussion only:

| Area | Description | Complexity |
|---|---|---|
| Multi-service correlation | Handle incidents spanning multiple services | High |
| Remediation suggestions | Not just root cause, but actionable fixes with risk assessment | Medium |
| Historical pattern matching | Compare current incident against similar past incidents | Medium |
| Slack / Teams integration | Post investigation results to incident channels | Low |
| Runbook automation | Link decision to specific runbook steps | Medium |
| AIOps benchmarking | Compare agent accuracy against human-only RCA | High |

---

## Architecture Principles for All Steps

1. **Core workflow does not change.** New functionality is added through new interfaces and implementations, not by modifying existing workflow steps.

2. **Evidence providers are pluggable.** The workflow accepts `List<Evidence>` — it doesn't care where the evidence comes from.

3. **LLM stays at the edges.** LLM consumes investigation output, never participates in the investigation.

4. **Every step is tested.** New features come with tests that verify behavior independently of the full workflow.

5. **No over-engineering.** Each step adds the minimum necessary to demonstrate the capability. The project is an MVP, not a production platform.

---

## 中文版

# 未来路线图

## 优先级取决于面试目标

| 目标岗位 | 优先步骤 | 原因 |
|---|---|---|
| AI Agent 工程师 | ~~Step G: LLM 综合报告~~ ✅ 已完成 → Step J: OpenAI 提供者 | 展示了规范的 LLM 集成能力 |
| SRE / 平台工程师 | Step H/I: 真实证据提供者 | 展示了 K8s 可观测性知识 |
| 工程经理 / 架构师 | 完善架构叙事 | 展示了设计权衡推理能力 |

---

## Step G: LLM 报告综合 ✅ 已完成

**状态：** 已完成。详见 `docs/llm-positioning.md`。

**已构建内容：**
- `sre-agent-llm` 模块，包含 `LlmClient` 接口、`MockLlmClient`、`LlmPromptBuilder`、`LlmReportSynthesizer`
- `LlmEnhancedReport` 记录，将 `base*`（确定性）字段与 LLM 生成的字段分离
- 提示词防护机制：LLM 不能更改决策、评分、证据，也不能捏造 K8s/EC2/RDS/CMDB 事实
- REST API：`POST /api/investigations/scenario-e/llm-summary`
- UI：LLM 辅助说明部分，带有权威/建议标签和防护说明
- 23 个新测试（共 111 个）

---

## Step H: 本地 Kubernetes 搭建

**目标：** 运行本地 K8s 集群，用于真实的演示证据收集。

**需要构建的内容：**
- `kind`（Docker 中的 Kubernetes）集群配置
- 部署 order-service 和 payment-service（简单的 Spring Boot 应用或模拟服务器）
- 注入故障（配置变更导致的部署问题、延迟注入、OOM 模拟）
- 脚本：捕获真实的 K8s 事件、Pod 状态和资源指标，输出为证据 JSON

**为什么重要：**
- 将演示从静态 JSON 提升为实时 K8s
- 展示 K8s 运维知识
- 为测试提供真实的证据数据

**预计工作量：** 2-3 天

---

## Step I: Prometheus / Loki / K8s 证据提供者

**目标：** 用真实的可观测性数据替换静态 JSON 证据。

**需要构建的内容：**
- 核心模块中的 `EvidenceProvider` 接口
- `PrometheusEvidenceProvider` — 通过 PromQL 查询指标类证据
- `LokiEvidenceProvider` — 通过 LogQL 查询日志类证据
- `KubernetesEvidenceProvider` — 通过 K8s API 查询部署事件、Pod 状态、资源指标
- `CompositeEvidenceProvider` — 编排多个提供者
- 按环境配置（使用哪些提供者、连接详情）

**架构：**
```
InvestigationWorkflow
  ↓
CompositeEvidenceProvider
  ├── PrometheusEvidenceProvider
  ├── LokiEvidenceProvider
  └── KubernetesEvidenceProvider
  ↓
List<Evidence>
```

**预计工作量：** 3-5 天

---

## Step I+: 多平台模型与证据提供者

**目标：** 移除领域模型中以 K8s 为中心的假设，支持非 K8s 部署目标（EC2 实例、AWS 托管服务）。

**原因：** 真实的生产环境是异构的——单个事件可能涉及 K8s 部署的服务调用 RDS 数据库，背后是 ALB，使用 ElastiCache 作为缓存层。Agent 必须能够从所有这些平台收集和推理证据。

### 模型变更（前置条件）

| 记录 | 字段变更 | 理由 |
|---|---|---|
| `IncidentTask` | `namespace` → `scope` | 并非所有平台都有命名空间。使用 `scope` 表示命名空间 / 可用区 / VPC / 区域。 |
| `IncidentTask` | 新增 `platform` 字段 | `"kubernetes"` / `"ec2"` / `"managed_service"`。决定激活哪些证据提供者。 |
| `Evidence` | `service` → `entity` | RDS 实例、ElastiCache 集群、ALB——这些不是"服务"。`entity` 是平台中性的。 |
| `Evidence` | 新增 `entityType` 字段 | `"service"` / `"instance"` / `"database"` / `"cache"` / `"load_balancer"`。支持类型感知的模式匹配。 |

**影响评估：**
- 核心流水线（VerificationEngine、ConfidenceScorer、HypothesisComparator）——**零修改**。它们仅操作 `evidenceType` 字符串。
- `HypothesisEngine`——小改：读取 `incident.service()` 获取 `affectedService`。
- Alert JSON / Evidence JSON——字段名更新。
- 测试——更新 JSON 固定数据和字段引用。
- 预计测试更新：约 15-20 个文件，机械性修改。

### 新证据提供者

| 提供者 | 数据源 | 证据类型 |
|---|---|---|
| `Ec2EvidenceProvider` | CloudWatch 指标 + EC2 API | `ec2_status_check_impaired`、`ec2_cpu_steal_high`、`ebs_iops_saturation` |
| `AwsManagedServiceEvidenceProvider` | CloudWatch + RDS/ElastiCache API | `rds_connection_exhaustion`、`rds_replica_lag_high`、`elasticache_eviction_spike`、`elasticache_replication_lag` |
| `CmdbTopologyProvider` | 内部 CMDB / 服务注册表 | `service_dependency_match`、`deployment_topology`、`service_owner`、`capacity_baseline` |

### 新诊断模式

| 模式 | 根因类型 | 关键证据 |
|---|---|---|
| `ec2_instance_degradation` | `infra_degradation` | CPU 窃取时间、EBS IOPS 饱和、状态检查异常 |
| `rds_connection_exhaustion` | `resource_exhaustion` | 达到最大连接数、连接超时激增、副本延迟 |
| `elasticache_memory_pressure` | `resource_pressure` | 驱逐率激增、交换区使用、复制延迟 |
| `managed_service_failover` | `infra_failover` | 多可用区故障转移事件、DNS 端点变更、连接重置激增 |

### 为什么这个设计可行

核心洞察：**评分流水线已经是平台无关的**。它只关心 `evidenceType` 字符串匹配和置信度权重。添加 AWS 证据类型和模式遵循与现有 K8s 模式完全相同的机制——无需特殊处理。

**预计工作量：** 3-4 天（模型变更 1 天 + 提供者 2-3 天）

---

## Step J: 人工反馈与置信度校准

**目标：** 闭环反馈——值班工程师确认或纠正决策。

**需要构建的内容：**
- 反馈端点：`POST /api/investigations/{id}/feedback`
- 反馈模式：确认的决策？正确的假设？建议的纠正？
- 校准服务：根据累积反馈调整置信度权重
- 校准指标：随时间变化的准确率、误报/漏报率

**为什么重要：**
- 从手动权重过渡到数据驱动权重
- 展示类 ML 反馈循环设计
- 提供可衡量的持续改进

**预计工作量：** 3-5 天

---

## Step K: 更多诊断模式

**目标：** 将覆盖范围扩展到当前 3 个模式之外。

**候选模式：**
- DNS 解析失败
- 证书过期 / TLS 握手错误
- 网络分区 / 连接丢失
- 数据库连接池耗尽
- 限流 / 节流
- 配置漂移
- 热循环 / 错误代码路径导致的 CPU 飙升
- 磁盘 I/O 饱和
- 级联故障 / 缺少熔断器

**实现模式：** 每个模式遵循相同的结构——定义证据需求、支持类型、反证类型和置信度权重。添加到 `BuiltinPatterns` 或从外部配置加载。

**预计工作量：** 每个模式 1-2 天（包括测试和证据 JSON）

---

## Step K 以后（长期方向）

这些尚未纳入计划——仅供讨论：

| 领域 | 描述 | 复杂度 |
|---|---|---|
| 多服务关联 | 处理跨多个服务的事件 | 高 |
| 修复建议 | 不仅提供根因，还提供带风险评估的可执行修复方案 | 中 |
| 历史模式匹配 | 将当前事件与类似的历史事件进行比对 | 中 |
| Slack / Teams 集成 | 将调查结果发送到事件频道 | 低 |
| Runbook 自动化 | 将决策关联到具体的 Runbook 步骤 | 中 |
| AIOps 基准测试 | 将 Agent 准确率与纯人工 RCA 进行比较 | 高 |

---

## 所有步骤的架构原则

1. **核心工作流不变。** 新功能通过新接口和实现添加，而不是修改现有工作流步骤。

2. **证据提供者可插拔。** 工作流接受 `List<Evidence>`——它不关心证据来自哪里。

3. **LLM 保持在边缘。** LLM 消费调查输出，永远不参与调查过程。

4. **每一步都有测试。** 新功能附带独立验证行为的测试，不依赖完整工作流。

5. **不过度工程。** 每一步只添加展示能力所需的最小内容。这是一个 MVP，不是生产平台。
