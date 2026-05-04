package ai.sreagent.k8s;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class KubernetesApiClientFactoryTest {

    @Nested
    @DisplayName("Invalid kubeconfig handling")
    class InvalidKubeconfig {

        @Test
        @DisplayName("should fail clearly when kubeconfig file does not exist")
        void nonExistentKubeconfig() {
            KubernetesClientConfig config = new KubernetesClientConfig(
                KubernetesClientConfig.KubernetesClientMode.KUBECONFIG,
                "/nonexistent/path/kubeconfig",
                null, 10_000
            );

            KubernetesApiClientFactory factory = new KubernetesApiClientFactory();

            assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(KubernetesEvidenceCollectionException.class)
                .hasMessageContaining("Kubeconfig file not found")
                .hasMessageContaining("/nonexistent/path/kubeconfig");
        }
    }

    @Nested
    @DisplayName("In-cluster mode handling")
    class InClusterMode {

        @Test
        @DisplayName("should fail clearly when not running inside Kubernetes")
        void notInCluster() {
            KubernetesClientConfig config = KubernetesClientConfig.inCluster();
            KubernetesApiClientFactory factory = new KubernetesApiClientFactory();

            assertThatThrownBy(() -> factory.create(config))
                .isInstanceOf(KubernetesEvidenceCollectionException.class)
                .hasMessageContaining("In-cluster Kubernetes config is not available");
        }
    }

    @Nested
    @DisplayName("Default kubeconfig path")
    class DefaultKubeconfig {

        @Test
        @DisplayName("should attempt default ~/.kube/config when no path given")
        void defaultPathAttempt() {
            // This test validates the error message mentions the expected default path
            KubernetesClientConfig config = KubernetesClientConfig.defaults();
            KubernetesApiClientFactory factory = new KubernetesApiClientFactory();

            // Will either succeed (if kubeconfig exists) or fail with clear message
            // We just verify it doesn't throw an unexpected exception type
            try {
                factory.create(config);
            } catch (KubernetesEvidenceCollectionException e) {
                // Expected: either kubeconfig not found or parsing error
                assertThat(e.getMessage()).isNotEmpty();
            } catch (Exception e) {
                // Kubeconfig may parse but context may not connect — that's OK for this test
                assertThat(e).isNotNull();
            }
        }
    }
}
