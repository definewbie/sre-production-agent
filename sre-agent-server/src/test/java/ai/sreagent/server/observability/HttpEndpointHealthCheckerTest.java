package ai.sreagent.server.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpEndpointHealthCheckerTest {

    @Test
    void shouldReturnConnectedOnSuccess() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        var checker = new HttpEndpointHealthChecker(mockClient);
        var config = new ObservabilityEndpointConfig("Prometheus", "prometheus", "http://localhost:9090", "/-/ready");

        var status = checker.check(config);

        assertThat(status.status()).isEqualTo("connected");
        assertThat(status.name()).isEqualTo("Prometheus");
        assertThat(status.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReturnDisconnectedOnServerError() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        var checker = new HttpEndpointHealthChecker(mockClient);
        var config = new ObservabilityEndpointConfig("Loki", "loki", "http://localhost:3100", "/ready");

        var status = checker.check(config);

        assertThat(status.status()).isEqualTo("disconnected");
        assertThat(status.message()).contains("HTTP 503");
    }

    @Test
    void shouldReturnDisconnectedOnIOException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        var checker = new HttpEndpointHealthChecker(mockClient);
        var config = new ObservabilityEndpointConfig("Jaeger", "trace", "http://localhost:16686", "/api/services");

        var status = checker.check(config);

        assertThat(status.status()).isEqualTo("disconnected");
        assertThat(status.message()).contains("unreachable");
    }

    @Test
    void shouldReturnNotConfiguredWhenUrlBlank() {
        var checker = new HttpEndpointHealthChecker();
        var config = new ObservabilityEndpointConfig("Test", "test", "  ", "/health");

        var status = checker.check(config);

        assertThat(status.status()).isEqualTo("not_configured");
    }

    @Test
    void shouldReturnNotConfiguredWhenConfigNull() {
        var checker = new HttpEndpointHealthChecker();

        var status = checker.check(null);

        assertThat(status.status()).isEqualTo("not_configured");
    }

    @Test
    void shouldReturnNotConfiguredWhenUrlNull() {
        var checker = new HttpEndpointHealthChecker();
        var config = new ObservabilityEndpointConfig("Test", "test", null, "/health");

        var status = checker.check(config);

        assertThat(status.status()).isEqualTo("not_configured");
    }
}
