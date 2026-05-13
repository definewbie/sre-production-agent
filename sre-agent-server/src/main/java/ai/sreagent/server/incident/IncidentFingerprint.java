package ai.sreagent.server.incident;

import ai.sreagent.core.domain.ServiceTopology;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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
    /** Default incident normalization window. */
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    /**
     * Create a fingerprint from a service and topology.
     * The chainSummary is the connected topology component containing the service,
     * treating dependencies as undirected for normalization. This groups alerts
     * from order-service and payment-service into one incident window when they
     * are part of the same call chain.
     */
    public static IncidentFingerprint from(String service, ServiceTopology topology) {
        return from(service, topology, DEFAULT_WINDOW);
    }

    public static IncidentFingerprint from(String service, ServiceTopology topology, Duration window) {
        return from(service, topology, window, Instant.now());
    }

    static IncidentFingerprint from(String service, ServiceTopology topology, Duration window, Instant timestamp) {
        String chain = connectedComponent(service, topology).stream()
                .sorted()
                .collect(Collectors.joining("→"));
        long windowSeconds = normalizedWindowSeconds(window);
        long bucket = timestamp.getEpochSecond() / windowSeconds;
        return new IncidentFingerprint(chain, bucket);
    }

    private static long normalizedWindowSeconds(Duration window) {
        if (window == null || window.isZero() || window.isNegative()) {
            return DEFAULT_WINDOW.toSeconds();
        }
        return Math.max(1, window.toSeconds());
    }

    private static Set<String> connectedComponent(String service, ServiceTopology topology) {
        Set<String> visited = new LinkedHashSet<>();
        if (service == null || service.isBlank() || topology == null || topology.size() == 0) {
            if (service != null && !service.isBlank()) {
                visited.add(service);
            }
            return visited;
        }

        Deque<String> queue = new ArrayDeque<>();
        queue.add(service);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (String next : topology.getDownstream(current)) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
            for (String next : topology.getUpstream(current)) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
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
