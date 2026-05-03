package ai.sreagent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "sre-agent", mixinStandardHelpOptions = true,
        description = "SRE Production Agent - Alert-driven RCA for Kubernetes microservices",
        subcommands = {InvestigateCommand.class, CollectK8sEvidenceCommand.class})
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'sre-agent investigate --alert <path> --evidence <path> --output <path>'");
        System.out.println("     'sre-agent collect-k8s-evidence --service <svc> --namespace <ns> --output <path>'");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
