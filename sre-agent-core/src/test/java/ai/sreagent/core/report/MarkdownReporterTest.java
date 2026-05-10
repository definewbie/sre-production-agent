package ai.sreagent.core.report;

import ai.sreagent.core.domain.*;
import ai.sreagent.core.evidence.EvidenceLoader;
import ai.sreagent.core.hypothesis.HypothesisEngine;
import ai.sreagent.core.patterns.BuiltinPatterns;
import ai.sreagent.core.patterns.PatternRegistry;
import ai.sreagent.core.verification.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReporterTest {

    private String report;
    private TemporalAligner temporalAligner;
    private ProblemWindow problemWindow;
    private ConfidenceResult firstConfResult;

    @BeforeEach
    void setUp() throws Exception {
        EvidenceLoader loader = new EvidenceLoader();
        PatternRegistry registry = BuiltinPatterns.defaultRegistry();
        Map<String, DiagnosticPattern> patternMap = new LinkedHashMap<>();
        registry.all().forEach(p -> patternMap.put(p.id(), p));

        IncidentTask incident;
        List<Evidence> evidence;
        try (InputStream alertIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_alert.json");
             InputStream evidenceIs = getClass().getResourceAsStream("/scenarios/competing_hypotheses_evidence.json")) {
            incident = loader.loadAlert(alertIs);
            evidence = loader.loadEvidence(evidenceIs);
        }

        // V.2-RCA-1A.3: Derive ProblemWindow & compute temporal alignment
        problemWindow = ProblemWindow.deriveFromIncident(incident, evidence);
        temporalAligner = new TemporalAligner();

        HypothesisEngine hypEngine = new HypothesisEngine();
        List<Hypothesis> hypotheses = hypEngine.generate(incident, registry.all());

        VerificationEngine verEngine = new VerificationEngine();
        Map<String, VerificationResult> verMap = verEngine.verifyAll(hypotheses, patternMap, evidence);
        List<VerificationResult> verResults = new ArrayList<>(verMap.values());

        // Compute temporal alignment results per hypothesis
        Map<String, TemporalAlignmentResult> temporalResults =
                temporalAligner.alignAll(problemWindow, evidence, hypotheses);

        // Use 5-arg scoreAll WITH temporal results (real temporal pathway)
        ConfidenceScorer scorer = new ConfidenceScorer();
        List<ConfidenceResult> confResults = scorer.scoreAll(
                hypotheses, patternMap, verResults, evidence, temporalResults);

        HypothesisComparator comparator = new HypothesisComparator();
        HypothesisComparison comparison = comparator.compare(incident, confResults, verResults, evidence);
        InvestigationDecision decision = comparator.decide(incident, comparison, confResults);

        MarkdownReporter reporter = new MarkdownReporter();
        report = reporter.generate(incident, hypotheses, verResults, confResults,
                comparison, decision, evidence, problemWindow);

        // Capture first confidence result for breakdown assertions
        firstConfResult = confResults.get(0);
    }

    // ── Baseline structure tests (unchanged expectations) ──

    @Test
    void reportContainsTitle() {
        assertThat(report).contains("竞争假设分析报告");
    }

    @Test
    void reportContainsDecision() {
        assertThat(report).contains("竞争假设");
    }

    @Test
    void reportContainsLeadingHypothesis() {
        assertThat(report).contains("近期部署引入回归缺陷");
    }

    @Test
    void reportContainsCompetingHypothesis() {
        assertThat(report).contains("下游依赖延迟导致超时");
    }

    @Test
    void reportContainsCalibrationNotes() {
        assertThat(report).contains("校准说明");
    }

    @Test
    void reportContainsSuggestedNextProbes() {
        assertThat(report).contains("建议下一步探测");
    }

    @Test
    void reportContainsContradictions() {
        assertThat(report).contains("矛盾点");
    }

    @Test
    void reportContainsCounterEvidence() {
        assertThat(report).contains("反驳证据");
    }

    // ── V.2-RCA-1A.3: Temporal section structure ──

    @Test
    void reportContainsTemporalAlignmentSection() {
        assertThat(report).contains("时间对齐分析");
    }

    @Test
    void reportContainsTemporalScoreColumns() {
        assertThat(report).contains("Temporal Score");
        assertThat(report).contains("Temporal 置信度");
        assertThat(report).contains("Candidate First Seen");
        assertThat(report).contains("Impacted First Seen");
    }

    @Test
    void reportContainsTemporalDetailSubsections() {
        assertThat(report).contains("时间对齐详情");
        assertThat(report).contains("Temporal 说明");
    }

    // ── V.2-RCA-1A.3: Real temporal data assertions (not N/A) ──

    @Test
    void reportShowsRealTemporalScore() {
        // 两个假设同 affectedService → temporalScore 相同，约为 +0.11
        String temporalScore = String.format("%+.2f", firstConfResult.temporalAlignmentScore());
        assertThat(firstConfResult.temporalAlignmentScore()).isNotEqualTo(0.0);
        assertThat(report).contains(temporalScore);
    }

    @Test
    void reportShowsRealTimestampValues() {
        // candidateFirstSeen = 2026-04-28T09:45:00Z (ev_007, 最早 order-service 证据)
        assertThat(report).contains("2026-04-28T09:45:00");
        // impactedFirstSeen = 2026-04-28T10:06:00Z (ev_005, 最早 payment-service 证据)
        assertThat(report).contains("2026-04-28T10:06:00");
    }

    @Test
    void reportShowsRealTemporalConfidence() {
        // 两个假设有充足时间戳 → confidence = HIGH
        assertThat(report).contains("高");
    }

    @Test
    void reportShowsProblemWindowBoundaries() {
        // ProblemWindow: alert startsAt 10:08, lookback 5min, lookahead 10min
        // → [2026-04-28T10:03:00Z, 2026-04-28T10:18:00Z]
        assertThat(report).contains("Problem Window");
        assertThat(report).contains("2026-04-28T10:03:00");
        assertThat(report).contains("2026-04-28T10:18:00");
        assertThat(report).contains("alert");
    }

    @Test
    void reportDoesNotShowNAForTemporalData() {
        // 在时间对齐分析 section 之后不应该有 N/A（说明数据已贯通）
        // N/A 在报告其他部分（如 Hypothesis missingEvidence）可能出现，但在 temporal
        // table row 中 candidateFirstSeen/impactedFirstSeen 应有真实时间戳
        int temporalIdx = report.indexOf("时间对齐分析");
        assertThat(temporalIdx).isGreaterThan(0);
        String temporalSection = report.substring(temporalIdx);
        // 去掉 detail sub-heading 的 N/A fallback — temporal section 内不应有 N/A
        assertThat(temporalSection).doesNotContain("N/A");
    }

    @Test
    void reportContainsTemporalExplanationWithRealDetail() {
        // TemporalAligner generates explanation with causality + window coverage detail
        String explanation = firstConfResult.temporalExplanation();
        assertThat(explanation).isNotNull();
        assertThat(explanation).isNotEmpty();
        assertThat(explanation).doesNotContain("No timestamp");
        assertThat(explanation).doesNotContain("无 temporal");
    }

    @Test
    void reportDoesNotContainPartialPlaceholder() {
        // 真实 temporal 通路 → 不应出现 PARTIAL 标记
        assertThat(report).doesNotContain("PARTIAL");
    }
}
