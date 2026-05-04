package ai.sreagent.trace;

import ai.sreagent.trace.client.HttpTraceQueryClient;
import ai.sreagent.trace.client.TraceClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTraceQueryClientTest {

    private static final String FAKE_BASE_URL = "http://localhost:19999";

    private HttpTraceQueryClient jaegerClient;
    private HttpTraceQueryClient tempoClient;

    @BeforeEach
    void setUp() {
        TraceClientConfig jaegerConfig = new TraceClientConfig(
                FAKE_BASE_URL, "jaeger", Duration.ofSeconds(2), Map.of());
        TraceClientConfig tempoConfig = new TraceClientConfig(
                FAKE_BASE_URL, "tempo", Duration.ofSeconds(2), Map.of());

        jaegerClient = new HttpTraceQueryClient(jaegerConfig);
        tempoClient = new HttpTraceQueryClient(tempoConfig);
    }

    @Nested
    @DisplayName("Jaeger URL construction for findTraces")
    class JaegerFindTracesUrl {

        @Test
        @DisplayName("should throw RuntimeException when calling findTraces on unreachable Jaeger")
        void shouldThrowForUnreachableJaeger() {
            Instant start = Instant.parse("2025-06-01T11:00:00Z");
            Instant end = Instant.parse("2025-06-01T12:00:00Z");

            assertThatThrownBy(() -> jaegerClient.findTraces("order-service", start, end, 20))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Trace backend");
        }
    }

    @Nested
    @DisplayName("Jaeger URL construction for getTrace")
    class JaegerGetTraceUrl {

        @Test
        @DisplayName("should throw RuntimeException when calling getTrace on unreachable Jaeger")
        void shouldThrowForUnreachableJaeger() {
            assertThatThrownBy(() -> jaegerClient.getTrace("abc123"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Trace backend");
        }
    }

    @Nested
    @DisplayName("Tempo URL construction")
    class TempoUrlConstruction {

        @Test
        @DisplayName("should throw RuntimeException when calling findTraces on unreachable Tempo")
        void shouldThrowForUnreachableTempo() {
            Instant start = Instant.parse("2025-06-01T11:00:00Z");
            Instant end = Instant.parse("2025-06-01T12:00:00Z");

            assertThatThrownBy(() -> tempoClient.findTraces("order-service", start, end, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Trace backend");
        }

        @Test
        @DisplayName("should throw RuntimeException when calling getTrace on unreachable Tempo")
        void shouldThrowForUnreachableTempoGetTrace() {
            assertThatThrownBy(() -> tempoClient.getTrace("xyz789"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Trace backend");
        }
    }

    @Nested
    @DisplayName("config default values")
    class ConfigDefaults {

        @Test
        @DisplayName("TraceClientConfig.defaults() should return Jaeger config with correct values")
        void defaultsShouldReturnJaegerConfig() {
            TraceClientConfig config = TraceClientConfig.defaults();

            assertThat(config.baseUrl()).isEqualTo("http://localhost:16686");
            assertThat(config.backendType()).isEqualTo("jaeger");
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(config.headers()).isEmpty();
        }

        @Test
        @DisplayName("TraceClientConfig with nulls should use defaults")
        void nullValuesShouldUseDefaults() {
            TraceClientConfig config = new TraceClientConfig("http://localhost:16686", null, null, null);

            assertThat(config.backendType()).isEqualTo("jaeger");
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(config.headers()).isEmpty();
        }

        @Test
        @DisplayName("TraceClientConfig.of() should create config with base URL and backend type")
        void ofShouldCreateConfig() {
            TraceClientConfig config = TraceClientConfig.of("http://tempo:3200", "tempo");

            assertThat(config.baseUrl()).isEqualTo("http://tempo:3200");
            assertThat(config.backendType()).isEqualTo("tempo");
            assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    @DisplayName("isAvailable returns false for unreachable host")
    class IsAvailable {

        @Test
        @DisplayName("should return false for unreachable Jaeger host")
        void shouldReturnFalseForUnreachableJaeger() {
            assertThat(jaegerClient.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should return false for unreachable Tempo host")
        void shouldReturnFalseForUnreachableTempo() {
            assertThat(tempoClient.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("clientName()")
    class ClientName {

        @Test
        @DisplayName("should return 'http'")
        void shouldReturnHttp() {
            assertThat(jaegerClient.clientName()).isEqualTo("http");
            assertThat(tempoClient.clientName()).isEqualTo("http");
        }
    }
}
