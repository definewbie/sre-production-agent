package ai.sreagent.k8s;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class KubernetesClientConfigTest {

    @Nested
    @DisplayName("Default configuration")
    class Defaults {

        @Test
        @DisplayName("defaults() should use kubeconfig mode with 10s timeout")
        void defaultsTest() {
            KubernetesClientConfig config = KubernetesClientConfig.defaults();

            assertThat(config.mode()).isEqualTo(KubernetesClientConfig.KubernetesClientMode.KUBECONFIG);
            assertThat(config.kubeconfigPath()).isNull();
            assertThat(config.context()).isNull();
            assertThat(config.requestTimeoutMs()).isEqualTo(10_000);
        }
    }

    @Nested
    @DisplayName("In-cluster configuration")
    class InCluster {

        @Test
        @DisplayName("inCluster() should use IN_CLUSTER mode")
        void inClusterTest() {
            KubernetesClientConfig config = KubernetesClientConfig.inCluster();

            assertThat(config.mode()).isEqualTo(KubernetesClientConfig.KubernetesClientMode.IN_CLUSTER);
            assertThat(config.kubeconfigPath()).isNull();
        }
    }

    @Nested
    @DisplayName("Kubeconfig with explicit path")
    class Kubeconfig {

        @Test
        @DisplayName("kubeconfig(path) should use KUBECONFIG mode with explicit path")
        void kubeconfigPathTest() {
            KubernetesClientConfig config = KubernetesClientConfig.kubeconfig("/tmp/my-kubeconfig");

            assertThat(config.mode()).isEqualTo(KubernetesClientConfig.KubernetesClientMode.KUBECONFIG);
            assertThat(config.kubeconfigPath()).isEqualTo("/tmp/my-kubeconfig");
        }
    }

    @Nested
    @DisplayName("Custom configuration via constructor")
    class Custom {

        @Test
        @DisplayName("should allow fully custom configuration")
        void customConfigTest() {
            KubernetesClientConfig config = new KubernetesClientConfig(
                KubernetesClientConfig.KubernetesClientMode.KUBECONFIG,
                "/custom/kubeconfig",
                "my-context",
                5_000
            );

            assertThat(config.mode()).isEqualTo(KubernetesClientConfig.KubernetesClientMode.KUBECONFIG);
            assertThat(config.kubeconfigPath()).isEqualTo("/custom/kubeconfig");
            assertThat(config.context()).isEqualTo("my-context");
            assertThat(config.requestTimeoutMs()).isEqualTo(5_000);
        }
    }
}
