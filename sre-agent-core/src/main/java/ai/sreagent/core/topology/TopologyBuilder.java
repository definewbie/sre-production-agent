package ai.sreagent.core.topology;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.ServiceTopology;
import ai.sreagent.core.domain.TopologyEdgeSource;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds the effective topology for one RCA run.
 *
 * <p>The configured topology is the stable baseline. Evidence can add observed
 * dependency edges when it carries explicit service relationship information,
 * such as {@code downstream=payment-service}, {@code trace_dependency_path}, or
 * timeout logs that name the called service.</p>
 */
public class TopologyBuilder {

    private static final List<String> DOWNSTREAM_ATTRS = List.of(
            "downstream", "dependency", "to_service", "toService",
            "downstream_service", "downstreamService", "target_service", "targetService"
    );
    private static final List<String> UPSTREAM_ATTRS = List.of(
            "upstream", "caller", "from_service", "fromService", "parent_service", "parentService"
    );

    private static final Pattern ARROW = Pattern.compile(
            "\\b([a-zA-Z0-9_-]+-service)\\b\\s*(?:->|→)\\s*\\b([a-zA-Z0-9_-]+-service)\\b");
    private static final Pattern CALLING = Pattern.compile(
            "(?i)\\b(?:calling|calls|to|depends\\s+on|downstream)\\s+([a-zA-Z0-9_-]+-service)\\b");
    private static final Pattern PARENT = Pattern.compile(
            "(?i)\\bparent\\s+([a-zA-Z0-9_-]+-service)\\b");

    public ServiceTopology build(ServiceTopology configuredTopology, List<Evidence> evidence) {
        Map<String, List<String>> dependencies = configuredTopology != null
                ? mutableCopy(configuredTopology.toDependencyMap())
                : new LinkedHashMap<>();
        Map<String, Map<String, TopologyEdgeSource>> sources = copySources(configuredTopology, dependencies);

        if (evidence != null) {
            for (Evidence item : evidence) {
                for (DependencyEdge edge : observedEdges(item)) {
                    addEdge(dependencies, sources, edge.from(), edge.to(), edge.source());
                }
            }
        }

        return new ServiceTopology(dependencies, sources);
    }

    private Map<String, List<String>> mutableCopy(Map<String, List<String>> dependencies) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (var entry : dependencies.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private Map<String, Map<String, TopologyEdgeSource>> copySources(
            ServiceTopology topology,
            Map<String, List<String>> dependencies
    ) {
        Map<String, Map<String, TopologyEdgeSource>> sources = new LinkedHashMap<>();
        if (topology == null) {
            return sources;
        }
        for (var entry : dependencies.entrySet()) {
            for (String dependency : entry.getValue()) {
                TopologyEdgeSource source = topology.getEdgeSource(entry.getKey(), dependency);
                sources.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                        .put(dependency, source != null ? source : TopologyEdgeSource.CONFIGURED_TOPOLOGY);
            }
        }
        return sources;
    }

    private List<DependencyEdge> observedEdges(Evidence evidence) {
        if (evidence == null) {
            return List.of();
        }

        List<DependencyEdge> edges = new ArrayList<>();
        String service = cleanService(evidence.service());
        String downstream = firstServiceAttribute(evidence, DOWNSTREAM_ATTRS);
        if (isUsable(service) && isUsable(downstream)) {
            edges.add(new DependencyEdge(service, downstream, sourceFor(evidence)));
        }

        String upstream = firstServiceAttribute(evidence, UPSTREAM_ATTRS);
        if (isUsable(upstream) && isUsable(service)) {
            edges.add(new DependencyEdge(upstream, service, sourceFor(evidence)));
        }

        String content = evidence.content();
        if (content != null) {
            var arrowMatcher = ARROW.matcher(content);
            while (arrowMatcher.find()) {
                edges.add(new DependencyEdge(
                        arrowMatcher.group(1), arrowMatcher.group(2), sourceFor(evidence)));
            }

            var parentMatcher = PARENT.matcher(content);
            if (parentMatcher.find() && isUsable(service)) {
                edges.add(new DependencyEdge(parentMatcher.group(1), service, sourceFor(evidence)));
            }

            var callingMatcher = CALLING.matcher(content);
            while (callingMatcher.find()) {
                String target = callingMatcher.group(1);
                if (isUsable(service) && isUsable(target)) {
                    edges.add(new DependencyEdge(service, target, sourceFor(evidence)));
                }
            }
        }

        return edges;
    }

    private String firstServiceAttribute(Evidence evidence, List<String> keys) {
        if (evidence.attributes() == null) {
            return null;
        }
        for (String key : keys) {
            Object value = evidence.attributes().get(key);
            String service = cleanService(value != null ? value.toString() : null);
            if (isUsable(service)) {
                return service;
            }
        }
        return null;
    }

    private void addEdge(
            Map<String, List<String>> dependencies,
            Map<String, Map<String, TopologyEdgeSource>> sources,
            String from,
            String to,
            TopologyEdgeSource source
    ) {
        if (!isUsable(from) || !isUsable(to) || from.equals(to)) {
            return;
        }
        dependencies.computeIfAbsent(from, k -> new ArrayList<>());
        if (!dependencies.get(from).contains(to)) {
            dependencies.get(from).add(to);
        }
        dependencies.computeIfAbsent(to, k -> new ArrayList<>());

        sources.computeIfAbsent(from, k -> new LinkedHashMap<>())
                .merge(to, source, this::strongerSource);
    }

    private TopologyEdgeSource sourceFor(Evidence evidence) {
        if ("trace_dependency_path".equals(evidence.evidenceType())) {
            return TopologyEdgeSource.TRACE;
        }
        return TopologyEdgeSource.OBSERVED_DEPENDENCY;
    }

    private TopologyEdgeSource strongerSource(TopologyEdgeSource left, TopologyEdgeSource right) {
        return priority(right) < priority(left) ? right : left;
    }

    private int priority(TopologyEdgeSource source) {
        return switch (source) {
            case TRACE -> 0;
            case OBSERVED_DEPENDENCY -> 1;
            case CONFIGURED_TOPOLOGY -> 2;
            case STATIC_FALLBACK -> 3;
        };
    }

    private String cleanService(String service) {
        if (service == null || service.isBlank() || "unknown".equalsIgnoreCase(service)) {
            return null;
        }
        return service.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private boolean isUsable(String service) {
        return service != null && !service.isBlank() && service.endsWith("-service");
    }

    private record DependencyEdge(String from, String to, TopologyEdgeSource source) {}
}
