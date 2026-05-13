package ai.sreagent.cli;

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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Command(name = "investigate", description = "Run RCA investigation on an incident alert")
public class InvestigateCommand implements Runnable {

    @Option(names = "--alert", description = "Path to alert JSON file", required = true)
    private String alertPath;

    @Option(names = "--evidence", description = "Path to evidence JSON file", required = true)
    private String evidencePath;

    @Option(names = "--output", description = "Path to output Markdown report", required = true)
    private String outputPath;

    @Option(names = "--show-trace", description = "Print event trace to stdout")
    private boolean showTrace;

    @Override
    public void run() {
        try {
            EventTraceStore traceStore = new InMemoryEventTraceStore();
            String incidentId = "inc_" + Instant.now().toString().replace(":", "").replace(".", "");
            AtomicInteger eventCounter = new AtomicInteger(0);

            // 1. Load alert
            EvidenceLoader loader = new EvidenceLoader();
            IncidentTask incident = loader.loadAlert(new File(alertPath));
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "INCIDENT_CREATED",
                    Map.of("alert", incident.alertName(), "service", incident.service())));
            System.out.println("Incident created");

            // 2. Load evidence
            List<Evidence> evidence = loader.loadEvidence(new File(evidencePath));
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "EVIDENCE_LOADED",
                    Map.of("count", evidence.size())));
            System.out.println("Evidence loaded");

            // 3. Load patterns
            PatternRegistry registry = BuiltinPatterns.defaultRegistry();
            Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
            registry.all().forEach(p -> patternMap.put(p.id(), p));

            // 4. Generate hypotheses
            HypothesisEngine hypEngine = new HypothesisEngine();
            List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "HYPOTHESES_GENERATED",
                    Map.of("count", hypotheses.size())));
            System.out.println("Hypotheses generated");

            // 5. Verify hypotheses
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
            System.out.println("Verification completed");

            // 6. Score confidence
            ConfidenceScorer scorer = new ConfidenceScorer();
            List<ConfidenceResult> confResults = scorer.scoreAll(hypotheses, patternMap, verResults, evidence);
            for (ConfidenceResult cr : confResults) {
                traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "CONFIDENCE_SCORED",
                        Map.of("hypothesisId", cr.hypothesisId(), "score", cr.score())));
            }
            System.out.println("Confidence scored");

            // 7. Compare hypotheses
            HypothesisComparator comparator = new HypothesisComparator();
            HypothesisComparison comparison = comparator.compare(incident, confResults, verResults, evidence);
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "HYPOTHESES_COMPARED",
                    Map.of("leading", comparison.leadingHypothesisId(),
                            "competing", comparison.competingHypothesisIds(),
                            "gap", comparison.scoreGap())));
            System.out.println("Hypotheses compared");

            // 8. Generate decision
            InvestigationDecision decision = comparator.decide(incident, comparison, confResults);
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "DECISION_MADE",
                    Map.of("decisionType", decision.decisionType(),
                            "selectedHypothesisId", decision.selectedHypothesisId(),
                            "confidenceScore", decision.confidenceScore())));
            System.out.println("Decision made: " + decision.decisionType());

            // 9. Generate report
            MarkdownReporter reporter = new MarkdownReporter();
            ProblemWindow problemWindow = ProblemWindow.deriveFromIncident(incident, evidence);
            String report = reporter.generate(incident, hypotheses, verResults, confResults, comparison, decision, evidence, problemWindow);

            // 10. Write report
            Path outPath = Path.of(outputPath);
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, report);
            traceStore.append(makeEvent(traceStore, incidentId, eventCounter, "REPORT_GENERATED",
                    Map.of("path", outputPath)));
            System.out.println("Report generated");

            // 11. Print summary
            printSummary(incident, evidence, confResults, comparison, decision);

            // 12. Print event trace if requested
            if (showTrace) {
                printTrace(traceStore, incidentId);
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
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

    private void printSummary(IncidentTask incident, List<Evidence> evidence,
                               List<ConfidenceResult> confResults,
                               HypothesisComparison comparison,
                               InvestigationDecision decision) {
        System.out.println();
        System.out.println("Incident: " + incident.alertName() + " on " + incident.service());
        System.out.println("Evidence loaded: " + evidence.size());
        System.out.println("Hypotheses generated: " + confResults.size());
        System.out.println();
        System.out.println("Scores:");
        confResults.stream()
                .sorted(java.util.Comparator.comparingDouble(ConfidenceResult::score).reversed())
                .forEach(c -> System.out.printf("  - %s: %.2f%n", c.hypothesisId(), c.score()));
        System.out.println();
        System.out.printf("Score gap: %.2f%n", comparison.scoreGap());
        System.out.println("Decision: " + decision.decisionType());
        System.out.println("Report generated: " + outputPath);
    }

    private void printTrace(EventTraceStore store, String incidentId) {
        System.out.println();
        System.out.println("Event Trace:");
        List<EventTraceEntry> events = store.getByIncidentId(incidentId);
        for (EventTraceEntry e : events) {
            StringBuilder line = new StringBuilder();
            line.append("[").append(e.timestamp()).append("] ");
            line.append(e.eventType()).append(": ");
            line.append(formatPayload(e.eventType(), e.payload()));
            System.out.println(line);
        }
    }

    private String formatPayload(String eventType, Map<String, Object> payload) {
        return switch (eventType) {
            case "INCIDENT_CREATED" -> payload.get("alert") + " on " + payload.get("service");
            case "EVIDENCE_LOADED" -> payload.get("count") + " evidence items";
            case "HYPOTHESES_GENERATED" -> payload.get("count") + " hypotheses";
            case "HYPOTHESIS_VERIFIED" -> payload.get("hypothesisId")
                    + " support=" + payload.get("supporting")
                    + " counter=" + payload.get("counter")
                    + " contradictions=" + payload.get("contradictions");
            case "CONFIDENCE_SCORED" -> payload.get("hypothesisId")
                    + " score=" + String.format("%.2f", payload.get("score"));
            case "HYPOTHESES_COMPARED" -> "leading=" + payload.get("leading")
                    + " competing=" + payload.get("competing")
                    + " gap=" + payload.get("gap");
            case "DECISION_MADE" -> payload.get("decisionType").toString();
            case "REPORT_GENERATED" -> payload.get("path").toString();
            default -> payload.toString();
        };
    }
}
