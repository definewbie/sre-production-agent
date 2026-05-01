package ai.sreagent.core.evidence;

import ai.sreagent.core.domain.Evidence;
import ai.sreagent.core.domain.IncidentTask;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads alert and evidence data from JSON files using Jackson.
 * Handles snake_case JSON → camelCase Java record mapping.
 */
public class EvidenceLoader {

    private final ObjectMapper mapper;

    public EvidenceLoader() {
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public IncidentTask loadAlert(File file) throws IOException {
        return mapper.readValue(file, IncidentTask.class);
    }

    public IncidentTask loadAlert(InputStream is) throws IOException {
        return mapper.readValue(is, IncidentTask.class);
    }

    public List<Evidence> loadEvidence(File file) throws IOException {
        return mapper.readValue(file,
                mapper.getTypeFactory().constructCollectionType(List.class, Evidence.class));
    }

    public List<Evidence> loadEvidence(InputStream is) throws IOException {
        return mapper.readValue(is,
                mapper.getTypeFactory().constructCollectionType(List.class, Evidence.class));
    }
}
