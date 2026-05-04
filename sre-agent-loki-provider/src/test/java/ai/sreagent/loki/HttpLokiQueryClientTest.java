package ai.sreagent.loki;

import ai.sreagent.loki.client.HttpLokiQueryClient;
import ai.sreagent.loki.client.LokiClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLokiQueryClientTest {

    private HttpLokiQueryClient client;

    @BeforeEach
    void setUp() {
        LokiClientConfig config = new LokiClientConfig(
                "http://localhost:19999", Duration.ofSeconds(2), Map.of());
        client = new HttpLokiQueryClient(config);
    }

    @Nested
    @DisplayName("clientName()")
    class ClientName {

        @Test
        @DisplayName("should return 'http'")
        void shouldReturnHttp() {
            assertThat(client.clientName()).isEqualTo("http");
        }
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("should return false when Loki is unreachable")
        void shouldReturnFalseWhenUnreachable() {
            assertThat(client.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("query()")
    class Query {

        @Test
        @DisplayName("should throw RuntimeException when Loki is unreachable")
        void shouldThrowWhenUnreachable() {
            assertThatThrownBy(() -> client.query("{app=\"test\"}", Instant.now()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("queryRange()")
    class QueryRange {

        @Test
        @DisplayName("should throw RuntimeException when Loki is unreachable")
        void shouldThrowWhenUnreachable() {
            Instant now = Instant.now();
            assertThatThrownBy(() -> client.queryRange("{app=\"test\"}", now.minusSeconds(60), now, Duration.ofSeconds(15)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
