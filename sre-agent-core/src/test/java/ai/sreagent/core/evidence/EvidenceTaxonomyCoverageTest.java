package ai.sreagent.core.evidence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class EvidenceTaxonomyCoverageTest {

    /**
     * All known evidence type strings produced by provider modules.
     * Since core cannot depend on provider modules, we list them explicitly here
     * to verify every provider type has a corresponding taxonomy entry.
     */
    private static final Set<String> KNOWN_PROVIDER_TYPES = Set.copyOf(java.util.List.of(
            // Prometheus
            "metric_error_rate_spike", "metric_latency_p95_spike", "metric_latency_p99_spike",
            "metric_downstream_latency_spike", "metric_memory_usage_high", "metric_cpu_usage_high",
            "metric_restart_rate_increased", "metric_request_rate_drop", "metric_no_signal",

            // Loki
            "log_timeout_error", "log_downstream_timeout", "log_exception_spike",
            "log_crash_loop", "log_oom_message", "log_db_connection_timeout",
            "log_retry_exhausted", "log_http_5xx", "log_no_signal",

            // Alertmanager
            "alert_firing", "alert_resolved", "alert_still_firing",
            "alert_severity_high", "alert_grouped", "alert_silenced",
            "alert_inhibited", "alert_near_window", "alert_no_signal",

            // Trace
            "trace_downstream_span_slow", "trace_error_span", "trace_root_span_slow",
            "trace_dependency_path", "trace_timeout_span", "trace_child_span_dominates_latency",
            "trace_no_signal",

            // K8s
            "k8s_pod_status", "k8s_deployment_status", "k8s_event",
            "container_crash_loop_backoff", "pod_restart_count_increased",
            "pod_not_ready", "deployment_metadata"
    ));

    @Test
    @DisplayName("All known provider evidence types are registered in the taxonomy")
    void allProviderTypesAreRegistered() {
        Set<String> registered = EvidenceTaxonomyRegistry.registeredTypes();

        assertThat(KNOWN_PROVIDER_TYPES)
                .as("Every known provider type should be registered in the taxonomy")
                .allMatch(registered::contains,
                        "to be present in EvidenceTaxonomyRegistry");
    }

    @Test
    @DisplayName("Each known type can be individually verified as registered")
    void eachKnownTypeIsRegistered() {
        for (String type : KNOWN_PROVIDER_TYPES) {
            assertThat(EvidenceTaxonomyRegistry.isRegistered(type))
                    .as("Evidence type '%s' should be registered", type)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Registry total size is at least 55")
    void registrySizeAtLeast55() {
        assertThat(EvidenceTaxonomyRegistry.size())
                .as("Registry should contain at least 55 evidence types (providers + static + internal)")
                .isGreaterThanOrEqualTo(55);
    }
}
