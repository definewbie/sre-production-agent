# Step A Implementation Notes

## What was built

- Maven multi-module project: `sre-agent-core`, `sre-agent-server`, `sre-agent-cli`
- 10 domain records in `sre-agent-core`
- Jackson-based `EvidenceLoader` with snake_case → camelCase mapping
- `StaticEvidenceProvider` for classpath-based demo data
- `PatternRegistry` + `BuiltinPatterns` (3 patterns)
- Scenario E alert JSON (8 fields) and evidence JSON (8 evidence items)
- Spring Boot skeleton with `GET /health`
- CLI skeleton with Picocli `investigate` command
- 3 test classes covering loading, patterns, and health endpoint

## Design decisions

1. **Java records** for all domain objects — immutable, concise, pattern-match friendly
2. **Zero Spring in core** — `sre-agent-core` pom.xml has no Spring dependency
3. **snake_case JSON** externally, **camelCase** internally — Jackson `@JsonProperty` handles mapping
4. **LinkedHashMap** in PatternRegistry — preserves insertion order for deterministic iteration
5. **Placeholder interfaces** for Step B/C/D — throw `UnsupportedOperationException` until implemented

## Known limitations

- HypothesisEngine, VerificationEngine, ConfidenceScorer, HypothesisComparator, MarkdownReporter are stubs
- No real K8s/Prometheus/Loki integration
- No LLM integration
- CLI `investigate` command prints parameters but doesn't run the workflow yet
