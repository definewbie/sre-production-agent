package ai.sreagent.loki;

import ai.sreagent.loki.query.LokiQueryTemplate;
import ai.sreagent.loki.query.LokiQueryTemplateRegistry;
import ai.sreagent.loki.query.LokiQueryType;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LokiQueryTemplateRegistryTest {

    private LokiQueryTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LokiQueryTemplateRegistry();
    }

    @Nested
    class TemplateRetrieval {

        @Test
        void shouldReturnNonEmptyTemplateForEachType() {
            for (LokiQueryType type : LokiQueryType.values()) {
                Optional<LokiQueryTemplate> template = registry.getTemplate(type);
                assertThat(template)
                        .as("Template for %s should be present", type)
                        .isPresent();
                assertThat(template.get().template())
                        .as("Template for %s should not be blank", type)
                        .isNotBlank();
            }
        }

        @Test
        void shouldReturnEmptyOptionalForNullType() {
            Optional<LokiQueryTemplate> template = registry.getTemplate(null);
            assertThat(template).isEmpty();
        }
    }

    @Nested
    class AvailableTypes {

        @Test
        void shouldHaveEightAvailableTypes() {
            Set<LokiQueryType> types = registry.availableTypes();
            assertThat(types).hasSize(8);
            assertThat(types).containsExactlyInAnyOrder(LokiQueryType.values());
        }
    }

    @Nested
    class QueryBuilding {

        @Test
        void shouldBuildTimeoutErrorQueryWithServiceAndNamespace() {
            LokiQueryTemplate template = registry.getTemplate(LokiQueryType.TIMEOUT_ERROR).orElseThrow();
            String query = template.buildQuery("order-service", "demo");

            assertThat(query).contains("service=\"order-service\"");
            assertThat(query).contains("namespace=\"demo\"");
            assertThat(query).containsIgnoringCase("timeout");
        }

        @Test
        void shouldBuildExceptionLogsQueryContainingExceptionPattern() {
            LokiQueryTemplate template = registry.getTemplate(LokiQueryType.EXCEPTION_LOGS).orElseThrow();
            String query = template.buildQuery("order-service", "demo");

            assertThat(query).contains("Exception|ERROR");
        }
    }

    @Nested
    class CustomRegistration {

        @Test
        void shouldRegisterAndRetrieveCustomTemplate() {
            registry.register(LokiQueryType.TIMEOUT_ERROR,
                    "{app=\"$service\", env=\"$namespace\"} |= \"custom_timeout\"");

            Optional<LokiQueryTemplate> template = registry.getTemplate(LokiQueryType.TIMEOUT_ERROR);
            assertThat(template).isPresent();
            assertThat(template.get().template())
                    .isEqualTo("{app=\"$service\", env=\"$namespace\"} |= \"custom_timeout\"");

            String query = template.get().buildQuery("api-gateway", "staging");
            assertThat(query).contains("app=\"api-gateway\"");
            assertThat(query).contains("env=\"staging\"");
            assertThat(query).contains("custom_timeout");
        }
    }
}
