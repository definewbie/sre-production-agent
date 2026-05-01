package ai.sreagent.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "sre-agent", mixinStandardHelpOptions = true,
        description = "SRE Production Agent - Alert-driven RCA for Kubernetes microservices",
        subcommands = {InvestigateCommand.class})
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'sre-agent investigate --alert <path> --evidence <path> --output <path>'");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
