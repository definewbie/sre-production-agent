package ai.sreagent.k8s;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads Kubernetes resources via kubectl CLI.
 * Suitable for local development and demo with kind/minikube.
 * NOT for production — use JavaClientKubernetesResourceReader instead.
 */
public class KubectlKubernetesResourceReader implements KubernetesResourceReader {

    private final KubectlCommandRunner commandRunner;

    public KubectlKubernetesResourceReader(KubectlCommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    public KubectlKubernetesResourceReader() {
        this(new ProcessKubectlCommandRunner());
    }

    @Override
    public String readResource(String resourceType, String name, String namespace, Map<String, String> labels) throws IOException {
        List<String> args = buildArgs("get", resourceType, name, namespace, labels);
        return commandRunner.execute(args);
    }

    @Override
    public String listResources(String resourceType, String namespace, Map<String, String> labels) throws IOException {
        // List operation: no resource name
        return readResource(resourceType, "", namespace, labels);
    }

    private List<String> buildArgs(String verb, String resourceType, String name, String namespace, Map<String, String> labels) {
        List<String> args = new ArrayList<>();
        args.add(verb);
        args.add(resourceType);

        if (name != null && !name.isEmpty()) {
            args.add(name);
        }

        args.add("-o");
        args.add("json");

        if (namespace != null && !namespace.isEmpty()) {
            args.add("-n");
            args.add(namespace);
        } else {
            args.add("-A");
        }

        if (labels != null && !labels.isEmpty()) {
            String selector = labels.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
            args.add("-l");
            args.add(selector);
        }

        return args;
    }

    @Override
    public boolean isAvailable() {
        return commandRunner.isKubectlAvailable();
    }

    @Override
    public String readerName() {
        return "kubectl";
    }
}
