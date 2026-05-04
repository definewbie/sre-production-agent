package ai.sreagent.trace;

import ai.sreagent.trace.parser.ParsedSpan;
import ai.sreagent.trace.parser.ParsedTrace;
import ai.sreagent.trace.parser.TraceResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceResponseParserTest {

    private TraceResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new TraceResponseParser();
    }

    @Nested
    @DisplayName("parse single trace with multiple spans")
    class SingleTraceMultipleSpans {

        @Test
        @DisplayName("should parse trace with two spans and correct trace ID")
        void shouldParseTraceWithMultipleSpans() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-abc",
                      "spans": [
                        {
                          "spanID": "span-1",
                          "operationName": "GET /api",
                          "startTime": 1714292400000000,
                          "duration": 500000,
                          "processID": "p1",
                          "references": [],
                          "tags": []
                        },
                        {
                          "spanID": "span-2",
                          "operationName": "SELECT *",
                          "startTime": 1714292400100000,
                          "duration": 300000,
                          "processID": "p1",
                          "references": [
                            {"refType": "CHILD_OF", "spanID": "span-1"}
                          ],
                          "tags": []
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "order-service"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);

            assertThat(traces).hasSize(1);
            ParsedTrace trace = traces.getFirst();
            assertThat(trace.traceId()).isEqualTo("trace-abc");
            assertThat(trace.spanCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("parse parent/child relationship from references")
    class ParentChildRelationship {

        @Test
        @DisplayName("should resolve parentSpanId from CHILD_OF reference")
        void shouldResolveParentFromReferences() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-rel",
                      "spans": [
                        {
                          "spanID": "span-parent",
                          "operationName": "POST /order",
                          "startTime": 1714292400000000,
                          "duration": 1000000,
                          "processID": "p1",
                          "references": [],
                          "tags": []
                        },
                        {
                          "spanID": "span-child",
                          "operationName": "POST /charge",
                          "startTime": 1714292400050000,
                          "duration": 800000,
                          "processID": "p2",
                          "references": [
                            {"refType": "CHILD_OF", "spanID": "span-parent"}
                          ],
                          "tags": []
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "order-service"},
                        "p2": {"serviceName": "payment-service"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedTrace trace = traces.getFirst();

            ParsedSpan rootSpan = trace.rootSpan();
            assertThat(rootSpan).isNotNull();
            assertThat(rootSpan.spanId()).isEqualTo("span-parent");
            assertThat(rootSpan.isRoot()).isTrue();

            List<ParsedSpan> children = trace.childSpansOf("span-parent");
            assertThat(children).hasSize(1);
            assertThat(children.getFirst().parentSpanId()).isEqualTo("span-parent");
            assertThat(children.getFirst().isRoot()).isFalse();
        }
    }

    @Nested
    @DisplayName("parse service extraction from processes map")
    class ServiceExtraction {

        @Test
        @DisplayName("should resolve service name from processes map via processID")
        void shouldResolveServiceFromProcesses() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-svc",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "GET /health",
                          "startTime": 1714292400000000,
                          "duration": 100000,
                          "processID": "p1",
                          "references": [],
                          "tags": []
                        },
                        {
                          "spanID": "s2",
                          "operationName": "GET /ready",
                          "startTime": 1714292400100000,
                          "duration": 50000,
                          "processID": "p2",
                          "references": [],
                          "tags": []
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "frontend"},
                        "p2": {"serviceName": "backend"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedTrace trace = traces.getFirst();

            assertThat(trace.spans()).hasSize(2);
            assertThat(trace.spans().get(0).service()).isEqualTo("frontend");
            assertThat(trace.spans().get(1).service()).isEqualTo("backend");
        }
    }

    @Nested
    @DisplayName("parse duration conversion (microseconds to milliseconds)")
    class DurationConversion {

        @Test
        @DisplayName("should convert Jaeger microseconds to milliseconds")
        void shouldConvertMicrosToMillis() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-dur",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "GET /api",
                          "startTime": 1714292400000000,
                          "duration": 2500000,
                          "processID": "p1",
                          "references": [],
                          "tags": []
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "svc"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedSpan span = traces.getFirst().spans().getFirst();

            // 2,500,000 microseconds = 2,500 milliseconds
            assertThat(span.durationMs()).isEqualTo(2500L);
        }
    }

    @Nested
    @DisplayName("parse empty data array")
    class EmptyDataArray {

        @Test
        @DisplayName("should return empty list for empty data array")
        void shouldReturnEmptyForEmptyData() {
            String json = """
                {"data": []}
                """;

            List<ParsedTrace> traces = parser.parse(json);
            assertThat(traces).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse null/blank input")
    class NullBlankInput {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyForNull() {
            List<ParsedTrace> traces = parser.parse(null);
            assertThat(traces).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank input")
        void shouldReturnEmptyForBlank() {
            List<ParsedTrace> traces = parser.parse("   ");
            assertThat(traces).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty string")
        void shouldReturnEmptyForEmptyString() {
            List<ParsedTrace> traces = parser.parse("");
            assertThat(traces).isEmpty();
        }
    }

    @Nested
    @DisplayName("parse tags to attributes")
    class TagsToAttributes {

        @Test
        @DisplayName("should convert Jaeger tags to span attributes map")
        void shouldConvertTagsToAttributes() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-tags",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "GET /api",
                          "startTime": 1714292400000000,
                          "duration": 100000,
                          "processID": "p1",
                          "references": [],
                          "tags": [
                            {"key": "http.status_code", "value": "200"},
                            {"key": "http.method", "value": "GET"}
                          ]
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "svc"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedSpan span = traces.getFirst().spans().getFirst();

            assertThat(span.attributes())
                    .containsEntry("http.status_code", "200")
                    .containsEntry("http.method", "GET");
        }
    }

    @Nested
    @DisplayName("parse error status from error=true tag")
    class ErrorStatusFromTag {

        @Test
        @DisplayName("should set status to 'error' when error=true tag is present")
        void shouldSetErrorStatusFromTag() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-err",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "POST /order",
                          "startTime": 1714292400000000,
                          "duration": 100000,
                          "processID": "p1",
                          "references": [],
                          "tags": [
                            {"key": "error", "value": "true"},
                            {"key": "error.message", "value": "connection refused"}
                          ]
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "svc"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedSpan span = traces.getFirst().spans().getFirst();

            assertThat(span.status()).isEqualTo("error");
            assertThat(span.hasError()).isTrue();
        }

        @Test
        @DisplayName("should set status to 'ok' when no error tag is present")
        void shouldSetOkStatusWithoutErrorTag() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-ok",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "GET /health",
                          "startTime": 1714292400000000,
                          "duration": 50000,
                          "processID": "p1",
                          "references": [],
                          "tags": [
                            {"key": "http.status_code", "value": "200"}
                          ]
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "svc"}
                      }
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedSpan span = traces.getFirst().spans().getFirst();

            assertThat(span.status()).isEqualTo("ok");
            assertThat(span.hasError()).isFalse();
        }
    }

    @Nested
    @DisplayName("parse missing processes gracefully")
    class MissingProcesses {

        @Test
        @DisplayName("should use 'unknown' service when processes map is missing")
        void shouldUseUnknownWhenProcessesMissing() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-noproc",
                      "spans": [
                        {
                          "spanID": "s1",
                          "operationName": "GET /api",
                          "startTime": 1714292400000000,
                          "duration": 100000,
                          "processID": "p1",
                          "references": [],
                          "tags": []
                        }
                      ]
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            ParsedSpan span = traces.getFirst().spans().getFirst();

            assertThat(span.service()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("should parse trace with no spans")
        void shouldParseTraceWithNoSpans() {
            String json = """
                {
                  "data": [
                    {
                      "traceID": "trace-empty-spans",
                      "spans": [],
                      "processes": {}
                    }
                  ]
                }
                """;

            List<ParsedTrace> traces = parser.parse(json);
            assertThat(traces).hasSize(1);
            assertThat(traces.getFirst().isEmpty()).isTrue();
            assertThat(traces.getFirst().spanCount()).isEqualTo(0);
        }
    }
}
