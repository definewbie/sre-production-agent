# Alertmanager Incident / Alert Evidence Provider

> Step O: Alertmanager Provider v1

## Overview

The `sre-agent-alertmanager-provider` module bridges **Alertmanager alerts** into the SRE Agent's RCA workflow. It converts Alertmanager v2 API alert JSON into two outputs:

1. **IncidentTask** — normalized incident entry for the RCA workflow
2. **Evidence** — semantic alert lifecycle evidence (firing, resolved, severity, silenced, etc.)

This provider is **read-only** — it does not create silences, manage routing, or modify Alertmanager state.

## Architecture

```
Alertmanager v2 API (/api/v2/alerts)
        ↓
AlertmanagerClient (fixture | http)
        ↓
AlertmanagerResponseParser
        ↓
AlertmanagerAlertFilter
        ↓
┌─────────────────────────────┐
│ AlertmanagerIncidentMapper  │ → IncidentTask
│ AlertmanagerEvidenceMapper  │ → List<Evidence>
└─────────────────────────────┘
        ↓
AlertmanagerResult (incidents + evidence + rawSummary)
```

## Components

### Client Layer

| Class | Purpose |
|---|---|
| `AlertmanagerClient` | Interface: `getAlerts(labelMatchers, includeResolved)` |
| `FixtureAlertmanagerClient` | Deterministic fixture reader for tests |
| `HttpAlertmanagerClient` | Optional live Alertmanager HTTP client |
| `AlertmanagerClientConfig` | Config: baseUrl, timeout, headers |

### Parser Layer

| Class | Purpose |
|---|---|
| `AlertmanagerResponseParser` | Parses Alertmanager v2 JSON → `List<AlertmanagerAlert>` |
| `AlertmanagerAlert` | Parsed alert: labels, annotations, startsAt, endsAt, state, fingerprint |

Handles: missing fields, zero timestamps, invalid JSON, empty responses.

### Filter Layer

| Class | Purpose |
|---|---|
| `AlertmanagerAlertFilter` | Filters by label matchers, firing/resolved state |

### Mapper Layer

| Class | Purpose |
|---|---|
| `AlertmanagerIncidentMapper` | Converts `AlertmanagerAlert` → `IncidentTask` |
| `AlertmanagerEvidenceMapper` | Converts alerts → semantic `Evidence` |
| `AlertmanagerEvidenceTypes` | Evidence type constants |

### Provider Layer

| Class | Purpose |
|---|---|
| `AlertmanagerProvider` | Orchestrates collection: client → parse → filter → map |
| `AlertmanagerRequest` | Input: incidentId, labelMatchers, includeResolved, onlyFiring |
| `AlertmanagerResult` | Output: incidents, evidence, rawSummary |

## Evidence Types

| Evidence Type | Trigger | Strength |
|---|---|---|
| `alert_firing` | Active/firing alert | 0.80 |
| `alert_resolved` | Alert with valid endsAt | 0.50 |
| `alert_severity_high` | Severity = critical/high/page | 0.75 |
| `alert_silenced` | silencedBy not empty | 0.60 |
| `alert_inhibited` | inhibitedBy not empty | 0.60 |
| `alert_grouped` | Multiple related alerts | 0.65 |
| `alert_no_signal` | Empty alert response | 0.00 |

Source: `alertmanager`

## Incident Mapping

Alerts are mapped to `IncidentTask`:

| Alert Field | IncidentTask Field |
|---|---|
| `labels.alertname` | `alertName` |
| `labels.service/app/job` | `service` |
| `labels.namespace` | `namespace` |
| `labels.severity` | `severity` |
| `startsAt` | `startedAt` |
| `fingerprint` | deterministic `id` |

Service fallback chain: `service` → `app` → `job` → `pod` → `deployment` → `unknown-service`

## CLI Usage

```bash
# Fixture mode (default, no live Alertmanager needed)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-alertmanager-alerts \
  --service order-service \
  --namespace demo \
  --output examples/evidence/alertmanager_high_error_rate.json \
  --reader fixture

# HTTP mode (requires Alertmanager URL)
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-alertmanager-alerts \
  --service order-service \
  --namespace demo \
  --output examples/evidence/alertmanager_live.json \
  --reader http \
  --alertmanager-url http://localhost:9093

# With separate incident/evidence outputs
java --enable-preview -jar sre-agent-cli/target/sre-agent-cli-*.jar \
  collect-alertmanager-alerts \
  --service order-service \
  --output examples/evidence/alert_evidence.json \
  --incident-output examples/alerts/alert_incidents.json \
  --reader fixture
```

### CLI Arguments

| Argument | Required | Description |
|---|---|---|
| `--output` | Yes | Output path for evidence JSON |
| `--service` | No | Service name filter |
| `--namespace` | No | Kubernetes namespace (default: default) |
| `--alert-name` | No | Alert name filter |
| `--reader` | No | `fixture` (default) or `http` |
| `--alertmanager-url` | For http | Alertmanager URL |
| `--include-resolved` | No | Include resolved alerts |
| `--only-firing` | No | Only firing alerts |
| `--incident-output` | No | Separate incident output path |

## Fixtures

Located in `src/main/resources/fixtures/alertmanager/`:

| Fixture | Content |
|---|---|
| `firing_high_error_rate.json` | Single active HighErrorRate alert |
| `resolved_high_error_rate.json` | Same alert, resolved |
| `grouped_downstream_latency.json` | Two related downstream latency alerts |
| `multiple_alerts.json` | 3 alerts: error rate, memory, crash loop |
| `empty_alerts.json` | Empty array |

## Constraints

- `sre-agent-core` has **zero Alertmanager dependency**
- Provider is **read-only** — no silence/routing management
- `mvn test` does **not require live Alertmanager**
- Provider only emits `IncidentTask` and `Evidence` — it does not decide RCA
- Alertmanager v2 API only (`/api/v2/alerts`)

## Relationship to Other Providers

| Provider | Evidence Type | Purpose |
|---|---|---|
| **Alertmanager** | Alert lifecycle | Incident entry, severity, timing |
| **Prometheus** | Metrics | Error rate, latency, saturation |
| **Loki** | Logs | Error messages, stack traces |
| **Kubernetes** | Runtime | Pod state, restarts, events |

All produce normalized `Evidence` for the same `RCA` core workflow.

## Future Integration

The LLM Hypothesis Proposer (future) can use alert context:
- Alert name and severity for hypothesis generation
- Firing/resolved lifecycle for temporal reasoning
- Grouped alerts for cascade detection

But any hypothesis must still go through evidence verification.
