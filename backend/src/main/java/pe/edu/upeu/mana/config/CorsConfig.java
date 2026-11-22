package pe.edu.upeu.mana.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.*;

@Configuration
public class CorsConfig {

    // Origen de la app de escritorio (Tauri): Windows usa http://tauri.localhost; macOS y Linux, tauri://localhost.
    static final List<String> ESCRITORIO = List.of("http://tauri.localhost", "tauri://localhost");

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        List<String> permitidos = new ArrayList<>(origins);
        ESCRITORIO.stream().filter(o -> !permitidos.contains(o)).forEach(permitidos::add);
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(permitidos);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
