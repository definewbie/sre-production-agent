# Alert Relevance Filtering & RCA Eligibility Guard

> V.2-UI-6.1: Alert Relevance Filtering & RCA Eligibility Guard

## Problem

In V.2-UI-6, all Alertmanager alerts were displayed on the Service Health Overview page and could trigger RCA analysis. This meant:

- **Platform alerts** (TargetDown, NodeClock, etcd, KubeScheduler, etc.) appeared in the business alert list
- **Watchdog alerts** (used for Alertmanager self-monitoring) triggered RCA
- **Unsupported alert types** (non-Kubernetes, non-application) polluted the UI
- Engineers saw 10+ infrastructure alerts alongside 0 business alerts — **signal-to-noise ratio was terrible**

The RCA engine is designed for **business service** incident analysis. Platform/infrastructure alerts should never enter the RCA pipeline.

## Solution

A two-layer filtering system:

```
Alertmanager (all alerts)
        ↓
AlertRelevanceClassifier (classify each alert)
        ↓
┌──────────────────────────────────────┐
│  SERVICE_ALERT    → show + RCA eligible  │
│  PLATFORM_ALERT   → hide + RCA blocked   │
│  WATCHDOG_ALERT   → hide + RCA blocked   │
│  UNSUPPORTED_ALERT→ hide + RCA blocked   │
│  IGNORED_ALERT    → hide + RCA blocked   │
└──────────────────────────────────────┘
        ↓                          ↓
   Frontend filter           Backend guard
   (only SERVICE_ALERT       (triggerRca rejects
    shown in UI)              non-SERVICE_ALERT → 400)
```

## Architecture

### AlertRelevanceClassifier

**Package:** `ai.sreagent.alertmanager.relevance`

Pure function classifier — takes an `AlertmanagerAlert`, returns a `ClassifiedAlert` with:

| Field | Description |
|-------|-------------|
| `relevance` | `AlertRelevance` enum value |
| `rcaEligible` | Whether this alert can trigger RCA |
| `ineligibleReason` | Human-readable Chinese reason if not eligible |

#### Classification Rules

1. **WATCHDOG_ALERT** — alert name matches `Watchdog` or `InfoInhibitor`
2. **PLATFORM_ALERT** — alert name matches platform patterns:
   - `TargetDown`, `NodeClock`, `etcd`, `KubeScheduler`, `KubeControllerManager`
   - `Prometheus`, `Alertmanager`, `KubeStateMetrics`
   - Regex patterns: `^Kubelet.*`, `^Node.*`, `^kube-.*`
3. **SERVICE_ALERT** — namespace in `DEMO_NAMESPACES` AND service label in `DEMO_SERVICES`
4. **UNSUPPORTED_ALERT** — has namespace/service labels but doesn't match service criteria
5. **IGNORED_ALERT** — missing required labels (namespace or service)

### API Contract

**Breaking change:** `GET /api/incidents/alerts` response changed from:

```json
// Old (V.2-UI-6)
[AlertView, AlertView, ...]
```

To:

```json
// New (V.2-UI-6.1)
{
  "alerts": [AlertView, ...],
  "summary": {
    "totalAlerts": 10,
    "serviceAlerts": 0,
    "platformAlerts": 9,
    "watchdogAlerts": 1,
    "unsupportedAlerts": 0,
    "ignoredAlerts": 0,
    "rcaEligibleAlerts": 0
  },
  "timestamp": "2026-05-07T10:02:40"
}
```

Each `AlertView` now includes:
- `relevance` — `AlertRelevance` enum string
- `rcaEligible` — boolean
- `ineligibleReason` — Chinese explanation (nullable)

### RCA Eligibility Guard

`POST /api/incidents/from-alert` now enforces:

```
AlertRelevanceClassifier.classify(alert)
  → if !rcaEligible → HTTP 400 + FAILED body
  → if rcaEligible → proceed with RCA
```

Response for blocked alerts:
```json
{
  "incidentId": "inc-alert-1746586000000",
  "alertName": "Watchdog",
  "status": "FAILED",
  "rcaResult": "该告警不可触发 RCA：Watchdog 是 Alertmanager 心跳检测告警，不适用业务 RCA 分析"
}
```

