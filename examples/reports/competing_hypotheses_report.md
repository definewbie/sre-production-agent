# Competing Hypotheses Report: HighErrorRate on order-service

## Decision

Decision: competing_hypotheses
Selected hypothesis: hyp_deployment_regression
Competing hypothesis: hyp_downstream_dependency_latency
Confidence score: 0.64
Score gap: 0.06

## Summary

order-service triggered HighErrorRate at 2026-04-28T10:08:00Z.

The top two hypotheses are close in score, so the agent preserves both explanations instead of forcing a single RCA.

## Hypothesis Scores

| Hypothesis | Score | Level | Decision |
|---|---:|---|---|
| hyp_deployment_regression | 0.64 | medium | probable_root_cause |
| hyp_downstream_dependency_latency | 0.58 | low | uncertain |
| hyp_pod_oom_killed | 0.05 | very_low | insufficient_evidence |

## Leading Hypothesis

hyp_deployment_regression

## Competing Hypotheses

- hyp_downstream_dependency_latency

## Why deployment regression Leads

- deploy_event_near_alert_window: order-service v1.2.3 deployed to demo namespace by ci-pipeline
- error_rate_spike_after_deploy: order-service HTTP 5xx rate increased from 0.2% to 8.7% within 3 minutes of deployment
- dependency_timeout_logs: Repeated payment timeout errors: 'payment timeout after 500ms' appearing every 2-3 seconds in order-service logs
- retry_timeout_config_change: Git diff in v1.2.3 shows payment client timeout changed from 2000ms to 500ms in commit abc1234

## Why downstream dependency latency Remains Plausible

- dependency_timeout_logs: Repeated payment timeout errors: 'payment timeout after 500ms' appearing every 2-3 seconds in order-service logs
- downstream_latency_spike: payment-service P95 latency increased moderately from 120ms to 450ms
- service_dependency_match: Service topology shows order-service has a synchronous dependency on payment-service for checkout flow

## Counter Evidence

### Against hyp_deployment_regression

- downstream_latency_spike: payment-service P95 latency increased moderately from 120ms to 450ms
- historical_timeout_logs_present: Same payment timeout errors appeared at low frequency (1-2 per hour) before deployment, indicating pre-existing condition

### Against hyp_downstream_dependency_latency

- deploy_event_near_alert_window: order-service v1.2.3 deployed to demo namespace by ci-pipeline
- downstream_5xx_absent: payment-service HTTP 5xx rate remained normal at 0.1%, no significant increase observed

## Contradictions

- Timeout logs existed before the deployment, so the deployment may not be the only cause.
- Downstream payment-service latency also increased, so dependency latency remains a competing explanation.
- payment-service 5xx did not increase, so downstream failure is not fully confirmed.
- A recent deployment is temporally correlated with the alert, so deployment regression remains a competing explanation.
- No OOMKilled, restart, or memory pressure evidence was found.

## Suggested Next Probes

1. Compare timeout error rate before and after deployment.
2. Check payment-service latency by endpoint.
3. Roll back order-service in staging or canary and compare error rate.
4. Inspect retry timeout config effect on payment calls.

## Calibration Notes

MVP confidence score is based on manually assigned, explainable SRE diagnostic weights. It is not learned from historical incidents yet.

## Event Trace Note

Run the CLI with --show-trace to inspect the investigation path.
