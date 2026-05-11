# Hermes Task: Live Observability E2E for V.2-RCA-1A.4

## 背景

当前分支已完成 V.2-RCA-1A.4 的核心改造：

```text
- ServiceTopology
- PropagationPath
- TopologyBuilder
- TopologyPathResolver
- configured topology 注入 workflow
- observed evidence 合成 effective topology
- propagationScore
- server 级 TopologyProvider -> InvestigationWorkflow 测试
```

最近安全点：

```text
7d2aeb1 V.2-RCA-1A.4: cover configured topology provider workflow
```

单元 / reactor 测试已通过：

```bash
mvn -pl sre-agent-server -am test
```

现在请执行真实 live E2E，不需要 simulation。

## 目标

验证 demo-services + observability tools 真实链路是否能证明：

```text
payment-service 故障
  -> order-service 受影响
  -> Prometheus / Loki / Jaeger / Kubernetes 收集证据
  -> RCA workflow 使用 configured topology / observed topology
  -> 解析 payment-service -> order-service propagation path
  -> propagationScore > 0
  -> downstream_dependency_latency 成为领先或强竞争假设
```

## 环境假设

用户确认：

```text
demo-services 已可用
observability tools 已可用
Prometheus / Loki / Jaeger / Kubernetes 都应该是 OK 的
```

不要跑 fixture simulation。直接跑 live。

## 需要阅读的文档

请先阅读：

```text
docs/chaos-to-rca.md
docs/demo-services-observability.md
docs/demo-services.md
docs/live-k8s-demo.md
docs/architecture/rca-causal-model.md
```

重点关注：

```text
- LiveScenarioService live mode
- demo service fault API
- Prometheus / Loki / Jaeger endpoint
- topology.yaml
- propagation path / propagationScore 在报告中的展示
```

## 建议执行顺序

### 1. 确认工作区和服务状态

```bash
git status --short
kubectl get ns
kubectl -n demo get pods -o wide
kubectl -n observability get pods -o wide
```

检查本地端口：

```bash
curl -s http://localhost:9090/-/ready
curl -s http://localhost:3100/ready
curl -s http://localhost:16686/api/services
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
```

如 server 未启动，启动：

```bash
mvn -pl sre-agent-server spring-boot:run
```

等待 `/health` OK：

```bash
curl -s http://localhost:8080/health
```

### 2. 先确认 topology 配置

检查：

```text
sre-agent-server/src/main/resources/topology.yaml
```

必须包含：

```yaml
order-service:
  dependsOn:
    - payment-service
    - inventory-service
```

### 3. 执行 live scenario

调用 live endpoint，不要 simulation：

```bash
curl -s -X POST http://localhost:8080/api/live-scenario/run \
  -H 'Content-Type: application/json' \
  -d '{
    "mode": "live",
    "faultMode": "latency",
    "faultParams": {"latencyMs": 2000},
    "waitSeconds": 30,
    "runLlmProposal": false
  }' | tee /tmp/sre-live-e2e-result.json
```

如果 latency 不足以触发明显信号，可再跑一次 timeout 或 error，但先不要改代码：

```bash
curl -s -X POST http://localhost:8080/api/live-scenario/run \
  -H 'Content-Type: application/json' \
  -d '{
    "mode": "live",
    "faultMode": "timeout",
    "faultParams": {"timeoutMs": 5000},
    "waitSeconds": 45,
    "runLlmProposal": false
  }' | tee /tmp/sre-live-e2e-timeout-result.json
```

### 4. 采集结果详情

从返回 JSON 中拿 `scenarioId` 或按 API 文档获取 latest：

```bash
curl -s http://localhost:8080/api/live-scenario/latest \
  | tee /tmp/sre-live-e2e-latest.json
```

如果有 result id：

```bash
curl -s http://localhost:8080/api/live-scenario/{scenarioId} \
  | tee /tmp/sre-live-e2e-detail.json
```

### 5. 必须验证的断言

请检查 JSON / markdown report / logs 中是否满足：

```text
1. evidence 总数 > 0
2. Prometheus 有非 _no_signal evidence
3. Loki 有非 _no_signal evidence
4. Jaeger 有非 _no_signal evidence
5. Kubernetes 如果可用，应有 pod/deployment/health evidence；如果没有，不应阻塞本次 topology 验证
6. downstream_dependency_latency hypothesis 存在
7. propagationPath 存在
8. propagationPath services 包含或等于:
   payment-service -> order-service
9. propagationPath.pathSource 是 CONFIGURED_TOPOLOGY / OBSERVED_DEPENDENCY / TRACE 之一
10. propagationScore > 0
11. markdown report 包含:
    - Propagation Score
    - 传播路径
    - payment-service
    - order-service
12. diagnosticQuality 不应因为 Loki/Prometheus/Jaeger 全盲而退化；若某 provider blind，需要说明真实原因
13. decision 可以是 probable_root_cause / competing_hypotheses / uncertain_requires_more_evidence，但必须解释为什么
```

注意：本次 E2E 重点不是强行要求某个固定 final decision，而是验证 live evidence + topology propagation 是否闭环。

## 额外诊断

如果出现 `_no_signal`，请不要立刻改代码，先定位。

### Prometheus

```bash
curl -s 'http://localhost:9090/api/v1/query?query=up' | jq .
curl -s 'http://localhost:9090/api/v1/targets' | jq '.data.activeTargets[] | {job: .labels.job, namespace: .labels.namespace, health: .health, scrapeUrl: .scrapeUrl}'
```

关键查询：

```bash
curl -G http://localhost:9090/api/v1/query \
  --data-urlencode 'query=sum(rate(http_server_requests_seconds_count{namespace="demo"}[5m])) by (service, app, status)'
```

### Loki

```bash
curl -G http://localhost:3100/loki/api/v1/query \
  --data-urlencode 'query={namespace="demo"}' \
  --data-urlencode 'limit=10'
```

### Jaeger

```bash
curl -s http://localhost:16686/api/services | jq .
curl -G http://localhost:16686/api/traces \
  --data-urlencode 'service=order-service' \
  --data-urlencode 'limit=5' | jq .
```

### Demo service direct checks

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8081/checkout
```

## 不要做的事情

```text
- 不要跑 simulation 替代 live E2E
- 不要修改算法或 expected 值来“让结果通过”
- 不要在未定位前修改 demo-services
- 不要提交 unrelated formatting
- 不要清理用户已有工作
- 不要把 LLM 结论当作最终 RCA 判断
```

## 输出报告

请生成一份简洁 E2E 报告，包含：

```text
1. 环境状态
2. 执行命令
3. 关键 API 返回摘要
4. evidence provider 状态
5. RCA decision
6. downstream_dependency_latency 分数
7. propagationPath
8. propagationScore
9. 是否满足验收标准
10. 如果失败，失败在哪一层：
   - demo service fault injection
   - Prometheus scrape/query
   - Loki log collection/query
   - Jaeger trace collection/query
   - Kubernetes evidence
   - TopologyProvider / TopologyBuilder
   - InvestigationWorkflow / ConfidenceScorer
11. 建议后续修复项
```

如果发现代码 bug，可以提出 patch 建议，但本任务优先完成 E2E 诊断报告；除非是非常小且确定的环境脚本问题，否则不要直接大改代码。
