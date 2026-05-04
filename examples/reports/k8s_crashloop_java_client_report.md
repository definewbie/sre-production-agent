# Competing Hypotheses Report: PodCrashLooping on recommend-service

## Decision

Decision: likely_root_cause
Selected hypothesis: hyp_pod_crash_loop
Confidence score: 0.95
Score gap: 0.70

## Summary

recommend-service triggered PodCrashLooping at 2026-04-28T11:20:00Z.

pod crash loop is the leading hypothesis with score 0.95 and gap 0.70 to the next candidate.

## Hypothesis Scores

| Hypothesis | Score | Level | Decision |
|---|---:|---|---|
| hyp_pod_crash_loop | 0.95 | high | likely_root_cause |
| hyp_pod_oom_killed | 0.25 | very_low | insufficient_evidence |
| hyp_deployment_regression | 0.00 | very_low | insufficient_evidence |
| hyp_downstream_dependency_latency | 0.00 | very_low | insufficient_evidence |

## Leading Hypothesis

hyp_pod_crash_loop

## Why pod crash loop Leads

- container_crash_loop_backoff: Pod recommend-service-6495dd76cd-pq55b is in CrashLoopBackOff state. Container exiting with code 1.
- pod_restart_count_increased: Pod has restarted 5 times. Restart count is abnormally high.
- pod_not_ready: Pod readiness probe failing. Pod recommend-service-6495dd76cd-pq55b is not ready.
- deployment_metadata: Deployment nginx-smoke has 2/2 desired replicas ready.

## Counter Evidence

## Contradictions


## Suggested Next Probes

1. Gather more evidence for the leading hypothesis.
2. Verify if counter evidence can be ruled out.

## Calibration Notes

MVP confidence score is based on manually assigned, explainable SRE diagnostic weights. It is not learned from historical incidents yet.

## Event Trace Note

Run the CLI with --show-trace to inspect the investigation path.
