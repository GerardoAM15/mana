package pe.edu.upeu.mana;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@EnabledIfEnvironmentVariable(named = "MANA_ORACLE_TEST", matches = "true")
class EntradaHttpOracleIntegrationTest {

    @LocalServerPort
    private int puerto;

    @Autowired
    private ObjectMapper mapper;

    @Value("${app.admin.email}")
    private String correo;

    @Value("${app.admin.password}")
    private String clave;

    private final HttpClient http = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void iniciarSesion() throws Exception {
        var respuesta = enviar("POST", "/auth/login",
                mapper.writeValueAsString(Map.of("correo", correo, "clave", clave)),
                Map.of("Content-Type", "application/json"));
        assertEquals(200, respuesta.statusCode());
        token = mapper.readTree(respuesta.body()).get("token").asText();
    }

    @Test
    void cantidadDecimalNoLlegaAlRegistroDeVenta() throws Exception {
        var respuesta = enviar("POST", "/ventas", """
                {"servicio":"A","paraLlevar":false,
                 "lineas":[{"tipo":"BEBIDA","bebidaId":999999999,"cantidad":1.9}],"pagos":[]}
                """, Map.of("Content-Type", "application/json"));

        assertEquals(400, respuesta.statusCode());
    }

    @Test
    void identificadorDecimalNoLlegaALaSimulacion() throws Exception {
        var respuesta = enviar("POST", "/contratos/simular", """
                {"clienteId":999999999.9,"planId":1,"fechaInicio":"2026-11-19","servicios":["A"]}
                """, Map.of("Content-Type", "application/json"));

        assertEquals(400, respuesta.statusCode());
    }

    @Test
    void contenidoNoSoportadoConservaElError415() throws Exception {
        var respuesta = enviar("POST", "/clientes", "{}", Map.of("Content-Type", "text/plain"));

        assertEquals(415, respuesta.statusCode());
        assertEquals(415, mapper.readTree(respuesta.body()).get("status").asInt());
        assertEquals("/api/v1/clientes", mapper.readTree(respuesta.body()).get("path").asText());
    }

    @Test
    void aceptarXmlNoSeConvierteEnErrorDeAutenticacion() throws Exception {
        assertEquals(200, enviar("GET", "/clientes", null,
                Map.of("Accept", "application/json")).statusCode());

        var respuesta = enviar("GET", "/clientes", null, Map.of("Accept", "application/xml"));
        assertEquals(406, respuesta.statusCode());
        assertEquals(406, mapper.readTree(respuesta.body()).get("status").asInt());
        assertEquals("/api/v1/clientes", mapper.readTree(respuesta.body()).get("path").asText());

        assertEquals(200, enviar("GET", "/clientes", null,
                Map.of("Accept", "application/json")).statusCode());
    }

    @Test
    void fechasImposiblesSeRechazanInclusoConIdiomaComodin() throws Exception {
        for (String idioma : new String[]{"*", "es-PE", "en-US"}) {
            var respuesta = enviar("GET", "/ventas?desde=2026-02-30", null,
                    Map.of("Accept-Language", idioma));
            assertEquals(400, respuesta.statusCode(), idioma);

            assertEquals(200, enviar("GET", "/ventas?desde=2026-11-18", null,
                    Map.of("Accept-Language", idioma)).statusCode(), idioma);
        }
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo,
            Map<String, String> encabezados) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1" + ruta));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        encabezados.forEach(request::header);
        request.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
