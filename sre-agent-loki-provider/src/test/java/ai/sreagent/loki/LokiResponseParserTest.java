package ai.sreagent.loki;

import ai.sreagent.loki.parser.LokiLogEntry;
import ai.sreagent.loki.parser.LokiQueryResult;
import ai.sreagent.loki.parser.LokiResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LokiResponseParserTest {

    private LokiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new LokiResponseParser();
    }

    @Nested
    @DisplayName("parse valid streams response")
    class ValidStreamsResponse {

        @Test
        @DisplayName("should parse resultType, entry count, labels, timestamp, and message")
        void shouldParseValidStreamsResponse() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "streams",
                    "result": [
                      {
                        "stream": {
                          "service": "order-service",
                          "namespace": "demo"
                        },
                        "values": [
                          ["1714292400000000000", "ERROR timeout after 500ms"]
                        ]
                      }
                    ]
                  }
                }
                """;

            LokiQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("streams");
            assertThat(result.entryCount()).isEqualTo(1);
            assertThat(result.isEmpty()).isFalse();

            LokiLogEntry entry = result.entries().getFirst();
            assertThat(entry.labels())
                    .containsEntry("service", "order-service")
                    .containsEntry("namespace", "demo");
            assertThat(entry.timestamp()).isEqualTo(Instant.parse("2024-04-28T08:20:00Z"));
            assertThat(entry.message()).contains("timeout");
        }
    }

    @Nested
    @DisplayName("parse empty result")
    class EmptyResult {

        @Test
        @DisplayName("should return empty result for empty result array")
        void shouldReturnEmptyForEmptyResultArray() {
            String json = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "streams",
                    "result": []
                  }
                }
                """;

            LokiQueryResult result = parser.parse(json);

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.entryCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("parse error response")
    class ErrorResponse {

        @Test
        @DisplayName("should return resultType 'error' for error response")
        void shouldReturnErrorResultType() {
            String json = """
                {
                  "status": "error",
                  "errorType": "exec",
                  "error": "query error"
                }
                """;

            LokiQueryResult result = parser.parse(json);

            assertThat(result.resultType()).isEqualTo("error");
            assertThat(result.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("parse null input")
    class NullInput {

        @Test
        @DisplayName("should return empty result for null input")
        void shouldReturnEmptyForNull() {
            LokiQueryResult result = parser.parse(null);

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.entryCount()).isEqualTo(0);
            assertThat(result.resultType()).isEqualTo("empty");
        }
    }

    @Nested
    @DisplayName("parse blank string")
    class BlankString {

        @Test
        @DisplayName("should return empty result for blank string")
        void shouldReturnEmptyForBlankString() {
            LokiQueryResult result = parser.parse("   ");

            assertThat(result.isEmpty()).isTrue();
            assertThat(result.entryCount()).isEqualTo(0);
            assertThat(result.resultType()).isEqualTo("empty");
        }
    }
}
