
package ai.sreagent.core.workflow;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.eventtrace.EventTraceStore;
import ai.sreagent.core.eventtrace.InMemoryEventTraceStore;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.report.MarkdownReporter;
import ai.sreagent.core.verification.ConfidenceScorer;
import ai.sreagent.core.verification.HypothesisComparator;
import ai.sreagent.core.verification.VerificationEngine;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared investigation workflow orchestrator.
 * Used by both CLI and Server adapters.
 * Zero Spring dependency — lives in sre-agent-core.
 */
public class InvestigationWorkflow {

    /**
     * Run investigation from JSON files (CLI / static scenarios).
     */
    public InvestigationResult run(File alertFile, File evidenceFile) throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        IncidentTask incident = loader.loadAlert(alertFile);
        List<Evidence> evidence = loader.loadEvidence(evidenceFile);
        return runFromMemory(incident, evidence);
    }

    /**
     * Run investigation from in-memory domain objects.
     * Used by live scenario runners (Step V) and server-side orchestration.
     * No file I/O required.
     */
    public InvestigationResult runFromMemory(IncidentTask incident, List<Evidence> evidence) {
        EventTraceStore traceStore = new InMemoryEventTraceStore();
        String incidentId = incident.id() != null ? incident.id()
                : "inc_" + Instant.now().toString().replace(":", "").replace(".", "");
        AtomicInteger eventCounter = new AtomicInteger(0);

        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "INCIDENT_CREATED",
                Map.of("alert", incident.alertName(), "service", incident.service())));

        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "EVIDENCE_LOADED",
                Map.of("count", evidence.size())));

        // Load patterns
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        // Generate hypotheses
        HypothesisEngine hypEngine = new HypothesisEngine();
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());
        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "HYPOTHESES_GENERATED",
                Map.of("count", hypotheses.size())));

        // Verify hypotheses
        VerificationEngine verEngine = new VerificationEngine();
        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);
        List<VerificationResult> verResults = new ArrayList<>(verMap.values());
        for (VerificationResult vr : verResults) {
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "HYPOTHESIS_VERIFIED",
                    Map.of("hypothesisId", vr.hypothesisId(),
                            "supporting", vr.supportingEvidenceIds().size(),
                            "counter", vr.counterEvidenceIds().size(),
                            "contradictions", vr.contradictions().size())));
        }

        // Score confidence
        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, verResults, evidence);
        for (ConfidenceResult cr : confResults) {
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "CONFIDENCE_SCORED",
                    Map.of("hypothesisId", cr.hypothesisId(), "score", cr.score())));
        }

        // Compare hypotheses
        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confResults, verResults, evidence);
        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "HYPOTHESES_COMPARED",
                Map.of("leading", comparison.leadingHypothesisId(),
                        "competing", comparison.competingHypothesisIds(),
                        "gap", comparison.scoreGap())));

        // Generate decision
        InvestigationDecision decision = comparator.decide(incident, comparison, confResults);
        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "DECISION_MADE",
                Map.of("decisionType", decision.decisionType(),
                        "selectedHypothesisId", decision.selectedHypothesisId(),
                        "confidenceScore", decision.confidenceScore())));

        // Generate report
        MarkdownReporter reporter = new MarkdownReporter();
        String markdownReport = reporter.generate(incident, hypotheses, verResults, confResults, comparison, decision, evidence);
        traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "REPORT_GENERATED",
                Map.of()));

        List<EventTraceEntry> eventTrace = traceStore.getByIncidentId(incidentId);

        return new InvestigationResult(
                incidentId, incident, hypotheses, verResults, confResults,
                comparison, decision, evidence, markdownReport, eventTrace
        );
    }

    private EventTraceEntry makeEvent(EventTraceStore store, String incidentId,
                                       AtomicInteger counter, String eventType,
                                       Map<String, Object> payload) {
        return new EventTraceEntry(
                "evt_" + String.format("%03d", counter.incrementAndGet()),
                incidentId,
                eventType,
                Instant.now(),
                payload
        );
    }
}
