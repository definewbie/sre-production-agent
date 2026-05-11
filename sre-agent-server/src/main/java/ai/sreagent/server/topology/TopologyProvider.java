package ai.sreagent.server.topology;

import ai.sreagent.core.domain.ServiceTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Loads the service topology from a YAML configuration file and provides
 * a shared {@link ServiceTopology} instance for the application.
 *
 * <p>Topology source priority:
 * <ol>
 *   <li>External file (env: {@code SRE_TOPOLOGY_PATH})</li>
 *   <li>Classpath: {@code topology.yaml}</li>
 *   <li>Empty topology (degraded mode — all services treated as isolated)</li>
 * </ol>
 */
@Component
public class TopologyProvider {

    private static final Logger log = LoggerFactory.getLogger(TopologyProvider.class);

    private final ServiceTopology topology;

    public TopologyProvider(Environment env) {
        this.topology = loadTopology(env);
        log.info("TopologyProvider initialized: {} services", topology.size());
    }

    public ServiceTopology getTopology() {
        return topology;
    }

    private ServiceTopology loadTopology(Environment env) {
        String externalPath = env.getProperty("sre.topology.path");
        try {
            Map<String, List<String>> serviceDeps;
            if (externalPath != null && !externalPath.isBlank()) {
                serviceDeps = loadFromFile(externalPath);
            } else {
                serviceDeps = loadFromClasspath();
            }
            return new ServiceTopology(serviceDeps);
        } catch (Exception e) {
            log.warn("Failed to load topology, using empty topology: {}", e.getMessage());
            return new ServiceTopology(Map.of());
        }
    }

    private Map<String, List<String>> loadFromClasspath() throws Exception {
        ClassPathResource resource = new ClassPathResource("topology.yaml");
        if (!resource.exists()) {
            log.warn("topology.yaml not found on classpath");
            return Map.of();
        }
        try (InputStream is = resource.getInputStream()) {
            return parseYaml(is);
        }
    }

    private Map<String, List<String>> loadFromFile(String path) throws Exception {
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            log.warn("Topology file not found: {}", path);
            return Map.of();
        }
        try (InputStream is = new java.io.FileInputStream(file)) {
            return parseYaml(is);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseYaml(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);
        Map<String, Object> services = (Map<String, Object>) root.get("services");
        if (services == null) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : services.entrySet()) {
            Map<String, Object> svcDef = (Map<String, Object>) entry.getValue();
            List<String> deps = (List<String>) svcDef.getOrDefault("dependsOn", List.of());
            result.put(entry.getKey(), deps);
        }
        return result;
    }
}
