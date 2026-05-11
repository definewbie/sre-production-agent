package ai.sreagent.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * A directed graph of service dependencies built from topology configuration.
 *
 * <p>Provides query methods used by evidence collection (leaves skip
 * downstream queries), RCA deduplication (affected node traversal), and
 * ConfidenceScorer (topology context for hypothesis ranking).</p>
 *
 * <h3>Graph model</h3>
 * <p>Edge direction: {@code A.dependsOn → [B, C]} means A calls B and C.
 * A is upstream, B/C are downstream. Fault can propagate either direction.</p>
 */
public class ServiceTopology {

    /** All service names in the graph. */
    @JsonProperty("services")
    private final Set<String> services;

    /** downstream.get(A) = set of services A depends on (A calls these). */
    @JsonProperty("downstream")
    private final Map<String, Set<String>> downstream;

    /** upstream.get(B) = set of services that call B (inverse of downstream). */
    @JsonProperty("upstream")
    private final Map<String, Set<String>> upstream;

    /**
     * Create a topology from a list of service dependency declarations.
     *
     * @param serviceDeps each entry is [serviceName, [dep1, dep2, ...]]
     */
    public ServiceTopology(Map<String, List<String>> serviceDeps) {
        this.services = new LinkedHashSet<>();
        this.downstream = new LinkedHashMap<>();
        this.upstream = new LinkedHashMap<>();

        for (var entry : serviceDeps.entrySet()) {
            String service = entry.getKey();
            List<String> deps = entry.getValue();

            services.add(service);
            downstream.computeIfAbsent(service, k -> new LinkedHashSet<>());
            if (deps != null) {
                downstream.get(service).addAll(deps);
                for (String dep : deps) {
                    services.add(dep);
                    upstream.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(service);
                }
            }
        }
    }

    /** True if the service has no downstream dependencies (leaf node). */
    public boolean isLeaf(String service) {
        Set<String> deps = downstream.get(service);
        return deps == null || deps.isEmpty();
    }

    /** True if no service calls this one (root / entry point). */
    public boolean isRoot(String service) {
        Set<String> up = upstream.get(service);
        return up == null || up.isEmpty();
    }

    /** All services that this service depends on (direct downstream). */
    public Set<String> getDownstream(String service) {
        return Collections.unmodifiableSet(downstream.getOrDefault(service, Set.of()));
    }

    /** All services that call this service (direct upstream). */
    public Set<String> getUpstream(String service) {
        return Collections.unmodifiableSet(upstream.getOrDefault(service, Set.of()));
    }

    /**
     * Find all services transitively affected when {@code service} fails.
     * Traverses upstream: if inventory fails, returns [inventory, order-service],
     * since order calls inventory.
     */
    public Set<String> findAffectedNodes(String service) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(service);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            for (String up : getUpstream(current)) {
                if (!visited.contains(up)) queue.add(up);
            }
        }
        return visited;
    }

    /**
     * Return the full chain of services from {@code leaf} up to the root,
     * ordered from leaf (most downstream) to root (entry point).
     */
    public List<String> getChainUp(String leaf) {
        List<String> chain = new ArrayList<>();
        String current = leaf;
        while (current != null) {
            chain.add(current);
            Set<String> up = getUpstream(current);
            if (up.isEmpty()) break;
            current = up.iterator().next(); // Take first if multiple upstream
        }
        return chain;
    }

    /** Number of services in the graph. */
    public int size() {
        return services.size();
    }

    @Override
    public String toString() {
        return "ServiceTopology{services=" + services
                + ", edges=" + downstream.size() + "}";
    }
}
