package ai.sreagent.alertmanager;

import ai.sreagent.alertmanager.client.AlertmanagerClientConfig;
import ai.sreagent.alertmanager.client.HttpAlertmanagerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAlertmanagerClientTest {

    private HttpAlertmanagerClient client;

    @BeforeEach
    void setUp() {
        AlertmanagerClientConfig config = AlertmanagerClientConfig.of("http://localhost:19999");
        client = new HttpAlertmanagerClient(config);
    }

    /**
     * Use reflection to invoke the private buildAlertsUrl method for URL construction tests.
     */
    private String invokeBuildAlertsUrl(Map<String, String> labelMatchers, boolean includeResolved) {
        try {
            Method method = HttpAlertmanagerClient.class.getDeclaredMethod(
                    "buildAlertsUrl", Map.class, boolean.class);
            method.setAccessible(true);
            return (String) method.invoke(client, labelMatchers, includeResolved);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke buildAlertsUrl via reflection", e);
        }
    }

    // ------------------------------------------------------------------ //
    //  URL construction with label matchers
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("URL construction with label matchers")
    class UrlWithLabelMatchers {

        @Test
        @DisplayName("should include filter params for label matchers")
        void shouldIncludeFilterParams() {
            Map<String, String> matchers = new LinkedHashMap<>();
            matchers.put("service", "order-service");
            matchers.put("namespace", "demo");

            String url = invokeBuildAlertsUrl(matchers, true);

            assertThat(url).startsWith("http://localhost:19999/api/v2/alerts?");
            assertThat(url).contains("filter=");
            assertThat(url).contains("service");
            assertThat(url).contains("order-service");
            assertThat(url).contains("namespace");
            assertThat(url).contains("demo");
        }
    }

    // ------------------------------------------------------------------ //
    //  URL construction without label matchers
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("URL construction without label matchers")
    class UrlWithoutLabelMatchers {

        @Test
        @DisplayName("should construct URL without filter params")
        void shouldConstructUrlWithoutFilters() {
            String url = invokeBuildAlertsUrl(Map.of(), true);

            assertThat(url).isEqualTo("http://localhost:19999/api/v2/alerts");
        }

        @Test
        @DisplayName("should construct URL without filter params for null matchers")
        void shouldConstructUrlForNullMatchers() {
            String url = invokeBuildAlertsUrl(null, true);

            assertThat(url).isEqualTo("http://localhost:19999/api/v2/alerts");
        }
    }

    // ------------------------------------------------------------------ //
    //  active=true when includeResolved=false
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("active param when includeResolved=false")
    class ActiveParam {

        @Test
        @DisplayName("should include active=true when includeResolved=false")
        void shouldIncludeActiveTrue() {
            String url = invokeBuildAlertsUrl(Map.of(), false);

            assertThat(url).contains("active=true");
        }

        @Test
        @DisplayName("should not include active param when includeResolved=true")
        void shouldNotIncludeActiveWhenResolvedIncluded() {
            String url = invokeBuildAlertsUrl(Map.of(), true);

            assertThat(url).doesNotContain("active=");
        }
    }

    // ------------------------------------------------------------------ //
    //  isAvailable for unreachable host
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("should return false for unreachable host")
        void shouldReturnFalseForUnreachable() {
            assertThat(client.isAvailable()).isFalse();
        }
    }

    // ------------------------------------------------------------------ //
    //  clientName
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("clientName()")
    class ClientName {

        @Test
        @DisplayName("should return 'http'")
        void shouldReturnHttp() {
            assertThat(client.clientName()).isEqualTo("http");
        }
    }
}
