# Changelog

## [V.2-UI-6.1] - 2026-05-07 — Alert Relevance Filtering & RCA Eligibility Guard

### Summary

Adds intelligent alert classification to separate business service alerts from platform/infrastructure noise. The Service Health Overview now shows only service-scoped alerts, and the RCA trigger endpoint enforces an eligibility guard that rejects non-business alerts with HTTP 400.

### Backend

- **AlertRelevanceClassifier** — classifies alerts into 5 categories: SERVICE_ALERT, PLATFORM_ALERT, WATCHDOG_ALERT, UNSUPPORTED_ALERT, IGNORED_ALERT
- **ClassifiedAlert** — classification result DTO with `relevance`, `rcaEligible`, `ineligibleReason`
- **AlertsResponse** — new API response wrapper with `alerts[]` + `summary{}` (counts per category)
- **AlertView** — extended with `relevance`, `rcaEligible`, `ineligibleReason` fields (14-param record)
- **IncidentService** — injects AlertRelevanceClassifier, adds RCA eligibility guard at triggerRcaFromAlert entry
- **IncidentController** — returns AlertsResponse (breaking change from flat array); returns HTTP 400 for ineligible RCA triggers

### Frontend

- **ServiceHealthOverview** — KPI shows service alert count only; subtitle shows "共 N 条（已过滤 M 条平台/基础设施告警）"; alert list filtered to SERVICE_ALERT only
- **API client** — `getFiringAlerts()` returns `ApiResponse<AlertsResponse>`; new types: AlertRelevance, AlertSummary, AlertsResponse

### Tests

- **55 backend tests** — AlertRelevanceClassifierTest (28), AlertViewTest (9), AlertsResponseTest (4), IncidentControllerTest (14)
- **8 Playwright E2E tests** — alert-relevance-filtering.spec.ts
- All **63 tests passing** (0 failures)

### Live Validation

- Tested against live kind cluster: 10 alerts → 9 PLATFORM + 1 WATCHDOG, 0 SERVICE
- RCA trigger on Watchdog → HTTP 400 ✅
- Frontend shows "活跃告警: 0" with filter summary ✅

---

## [V.2-UI-6] - 2026-05-07 — Alert-Driven Incident Intake

### Summary

Closes the first production-like end-to-end path in the SRE Agent: **Alertmanager alert → IncidentTask → RCA analysis**. Users can now view firing alerts from the Service Health Overview page and trigger RCA analysis with a single button click.

### Backend

- **IncidentController** — 5 new REST endpoints for alert polling and incident-driven RCA
  - `GET /api/incidents/alerts` — poll firing alerts from Alertmanager
  - `POST /api/incidents/from-alert` — trigger RCA from alert fingerprint
  - `GET /api/incidents/{id}` — get incident record
  - `GET /api/incidents/{id}/rca` — get incident RCA result
  - `GET /api/incidents/{id}/report` — get incident markdown report
- **IncidentService** — core orchestration: poll alerts → map to IncidentTask → collect evidence → run RCA
- **IncidentRcaResultView** — response DTO with incidentId, alertName, severity, status, rcaResult, evidenceReport
- **AlertView** — simplified alert view DTO (fingerprint, alertName, service, namespace, severity, status, labels, annotations)
- **IncidentRecord** — in-memory aggregate of incidentTask + alert + rcaResult + evidenceReport

### Frontend

- **ServiceHealthOverview** — alert cards with "触发 RCA 分析" buttons, wired to real `/api/incidents/from-alert` endpoint
- **RcaAnalysisPanel** — accepts `alertIncidentId` prop for alert-driven RCA display with breadcrumb context
- **App.tsx** — state-based navigation: `handleRcaTriggered(incidentId)` → auto-navigate to RCA page

### Tests

- **34 backend tests** — IncidentControllerTest (WebMvcTest + MockMvc), AlertViewTest, IncidentRcaTriggerRequestTest, IncidentRcaResultViewTest
- **5 Playwright E2E tests** — alert loading, KPI card, RCA trigger navigation, breadcrumb, hypotheses+evidence display
- All **39 tests passing** (0 failures)

---

## [V.2-UI-5] - 2026-05-06 — Evidence Drilldown Page

### Summary

Per-hypothesis evidence breakdown page with raw data inspection and source attribution.

### Frontend

- **EvidenceDrilldownPanel** — per-hypothesis evidence cards showing supporting/counter/missing classification
- Raw evidence JSON viewer with source attribution (Prometheus, Loki, Jaeger, K8s, Alertmanager)

---

## [V.2-UI-4] - 2026-05-05 — RCA Analysis Page (Real API)

### Summary

Connected RCA analysis page to real `LiveScenarioService` API with semantic typing for Kubernetes evidence.

### Backend

- Semantic typing for Kubernetes evidence (PodStatus → DEGRADED/CRASHING/TERMINATED)
- V.2-UI-4.1: interview docs + semantic typing fix

### Frontend

- **RcaAnalysisPanel** — full hypothesis → verification → confidence → decision display
- Real API integration via `runLiveScenarioForRca()`

---

## [V.2-UI-3] - 2026-05-04 — Service Health Overview

### Summary

KPI dashboard with service health table and interactive topology graph.

### Frontend

- KPI cards: service count, unhealthy, alerts, affected users
- Service table with health metrics (error rate, P95 latency, throughput, saturation, restarts)
- D3 force layout topology graph with fault annotation
- Chaos fault injection controls

---

## [V.2-UI-2] - 2026-05-03 — React Rewrite + Environment Status

### Summary

Complete rewrite from single-page HTML to React + TypeScript + Vite application.

### Frontend

- `sre-agent-ui/` React app with Vite
- Sidebar navigation with 6 pages
- Environment Status page connected to real `/api/observability/status`
- Sidebar environment summary badges (Prometheus, Loki, Jaeger, Demo, API)
- E2E test setup with Playwright
