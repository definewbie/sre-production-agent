# Changelog

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
