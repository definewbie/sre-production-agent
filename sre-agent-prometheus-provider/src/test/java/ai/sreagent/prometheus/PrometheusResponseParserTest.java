package ai.sreagent.prometheus;

import ai.sreagent.prometheus.parser.PrometheusResponseParser;
import ai.sreagent.prometheus.parser.PrometheusQueryResult;
import ai.sreagent.prometheus.parser.PrometheusSample;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusResponseParserTest {

    private PrometheusResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new PrometheusResponseParser();
    }

    @Nested
    class VectorParsing {

        @Test
        void shouldParseInstantVectorResponse() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": {"service": "payment-service"},
                        "value": [1714292400.000, "1.25"]
                      }
                    ]
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("vector");
            assertThat(result.samples()).hasSize(1);

            PrometheusSample sample = result.samples().get(0);
            assertThat(sample.labels()).containsEntry("service", "payment-service");
            assertThat(sample.value()).isEqualTo(1.25);
            assertThat(sample.timestamp()).isEqualTo(Instant.ofEpochSecond(1714292400));
        }

        @Test
        void shouldParseRangeVectorResponse() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [
                      {
                        "metric": {"service": "payment-service"},
                        "values": [
                          [1714292340.000, "1.0"],
                          [1714292400.000, "1.25"],
                          [1714292460.000, "0.8"]
                        ]
                      }
                    ]
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("matrix");
            assertThat(result.samples()).hasSize(3);
            assertThat(result.samples().get(0).value()).isEqualTo(1.0);
            assertThat(result.samples().get(1).value()).isEqualTo(1.25);
            assertThat(result.samples().get(2).value()).isEqualTo(0.8);
        }

        @Test
        void shouldParseMultipleResults() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {"metric": {"service": "a"}, "value": [1714292400.0, "0.5"]},
                      {"metric": {"service": "b"}, "value": [1714292400.0, "1.5"]}
                    ]
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);
            assertThat(result.samples()).hasSize(2);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void shouldParseEmptyResult() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": []
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("vector");
            assertThat(result.isEmpty()).isTrue();
            assertThat(result.samples()).isEmpty();
        }

        @Test
        void shouldHandleErrorResponse() {
            String json = """
                {
                  "status": "error",
                  "errorType": "bad_data",
                  "error": "invalid parameter \"query\": parse error"
                }
                """;

            PrometheusQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("error");
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        void shouldHandleNaNValue() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {"metric": {"service": "test"}, "value": [1714292400.0, "NaN"]}
                    ]
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);
            assertThat(result.samples()).hasSize(1);
            assertThat(result.samples().get(0).value()).isNaN();
        }

        @Test
        void shouldHandleInfValue() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {"metric": {"service": "test"}, "value": [1714292400.0, "+Inf"]}
                    ]
                  }
                }
                """;

            PrometheusQueryResult result = parser.parse(json);
            assertThat(result.samples()).hasSize(1);
            assertThat(result.samples().get(0).value()).isNaN();
        }

        @Test
        void shouldHandleInvalidJson() {
            PrometheusQueryResult result = parser.parse("not json");
            assertThat(result.resultType()).isEqualTo("error");
            assertThat(result.isEmpty()).isTrue();
        }

        @Test
        void shouldHandleEmptyString() {
            PrometheusQueryResult result = parser.parse("");
            assertThat(result.resultType()).isEqualTo("error");
        }
    }
}
