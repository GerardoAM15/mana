package pe.edu.upeu.mana.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    private CorsConfiguration configuracion(List<String> origenes) {
        var source = (UrlBasedCorsConfigurationSource) new CorsConfig().corsConfigurationSource(origenes);
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/ventas"));
    }

    @Test
    void permiteLaWebConfiguradaYLaAppDeEscritorio() {
        CorsConfiguration cors = configuracion(List.of("http://localhost:4200"));

        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:4200", "http://tauri.localhost", "tauri://localhost");
        assertThat(cors.checkOrigin("http://tauri.localhost")).isEqualTo("http://tauri.localhost");
        assertThat(cors.checkOrigin("http://otro-sitio.com")).isNull();
    }

    @Test
    void noRepiteElOrigenDeEscritorioSiYaVieneConfigurado() {
        CorsConfiguration cors = configuracion(List.of("http://tauri.localhost"));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://tauri.localhost", "tauri://localhost");
    }
}
