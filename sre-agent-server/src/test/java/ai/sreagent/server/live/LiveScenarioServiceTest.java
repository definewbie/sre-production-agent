package ai.sreagent.server.live;

import ai.sreagent.core.domain.ServiceTopology;
import ai.sreagent.server.demo.DemoServiceClient;
import ai.sreagent.server.demo.DemoServiceConfig;
import ai.sreagent.server.demo.DemoServiceStatus;
import ai.sreagent.server.demo.DemoServicesStatusResponse;
import ai.sreagent.server.topology.TopologyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveScenarioServiceTest {

    @Test
    void storesFailedLiveResultWhenPreflightFindsNoReachableDemoServices() {
        DemoServiceClient demoClient = mock(DemoServiceClient.class);
        when(demoClient.checkAllServices()).thenReturn(new DemoServicesStatusResponse(List.of(
                new DemoServiceStatus("order-service", "http://localhost:18081", "unreachable", "unknown", false),
                new DemoServiceStatus("payment-service", "http://localhost:18082", "unreachable", "unknown", false),
                new DemoServiceStatus("inventory-service", "http://localhost:18083", "unreachable", "unknown", false)
        ), "order-service -> payment-service -> inventory-service"));

        TopologyProvider topologyProvider = mock(TopologyProvider.class);
        when(topologyProvider.getTopology()).thenReturn(new ServiceTopology(Map.of()));

        LiveScenarioService service = new LiveScenarioService(
                demoClient, new DemoServiceConfig(), new MockEnvironment(), topologyProvider);

        LiveScenarioResult result = service.runScenarioG("live", "latency", null, 0, false);

        assertThat(result.status()).isEqualTo(LiveScenarioResult.LiveScenarioStatus.FAILED);
        assertThat(service.getResult(result.scenarioId())).containsSame(result);
    }
}
