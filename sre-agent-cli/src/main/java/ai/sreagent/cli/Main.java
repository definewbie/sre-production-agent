package ai.sreagent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "sre-agent", mixinStandardHelpOptions = true,
        description = "SRE Production Agent - Alert-driven RCA for Kubernetes microservices",
        subcommands = {InvestigateCommand.class, CollectK8sEvidenceCommand.class,
                CollectPrometheusEvidenceCommand.class,
                CollectLokiEvidenceCommand.class,
                CollectAlertmanagerAlertsCommand.class,
                CollectTraceEvidenceCommand.class,
                NormalizeEvidenceCommand.class,
                ProposeHypothesesCommand.class,
                ProposeAndExecuteProbesCommand.class})
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'sre-agent investigate --alert <path> --evidence <path> --output <path>'");
        System.out.println("     'sre-agent collect-k8s-evidence --service <svc> --namespace <ns> --output <path>'");
        System.out.println("     'sre-agent collect-prometheus-evidence --service <svc> --query-type LATENCY_P95 --output <path>'");
        System.out.println("     'sre-agent collect-loki-evidence --service <svc> --query-type TIMEOUT_ERROR --output <path>'");
        System.out.println("     'sre-agent collect-alertmanager-alerts --service <svc> --output <path>'");
        System.out.println("     'sre-agent collect-trace-evidence --service <svc> --query-type DOWNSTREAM_SLOW_SPAN --output <path>'");
        System.out.println("     'sre-agent normalize-evidence --input <path> --output <path>'");
        System.out.println("     'sre-agent propose-hypotheses --alert <path> --evidence <path> --output <path>'");
        System.out.println("     'sre-agent propose-and-execute-probes --alert <path> --evidence <path> --output <path> --mode fixture'");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
