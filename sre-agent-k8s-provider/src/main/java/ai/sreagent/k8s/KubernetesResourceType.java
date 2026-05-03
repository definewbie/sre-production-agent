package ai.sreagent.k8s;

/**
 * Known Kubernetes resource types that the provider can collect.
 */
public enum KubernetesResourceType {
    POD("pods", "Pod"),
    DEPLOYMENT("deployments", "Deployment"),
    SERVICE("services", "Service"),
    EVENT("events", "Event"),
    NODE("nodes", "Node"),
    CONFIGMAP("configmaps", "ConfigMap"),
    SECRET("secrets", "Secret"),
    INGRESS("ingresses", "Ingress");

    private final String plural;
    private final String kind;

    KubernetesResourceType(String plural, String kind) {
        this.plural = plural;
        this.kind = kind;
    }

    public String plural() { return plural; }
    public String kind() { return kind; }
}
