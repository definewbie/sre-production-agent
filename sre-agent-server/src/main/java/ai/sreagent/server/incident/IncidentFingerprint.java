package ai.sreagent.server.incident;

import ai.sreagent.core.domain.ServiceTopology;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unique fingerprint for incident deduplication.
 *
 * <p>Two RCAs on the same topology chain within the same time window
 * are considered duplicates. This prevents separate RCA invocations for
 * each node on the same dependency chain when all are affected by the
 * same root cause.
 *
 * <p>Example: if inventory-service latency causes order-service and payment-service
 * to also show errors, only one RCA is triggered for the chain, not one per service.
 */
public record IncidentFingerprint(
        String chainSummary,
        long timeWindowBucket
) {
    /** 1-minute time window for deduplication. */
    private static final long WINDOW_MINUTES = 1;

    /**
     * Create a fingerprint from the affected service and topology.
     * The chainSummary includes all services in the topology that are 
     * reachable from (or upstream of) the affected service.
     */
    public static IncidentFingerprint from(String service, ServiceTopology topology) {
        String chain = topology.findAffectedNodes(service).stream()
                .sorted()
                .collect(Collectors.joining("→"));
        long bucket = System.currentTimeMillis() / (WINDOW_MINUTES * 60_000);
        return new IncidentFingerprint(chain, bucket);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentFingerprint( final String cs, final long twb))) return false;
        return twb == timeWindowBucket && Objects.equals(cs, chainSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chainSummary, timeWindowBucket);
    }
}
