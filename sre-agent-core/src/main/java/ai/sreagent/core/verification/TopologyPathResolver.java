package ai.sreagent.core.verification;

import ai.sreagent.core.domain.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Resolves configured topology into per-hypothesis propagation paths.
 *
 * <p>This is deliberately conservative: if it cannot identify a concrete
 * candidate dependency, it returns no path and lets the existing evidence-based
 * topology inference continue to operate.</p>
 */
public class TopologyPathResolver {

    private static final Pattern SERVICE_NAME = Pattern.compile("([a-zA-Z0-9_-]+-service)");

    public Map<String, PropagationPath> resolveAll(
            ServiceTopology topology,
            List<Evidence> evidence,
            List<Hypothesis> hypotheses,
            Map<String, DiagnosticPattern> patterns
    ) {
        if (topology == null || topology.size() == 0 || hypotheses == null || hypotheses.isEmpty()) {
            return Map.of();
        }

        Map<String, PropagationPath> results = new LinkedHashMap<>();
        for (Hypothesis hypothesis : hypotheses) {
            DiagnosticPattern pattern = patterns != null ? patterns.get(hypothesis.patternId()) : null;
            PropagationPath path = resolve(topology, evidence, hypothesis, pattern);
            if (path.isPresent()) {
                results.put(hypothesis.id(), path);
            }
        }
        return results;
    }

    public PropagationPath resolve(
            ServiceTopology topology,
            List<Evidence> evidence,
            Hypothesis hypothesis,
            DiagnosticPattern pattern
    ) {
        if (topology == null || hypothesis == null || !isTopologySensitive(pattern)) {
            return PropagationPath.NONE;
        }

        String affected = hypothesis.affectedService();
        if (affected == null || affected.isBlank()) {
            return PropagationPath.NONE;
        }

        for (String candidate : candidateServices(topology, evidence, hypothesis)) {
            if (candidate.equals(affected)) {
                continue;
            }
            PropagationPath path = topology.findImpactPath(candidate, affected);
            if (path.isPresent()) {
                return path;
            }
        }
        return PropagationPath.NONE;
    }

    private boolean isTopologySensitive(DiagnosticPattern pattern) {
        return pattern != null && "downstream_dependency_latency".equals(pattern.id());
    }

    private List<String> candidateServices(
            ServiceTopology topology,
            List<Evidence> evidence,
            Hypothesis hypothesis
    ) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addServiceNames(candidates, hypothesis.candidateCause());

        if (evidence != null) {
            for (Evidence item : evidence) {
                if (item.service() != null && !item.service().isBlank()) {
                    candidates.add(item.service());
                }
                addServiceNames(candidates, item.content());
            }
        }

        candidates.addAll(topology.getDownstream(hypothesis.affectedService()));
        return List.copyOf(candidates);
    }

    private void addServiceNames(Set<String> out, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        var matcher = SERVICE_NAME.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
    }
}
