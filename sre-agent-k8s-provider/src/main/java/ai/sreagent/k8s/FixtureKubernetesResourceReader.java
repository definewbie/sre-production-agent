package ai.sreagent.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Reads Kubernetes resources from bundled test fixture JSON files.
 * Use for testing and demo scenarios — no real cluster needed.
 */
public class FixtureKubernetesResourceReader implements KubernetesResourceReader {

    private static final String FIXTURE_BASE = "/fixtures/";
    private final ObjectMapper mapper;

    public FixtureKubernetesResourceReader() {
        this.mapper = new ObjectMapper();
    }

    @Override
    public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
        String fixtureFile = resolveFixture(resourceType, name);
        try (InputStream is = getClass().getResourceAsStream(FIXTURE_BASE + fixtureFile)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + fixtureFile);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String resolveFixture(String resourceType, String name) {
        // Map resource queries to fixture files
        if ("pods".equals(resourceType) && name != null && !name.isEmpty()) {
            if (name.contains("crashloop") || name.contains("payment")) {
                return "pod-crashloopbackoff.json";
            }
            if (name.contains("oom") || name.contains("order")) {
                return "pod-oomkilled.json";
            }
        }
        if ("pods".equals(resourceType)) {
            return "pod-crashloopbackoff.json";
        }
        if ("deployments".equals(resourceType)) {
            return "deployment-sample.json";
        }
        if ("events".equals(resourceType)) {
            return "events-sample.json";
        }
        if ("services".equals(resourceType)) {
            return "service-sample.json";
        }
        return "pod-crashloopbackoff.json"; // default fallback
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String readerName() {
        return "fixture";
    }
}
