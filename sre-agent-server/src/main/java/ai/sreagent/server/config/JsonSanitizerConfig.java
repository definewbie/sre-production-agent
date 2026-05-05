package ai.sreagent.server.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Jackson configuration that sanitizes all string values during serialization.
 *
 * Strips non-printable control characters (0x00-0x1F except TAB/LF/CR)
 * to ensure JSON responses are safe for jq, Python json.loads, and browser JSON.parse().
 *
 * This prevents Loki log content and other external data from breaking JSON parsing.
 */
@Configuration
public class JsonSanitizerConfig {

    private static final Pattern CONTROL_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0E-\\x1F]");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer stringSanitizerCustomizer() {
        return builder -> builder.serializerByType(String.class, new SanitizingStringSerializer());
    }

    /**
     * Also register as a SimpleModule to handle all String serialization,
     * including inside Maps, Lists, and Records.
     */
    @Bean
    public SimpleModule stringSanitizerModule() {
        SimpleModule module = new SimpleModule("StringControlCharSanitizer");
        module.addSerializer(String.class, new SanitizingStringSerializer());
        return module;
    }

    static class SanitizingStringSerializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            String sanitized = CONTROL_CHARS.matcher(value).replaceAll("");
            gen.writeString(sanitized);
        }
    }
}
