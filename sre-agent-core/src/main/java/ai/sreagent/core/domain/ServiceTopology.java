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

    /** edgeSources.get(A).get(B) = source for dependency edge A → B. */
    private final Map<String, Map<String, TopologyEdgeSource>> edgeSources;

    /**
     * Create a topology from a list of service dependency declarations.
     *
     * @param serviceDeps each entry is [serviceName, [dep1, dep2, ...]]
     */
    public ServiceTopology(Map<String, List<String>> serviceDeps) {
        this(serviceDeps, TopologyEdgeSource.CONFIGURED_TOPOLOGY);
    }

    /**
     * Create a topology from dependency declarations with a shared edge source.
     */
    public ServiceTopology(Map<String, List<String>> serviceDeps, TopologyEdgeSource edgeSource) {
        this.services = new LinkedHashSet<>();
        this.downstream = new LinkedHashMap<>();
        this.upstream = new LinkedHashMap<>();
        this.edgeSources = new LinkedHashMap<>();

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
                    edgeSources.computeIfAbsent(service, k -> new LinkedHashMap<>())
                            .put(dep, edgeSource);
                }
            }
        }
    }

    /**
     * Create a topology with explicit per-edge source metadata.
     */
    public ServiceTopology(Map<String, List<String>> serviceDeps,
                           Map<String, Map<String, TopologyEdgeSource>> edgeSources) {
        this.services = new LinkedHashSet<>();
        this.downstream = new LinkedHashMap<>();
        this.upstream = new LinkedHashMap<>();
        this.edgeSources = new LinkedHashMap<>();

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
                    TopologyEdgeSource source = Optional.ofNullable(edgeSources.get(service))
                            .map(m -> m.get(dep))
                            .orElse(TopologyEdgeSource.CONFIGURED_TOPOLOGY);
                    this.edgeSources.computeIfAbsent(service, k -> new LinkedHashMap<>())
                            .put(dep, source);
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

    /** All known services in deterministic insertion order. */
    public Set<String> getServices() {
        return Collections.unmodifiableSet(services);
    }

    /** Return a defensive copy of dependency declarations. */
    public Map<String, List<String>> toDependencyMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (String service : services) {
            copy.put(service, List.copyOf(downstream.getOrDefault(service, Set.of())));
        }
        return copy;
    }

    /** Return the source metadata for an edge, if known. */
    public TopologyEdgeSource getEdgeSource(String caller, String dependency) {
        return Optional.ofNullable(edgeSources.get(caller))
                .map(m -> m.get(dependency))
                .orElse(null);
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

    /**
     * Find a dependency path in call direction: caller → ... → dependency.
     */
    public PropagationPath findDependencyPath(
            String caller,
            String dependency,
            TopologyEdgeSource source
    ) {
        List<String> path = shortestPath(caller, dependency, this::getDownstream);
        if (path.isEmpty()) {
            return PropagationPath.NONE;
        }
        List<TopologyEdge> edges = buildEdges(path, source, PropagationDirection.UPSTREAM_TO_DOWNSTREAM);
        return PropagationPath.fromEdges(
                path,
                edges,
                PropagationDirection.UPSTREAM_TO_DOWNSTREAM,
                source,
                "Dependency path: " + String.join(" → ", path)
        );
    }

    /**
     * Find a dependency path using per-edge source metadata.
     */
    public PropagationPath findDependencyPath(String caller, String dependency) {
        List<String> path = shortestPath(caller, dependency, this::getDownstream);
        if (path.isEmpty()) {
            return PropagationPath.NONE;
        }
        List<TopologyEdge> edges = buildEdges(path, PropagationDirection.UPSTREAM_TO_DOWNSTREAM);
        return PropagationPath.fromEdges(
                path,
                edges,
                PropagationDirection.UPSTREAM_TO_DOWNSTREAM,
                strongestSource(edges),
                "Dependency path: " + String.join(" → ", path)
        );
    }

    /**
     * Find a fault-impact path in propagation direction:
     * failed downstream dependency → ... → impacted upstream caller.
     */
    public PropagationPath findImpactPath(
            String failedDependency,
            String impactedCaller,
            TopologyEdgeSource source
    ) {
        List<String> callPath = shortestPath(impactedCaller, failedDependency, this::getDownstream);
        if (callPath.isEmpty()) {
            return PropagationPath.NONE;
        }
        List<String> propagationPath = new ArrayList<>(callPath);
        Collections.reverse(propagationPath);
        List<TopologyEdge> edges = buildEdges(
                propagationPath, source, PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT);
        return PropagationPath.fromEdges(
                propagationPath,
                edges,
                PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT,
                source,
                "Impact path: " + String.join(" → ", propagationPath)
        );
    }

    /**
     * Find a fault-impact path using per-edge source metadata.
     */
    public PropagationPath findImpactPath(String failedDependency, String impactedCaller) {
        List<String> callPath = shortestPath(impactedCaller, failedDependency, this::getDownstream);
        if (callPath.isEmpty()) {
            return PropagationPath.NONE;
        }
        List<String> propagationPath = new ArrayList<>(callPath);
        Collections.reverse(propagationPath);
        List<TopologyEdge> edges = buildEdges(
                propagationPath, PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT);
        return PropagationPath.fromEdges(
                propagationPath,
                edges,
                PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT,
                strongestSource(edges),
                "Impact path: " + String.join(" → ", propagationPath)
        );
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

    private List<String> shortestPath(
            String start,
            String end,
            java.util.function.Function<String, Set<String>> neighbors
    ) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            return List.of();
        }
        if (start.equals(end)) {
            return List.of(start);
        }

        Set<String> visited = new LinkedHashSet<>();
        Deque<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(start));

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);
            if (!visited.add(current)) {
                continue;
            }
            for (String next : neighbors.apply(current)) {
                List<String> candidate = new ArrayList<>(path);
                candidate.add(next);
                if (next.equals(end)) {
                    return candidate;
                }
                if (!visited.contains(next)) {
                    queue.add(candidate);
                }
            }
        }
        return List.of();
    }

    private List<TopologyEdge> buildEdges(
            List<String> path,
            TopologyEdgeSource source,
            PropagationDirection direction
    ) {
        if (path.size() < 2) {
            return List.of();
        }
        List<TopologyEdge> edges = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            edges.add(new TopologyEdge(
                    from,
                    to,
                    source,
                    TopologyEdge.deriveConfidence(source),
                    direction,
                    i + 1,
                    from + " → " + to
            ));
        }
        return edges;
    }

    private List<TopologyEdge> buildEdges(
            List<String> path,
            PropagationDirection direction
    ) {
        if (path.size() < 2) {
            return List.of();
        }
        List<TopologyEdge> edges = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            TopologyEdgeSource source = sourceForPathEdge(from, to, direction);
            edges.add(new TopologyEdge(
                    from,
                    to,
                    source,
                    TopologyEdge.deriveConfidence(source),
                    direction,
                    i + 1,
                    from + " → " + to
            ));
        }
        return edges;
    }

    private TopologyEdgeSource sourceForPathEdge(
            String from,
            String to,
            PropagationDirection direction
    ) {
        if (direction == PropagationDirection.DOWNSTREAM_TO_UPSTREAM_IMPACT) {
            TopologyEdgeSource reverse = getEdgeSource(to, from);
            return reverse != null ? reverse : TopologyEdgeSource.CONFIGURED_TOPOLOGY;
        }
        TopologyEdgeSource source = getEdgeSource(from, to);
        return source != null ? source : TopologyEdgeSource.CONFIGURED_TOPOLOGY;
    }

    private TopologyEdgeSource strongestSource(List<TopologyEdge> edges) {
        if (edges.stream().anyMatch(e -> e.edgeSource() == TopologyEdgeSource.TRACE)) {
            return TopologyEdgeSource.TRACE;
        }
        if (edges.stream().anyMatch(e -> e.edgeSource() == TopologyEdgeSource.OBSERVED_DEPENDENCY)) {
            return TopologyEdgeSource.OBSERVED_DEPENDENCY;
        }
        if (edges.stream().anyMatch(e -> e.edgeSource() == TopologyEdgeSource.CONFIGURED_TOPOLOGY)) {
            return TopologyEdgeSource.CONFIGURED_TOPOLOGY;
        }
        return TopologyEdgeSource.STATIC_FALLBACK;
    }
}
