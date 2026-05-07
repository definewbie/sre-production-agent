package ai.sreagent.alertmanager.relevance;

import ai.sreagent.alertmanager.parser.AlertmanagerAlert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertRelevanceClassifier")
class AlertRelevanceClassifierTest {

    private final AlertRelevanceClassifier classifier = new AlertRelevanceClassifier();

    private AlertmanagerAlert makeAlert(String alertName, String service, String namespace,
                                         String job, Map<String, String> extraLabels) {
        Map<String, String> labels = new java.util.HashMap<>();
        labels.put("alertname", alertName);
        if (service != null) labels.put("service", service);
        if (namespace != null) labels.put("namespace", namespace);
        if (job != null) labels.put("job", job);
        if (extraLabels != null) labels.putAll(extraLabels);
        return new AlertmanagerAlert(labels, Map.of(), Instant.now(), null, "active",
                "fp-" + alertName, List.of(), List.of());
    }

    // ── Service Alerts ──────────────────────────────────────────

    @Nested
    @DisplayName("Demo service alerts → SERVICE_ALERT")
    class ServiceAlertTests {

        @Test
        @DisplayName("namespace=demo + service=payment-service → SERVICE_ALERT, rcaEligible=true")
        void demoServiceInDemoNamespace() {
            var alert = makeAlert("HighLatencyP95", "payment-service", "demo", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
            assertNull(result.ineligibleReason());
        }

        @Test
        @DisplayName("app=order-service → SERVICE_ALERT (fallback chain)")
        void appLabelMatch() {
            var alert = makeAlert("HighErrorRate", null, "demo", null, Map.of("app", "order-service"));
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
        }

        @Test
        @DisplayName("service=inventory-service → SERVICE_ALERT")
        void inventoryService() {
            var alert = makeAlert("PodCrashLoop", "inventory-service", "demo", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
        }

        @Test
        @DisplayName("namespace=demo without platform job → SERVICE_ALERT")
        void demoNamespaceFallback() {
            var alert = makeAlert("SomeAppAlert", "my-app", "demo", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
        }
    }

    // ── Watchdog ────────────────────────────────────────────────

    @Nested
    @DisplayName("Watchdog alert → WATCHDOG_ALERT")
    class WatchdogTests {

        @Test
        @DisplayName("alertName=Watchdog → WATCHDOG_ALERT, rcaEligible=false")
        void watchdog() {
            var alert = makeAlert("Watchdog", "prometheus", "monitoring", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.WATCHDOG_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
            assertNotNull(result.ineligibleReason());
            assertTrue(result.ineligibleReason().contains("Watchdog"));
        }
    }

    // ── Platform Alerts ─────────────────────────────────────────

    @Nested
    @DisplayName("Platform alerts → PLATFORM_ALERT")
    class PlatformAlertTests {

        @Test
        @DisplayName("NodeClockNotSynchronising → PLATFORM_ALERT")
        void nodeClock() {
            var alert = makeAlert("NodeClockNotSynchronising", "node-exporter", "observability", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("etcdInsufficientMembers → PLATFORM_ALERT")
        void etcdInsufficient() {
            var alert = makeAlert("etcdInsufficientMembers", "etcd", "kube-system", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("etcdMembersDown → PLATFORM_ALERT")
        void etcdMembersDown() {
            var alert = makeAlert("etcdMembersDown", "etcd", "kube-system", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("KubePodCrashLooping → PLATFORM_ALERT")
        void kubePrefix() {
            var alert = makeAlert("KubePodCrashLooping", "kubelet", "kube-system", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("kube-system namespace → PLATFORM_ALERT")
        void kubeSystemNamespace() {
            var alert = makeAlert("SomeAlert", "some-svc", "kube-system", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("monitoring namespace → PLATFORM_ALERT")
        void monitoringNamespace() {
            var alert = makeAlert("SomeAlert", "some-svc", "monitoring", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("observability namespace → PLATFORM_ALERT")
        void observabilityNamespace() {
            var alert = makeAlert("SomeAlert", "some-svc", "observability", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("job=prometheus → PLATFORM_ALERT")
        void prometheusJob() {
            var alert = makeAlert("SomeAlert", "some-svc", "default", "prometheus", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("job=node-exporter → PLATFORM_ALERT")
        void nodeExporterJob() {
            var alert = makeAlert("SomeAlert", "some-svc", "default", "node-exporter", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("job=etcd → PLATFORM_ALERT")
        void etcdJob() {
            var alert = makeAlert("SomeAlert", "some-svc", "default", "etcd", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("job contains 'prometheus' (e.g. prometheus-k8s) → PLATFORM_ALERT")
        void prometheusJobContains() {
            var alert = makeAlert("SomeAlert", "some-svc", "default", "prometheus-k8s", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }
    }

    // ── TargetDown Special ──────────────────────────────────────

    @Nested
    @DisplayName("TargetDown special handling")
    class TargetDownTests {

        @Test
        @DisplayName("TargetDown + demo service → SERVICE_ALERT")
        void targetDownDemoService() {
            var alert = makeAlert("TargetDown", "order-service", "demo", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
        }

        @Test
        @DisplayName("TargetDown + prometheus job → PLATFORM_ALERT")
        void targetDownPrometheus() {
            var alert = makeAlert("TargetDown", "some-svc", "monitoring", "prometheus", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("TargetDown + node-exporter job → PLATFORM_ALERT")
        void targetDownNodeExporter() {
            var alert = makeAlert("TargetDown", "node-exporter", "observability", "node-exporter", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("TargetDown + unknown service, non-demo ns → PLATFORM_ALERT")
        void targetDownUnknownService() {
            var alert = makeAlert("TargetDown", "unknown-svc", "default", "some-job", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }

        @Test
        @DisplayName("TargetDown + demo namespace but unknown service → SERVICE_ALERT")
        void targetDownDemoNamespace() {
            var alert = makeAlert("TargetDown", "my-app", "demo", null, null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
            assertTrue(result.rcaEligible());
        }
    }

    // ── Unsupported Alerts ──────────────────────────────────────

    @Nested
    @DisplayName("Unknown/unsupported alerts → UNSUPPORTED_ALERT")
    class UnsupportedAlertTests {

        @Test
        @DisplayName("unknown service in default namespace → UNSUPPORTED_ALERT")
        void unknownService() {
            var alert = makeAlert("SomeRandomAlert", "unknown-service", "default", "some-job", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.UNSUPPORTED_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("alert in demo namespace but with platform job → platform job wins")
        void demoNamespacePlatformJob() {
            // Demo namespace but job is etcd → should be PLATFORM
            var alert = makeAlert("SomeAlert", null, "demo", "etcd", null);
            var result = classifier.classify(alert);
            assertEquals(AlertRelevance.PLATFORM_ALERT, result.relevance());
        }
    }

    // ── Null / Edge Cases ───────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("null alert → UNSUPPORTED_ALERT")
        void nullAlert() {
            var result = classifier.classify(null);
            assertEquals(AlertRelevance.UNSUPPORTED_ALERT, result.relevance());
            assertFalse(result.rcaEligible());
        }

        @Test
        @DisplayName("AlertRelevance.isRcaEligible() — only SERVICE_ALERT returns true")
        void relevanceEligibility() {
            assertTrue(AlertRelevance.SERVICE_ALERT.isRcaEligible());
            assertFalse(AlertRelevance.PLATFORM_ALERT.isRcaEligible());
            assertFalse(AlertRelevance.WATCHDOG_ALERT.isRcaEligible());
            assertFalse(AlertRelevance.UNSUPPORTED_ALERT.isRcaEligible());
            assertFalse(AlertRelevance.IGNORED_ALERT.isRcaEligible());
        }
    }

    // ── Full Coverage: All demo services ────────────────────────

    @ParameterizedTest(name = "{0} in demo ns → SERVICE_ALERT")
    @DisplayName("All demo services are SERVICE_ALERT")
    @CsvSource({
            "order-service",
            "payment-service",
            "inventory-service"
    })
    void allDemoServices(String service) {
        var alert = makeAlert("HighLatency", service, "demo", null, null);
        var result = classifier.classify(alert);
        assertEquals(AlertRelevance.SERVICE_ALERT, result.relevance());
        assertTrue(result.rcaEligible());
    }
}
