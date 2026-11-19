package pe.edu.upeu.mana.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer jsonMapperCustomizer() {
        // Los identificadores y cantidades enteras no deben perder su fracción.
        return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
