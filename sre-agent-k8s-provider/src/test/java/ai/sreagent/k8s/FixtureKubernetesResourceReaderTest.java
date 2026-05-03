package ai.sreagent.k8s;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FixtureKubernetesResourceReaderTest {

    private FixtureKubernetesResourceReader reader;

    @BeforeEach
    void setUp() {
        reader = new FixtureKubernetesResourceReader();
    }

    @Nested
    @DisplayName("Availability")
    class Availability {
        @Test
        @DisplayName("should always be available")
        void alwaysAvailable() {
            assertThat(reader.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should report fixture as reader name")
        void readerName() {
            assertThat(reader.readerName()).isEqualTo("fixture");
        }
    }

    @Nested
    @DisplayName("Resource reading")
    class ResourceReading {
        @Test
        @DisplayName("should read CrashLoopBackOff pod fixture")
        void readCrashLoopPod() throws IOException {
            String json = reader.readResource("pods", "payment-service-xyz", "production", null);
            assertThat(json).contains("CrashLoopBackOff");
            assertThat(json).contains("payment-service-7d9f8b6c4-x2k9p");
        }

        @Test
        @DisplayName("should read OOMKilled pod fixture")
        void readOomKilledPod() throws IOException {
            String json = reader.readResource("pods", "order-oom", "production", null);
            assertThat(json).contains("OOMKilled");
        }

        @Test
        @DisplayName("should read deployment fixture")
        void readDeployment() throws IOException {
            String json = reader.readResource("deployments", "payment-service", "production", null);
            assertThat(json).contains("payment-service");
            assertThat(json).contains("replicas");
        }

        @Test
        @DisplayName("should read events fixture")
        void readEvents() throws IOException {
            String json = reader.readResource("events", "", "production", null);
            assertThat(json).contains("EventList");
            assertThat(json).contains("BackOff");
        }

        @Test
        @DisplayName("should read service fixture")
        void readService() throws IOException {
            String json = reader.readResource("services", "payment-service", "production", null);
            assertThat(json).contains("ClusterIP");
        }
    }
}