## Frontend Changes

### ServiceHealthOverview

- **KPI "活跃告警"** now shows `summary.serviceAlerts` count only
- **Subtitle text:** `"共 N 条（已过滤 M 条平台/基础设施告警）"`
- **Alert list title:** changed from "活跃告警" to "业务告警"
- **Alert filter:** only displays alerts where `rcaEligible === true`
- **RCA button:** only rendered for eligible alerts (defensive, backend also guards)

### API Client

- `getFiringAlerts()` return type: `ApiResponse<AlertsResponse>` (was `ApiResponse<AlertView[]>`)
- New types: `AlertRelevance`, `AlertSummary`, `AlertsResponse`
- `AlertView` extended with `relevance`, `rcaEligible`, `ineligibleReason`

## Test Coverage

| Test Suite | Tests | Status |
|-----------|-------|--------|
| AlertRelevanceClassifierTest | 28 | ✅ All pass |
| AlertViewTest | 9 | ✅ All pass |
| AlertsResponseTest | 4 | ✅ All pass |
| IncidentControllerTest | 14 | ✅ All pass |
| **Total V.2-UI-6.1** | **55** | **✅** |

### Classifier Test Categories

- SERVICE_ALERT: 5 tests (namespace+service matching)
- PLATFORM_ALERT: 5 tests (TargetDown, NodeClock, etcd, KubeScheduler, Alertmanager)
- WATCHDOG_ALERT: 3 tests (Watchdog, InfoInhibitor)
- UNSUPPORTED_ALERT: 5 tests (wrong namespace, unknown service, etc.)
- IGNORED_ALERT: 3 tests (missing labels)
- Null/empty safety: 5 tests

## Live Validation Results

Tested against live kind cluster with Alertmanager:

```json
{
  "summary": {
    "totalAlerts": 10,
    "serviceAlerts": 0,
    "platformAlerts": 9,
    "watchdogAlerts": 1,
    "rcaEligibleAlerts": 0
  }
}
```

- 9 TargetDown + NodeClock + etcd alerts → PLATFORM_ALERT ✅
- 1 Watchdog → WATCHDOG_ALERT ✅
- All `rcaEligible: false` ✅
- RCA trigger on Watchdog → HTTP 400 ✅
- Frontend shows "活跃告警: 0" + "共 10 条（已过滤 10 条平台/基础设施告警）" ✅

## Files Changed

### Backend (sre-agent-alertmanager-provider)

- `src/main/java/ai/sreagent/alertmanager/relevance/AlertRelevance.java` — enum
- `src/main/java/ai/sreagent/alertmanager/relevance/ClassifiedAlert.java` — DTO
- `src/main/java/ai/sreagent/alertmanager/relevance/AlertRelevanceClassifier.java` — classifier
- `src/test/java/ai/sreagent/alertmanager/relevance/AlertRelevanceClassifierTest.java` — 28 tests

### Backend (sre-agent-server)

- `src/main/java/ai/sreagent/server/incident/AlertView.java` — extended with relevance fields
- `src/main/java/ai/sreagent/server/incident/AlertsResponse.java` — new wrapper DTO
- `src/main/java/ai/sreagent/server/incident/IncidentService.java` — inject classifier + guard
- `src/main/java/ai/sreagent/server/incident/IncidentController.java` — return AlertsResponse + 400 on guard
- `src/test/java/ai/sreagent/server/incident/IncidentControllerTest.java` — 14 tests (rewritten)
- `src/test/java/ai/sreagent/server/incident/AlertViewTest.java` — 9 tests
- `src/test/java/ai/sreagent/server/incident/AlertsResponseTest.java` — 4 tests

### Frontend (sre-agent-ui)

- `src/api/client.ts` — types + API client update
- `src/sections/ServiceHealthOverview.tsx` — filtering + UI update
- `tests/e2e/alert-relevance-filtering.spec.ts` — 8 E2E test cases
