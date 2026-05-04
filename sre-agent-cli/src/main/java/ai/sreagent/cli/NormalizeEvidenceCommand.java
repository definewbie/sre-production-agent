package ai.sreagent.cli;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.evidence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(
    name = "normalize-evidence",
    description = "Normalize evidence JSON into taxonomy-enriched representation"
)
public class NormalizeEvidenceCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--input", required = true, description = "Input evidence JSON file")
    private String inputPath;

    @CommandLine.Option(names = "--output", description = "Output normalized JSON file")
    private String outputPath;

    @Override
    public Integer call() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + inputPath);
            return 1;
        }

        // Read evidence — try as array first, then as object with "evidence" field
        List<Evidence> evidenceList;
        try {
            Evidence[] arr = mapper.readValue(inputFile, Evidence[].class);
            evidenceList = Arrays.asList(arr);
        } catch (Exception e1) {
            try {
                var tree = mapper.readTree(inputFile);
                if (tree.has("evidence")) {
                    evidenceList = Arrays.asList(mapper.treeToValue(tree.get("evidence"), Evidence[].class));
                } else {
                    System.err.println("Could not parse evidence from input file");
                    return 1;
                }
            } catch (Exception e2) {
                System.err.println("Failed to parse input: " + e2.getMessage());
                return 1;
            }
        }

        List<NormalizedEvidence> normalized = EvidenceNormalizer.normalizeAll(evidenceList);

        // Print summary
        System.out.println("Normalized evidence written");
        System.out.println("input: " + evidenceList.size() + " evidence items");

        var categories = normalized.stream()
            .map(n -> n.category().name())
            .distinct()
            .sorted()
            .toList();
        System.out.println("categories:");
        categories.forEach(c -> System.out.println("  - " + c));

        var signals = normalized.stream()
            .map(n -> n.signal().name())
            .distinct()
            .sorted()
            .toList();
        System.out.println("signals:");
        signals.forEach(s -> System.out.println("  - " + s));

        var roles = normalized.stream()
            .map(n -> n.causalRole().name())
            .distinct()
            .sorted()
            .toList();
        System.out.println("causal roles:");
        roles.forEach(r -> System.out.println("  - " + r));

        // Write output if specified
        if (outputPath != null) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("totalCount", normalized.size());
            output.put("categories", categories);
            output.put("signals", signals);
            output.put("normalizedEvidence", normalized);
            mapper.writeValue(new File(outputPath), output);
            System.out.println("output: " + outputPath);
        }

        return 0;
    }
}
