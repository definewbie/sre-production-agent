package ai.sreagent.trace;

import ai.sreagent.trace.query.TraceQueryTemplate;
import ai.sreagent.trace.query.TraceQueryTemplateRegistry;
import ai.sreagent.trace.query.TraceQueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TraceQueryTemplateRegistryTest {

    private TraceQueryTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TraceQueryTemplateRegistry();
    }

    @Nested
    @DisplayName("all query types have templates registered")
    class AllTypesRegistered {

        @Test
        @DisplayName("should have a template for each of the 5 TraceQueryType values")
        void shouldHaveTemplateForAllQueryTypes() {
            for (TraceQueryType type : TraceQueryType.values()) {
                Optional<TraceQueryTemplate> template = registry.getTemplate(type);
                assertThat(template)
                        .as("Template for %s should be present", type)
                        .isPresent();
                assertThat(template.get().queryType())
                        .isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("getTemplate returns correct description")
    class TemplateDescription {

        @Test
        @DisplayName("DOWNSTREAM_SLOW_SPAN description should mention latency")
        void downstreamSlowSpanDescription() {
            TraceQueryTemplate template = registry.getTemplate(TraceQueryType.DOWNSTREAM_SLOW_SPAN).orElseThrow();
            assertThat(template.description()).containsIgnoringCase("latency");
        }

        @Test
        @DisplayName("ERROR_SPAN description should mention error")
        void errorSpanDescription() {
            TraceQueryTemplate template = registry.getTemplate(TraceQueryType.ERROR_SPAN).orElseThrow();
            assertThat(template.description()).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("ROOT_SPAN_SLOW description should mention root span or duration")
        void rootSpanSlowDescription() {
            TraceQueryTemplate template = registry.getTemplate(TraceQueryType.ROOT_SPAN_SLOW).orElseThrow();
            assertThat(template.description()).containsIgnoringCase("root");
        }

        @Test
        @DisplayName("DEPENDENCY_PATH description should mention dependency or downstream")
        void dependencyPathDescription() {
            TraceQueryTemplate template = registry.getTemplate(TraceQueryType.DEPENDENCY_PATH).orElseThrow();
            assertThat(template.description()).containsIgnoringCase("dependency");
        }

        @Test
        @DisplayName("TIMEOUT_SPAN description should mention timeout")
        void timeoutSpanDescription() {
            TraceQueryTemplate template = registry.getTemplate(TraceQueryType.TIMEOUT_SPAN).orElseThrow();
            assertThat(template.description()).containsIgnoringCase("timeout");
        }
    }

    @Nested
    @DisplayName("getTemplate for unknown/null type")
    class UnknownType {

        @Test
        @DisplayName("should return empty Optional for null type")
        void shouldReturnEmptyForNull() {
            Optional<TraceQueryTemplate> template = registry.getTemplate(null);
            assertThat(template).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllTemplates returns 5 templates")
    class AllTemplates {

        @Test
        @DisplayName("should return exactly 5 templates")
        void shouldReturnFiveTemplates() {
            List<TraceQueryTemplate> templates = registry.getAllTemplates();

            assertThat(templates).hasSize(5);
        }

        @Test
        @DisplayName("getSupportedTypes should return all 5 types")
        void shouldReturnFiveSupportedTypes() {
            Set<TraceQueryType> types = registry.getSupportedTypes();

            assertThat(types).hasSize(5);
            assertThat(types).containsExactlyInAnyOrder(TraceQueryType.values());
        }
    }
}
