package pe.edu.upeu.mana.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.edu.upeu.mana.controller.ClienteController;
import pe.edu.upeu.mana.controller.ContratoController;
import pe.edu.upeu.mana.controller.ReporteController;
import pe.edu.upeu.mana.controller.VentaController;
import pe.edu.upeu.mana.dto.PaginaResponseDTO;
import pe.edu.upeu.mana.dto.venta.VentaRequestDTO;
import pe.edu.upeu.mana.exception.GlobalExceptionHandler;
import pe.edu.upeu.mana.service.service.ClienteService;
import pe.edu.upeu.mana.service.service.ContratoService;
import pe.edu.upeu.mana.service.service.ReporteService;
import pe.edu.upeu.mana.service.service.VentaService;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EntradaHttpTest {

    private MockMvc mvc;
    private VentaService ventas;
    private ContratoService contratos;
    private ClienteService clientes;
    private ReporteService reportes;

    @BeforeEach
    void preparar() {
        ventas = mock(VentaService.class);
        contratos = mock(ContratoService.class);
        clientes = mock(ClienteService.class);
        reportes = mock(ReporteService.class);
        when(clientes.listar(null, 0, 20, "id", "desc"))
                .thenReturn(new PaginaResponseDTO<>(List.of(), 0, 20, 0L, 0, true));

        var builder = JsonMapper.builder().findAndAddModules();
        new JsonConfig().jsonMapperCustomizer().customize(builder);
        var conversion = new DefaultFormattingConversionService();
        new FechaConfig().addFormatters(conversion);
        var clock = Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneId.of("America/Lima"));

        mvc = MockMvcBuilders.standaloneSetup(new VentaController(ventas),
                        new ContratoController(contratos), new ClienteController(clientes),
                        new ReporteController(reportes))
                .setConversionService(conversion)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(builder.build()))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.9", "2.0", "1e1"})
    void rechazaCantidadDecimalSinRegistrarVenta(String cantidad) throws Exception {
        mvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"servicio":"A","paraLlevar":false,
                                 "lineas":[{"tipo":"BEBIDA","bebidaId":1,"cantidad":%s}],"pagos":[]}
                                """.formatted(cantidad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El campo 'lineas[0].cantidad' debe ser un número entero, sin decimales y dentro del rango permitido"));

        verifyNoInteractions(ventas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.9", "4.0", "1e1"})
    void rechazaIdentificadorDecimalAntesDeSimular(String clienteId) throws Exception {
        mvc.perform(post("/api/v1/contratos/simular").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":%s,"planId":1,"fechaInicio":"2026-11-19","servicios":["A"]}
                                """.formatted(clienteId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'clienteId' debe ser un número entero, sin decimales y dentro del rango permitido"));

        verifyNoInteractions(contratos);
    }

    @Test
    void conservaIdentificadoresYCantidadesEnteras() throws Exception {
        mvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"servicio":"A","paraLlevar":false,
                                 "lineas":[{"tipo":"BEBIDA","bebidaId":7,"cantidad":2}],"pagos":[]}
                                """))
                .andExpect(status().isCreated());

        var argumento = ArgumentCaptor.forClass(VentaRequestDTO.class);
        verify(ventas).crear(argumento.capture());
        assertEquals(7L, argumento.getValue().getLineas().getFirst().getBebidaId());
        assertEquals(2L, argumento.getValue().getLineas().getFirst().getCantidad());
    }

    @Test
    void tipoDeContenidoNoSoportadoResponde415() throws Exception {
        mvc.perform(post("/api/v1/clientes").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.path").value("/api/v1/clientes"));

        verifyNoInteractions(clientes);
    }

    @Test
    void aceptarXmlResponde406ConErrorJson() throws Exception {
        mvc.perform(get("/api/v1/clientes").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.path").value("/api/v1/clientes"));

        mvc.perform(get("/api/v1/clientes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-02-30, *", "2026-02-30, es-PE", "2026-02-30, en-US",
            "2026-11-31, *", "2026-11-31, es-PE", "2026-11-31, en-US"
    })
    void rechazaFechaInexistenteEnCualquierIdioma(String fecha, String idioma) throws Exception {
        mvc.perform(get("/api/v1/ventas").param("desde", fecha).header("Accept-Language", idioma))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'desde' debe ser una fecha válida con formato yyyy-MM-dd"));

        verifyNoInteractions(ventas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "es-PE", "en-US"})
    void conservaFechaIsoValidaIncluidoAnioBisiesto(String idioma) throws Exception {
        mvc.perform(get("/api/v1/reportes/ingresos-mensuales")
                        .param("desde", "2024-02-29").param("hasta", "2024-03-01")
                        .header("Accept-Language", idioma))
                .andExpect(status().isOk());

        verify(reportes).ingresos(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 1), null);
    }

    @Test
    void fechaInvalidaEnJsonIdentificaElCampo() throws Exception {
        mvc.perform(post("/api/v1/contratos/simular").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":1,"planId":1,"fechaInicio":"2026-02-30","servicios":["A"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'fechaInicio' debe ser una fecha válida con formato yyyy-MM-dd"));

        verifyNoInteractions(contratos);
    }

    @Test
    void jsonMalFormadoNoSePresentaComoErrorDeFecha() throws Exception {
        mvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicio\":\"A\",}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cuerpo JSON inválido: revise la sintaxis y envíe un objeto JSON válido"));

        verifyNoInteractions(ventas);
    }

    @Test
    void tipoDeLineaInvalidoNoSePresentaComoErrorDeFecha() throws Exception {
        mvc.perform(post("/api/v1/ventas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"servicio":"A","paraLlevar":false,
                                 "lineas":[{"tipo":"DESCONOCIDO","bebidaId":1,"cantidad":1}],"pagos":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El campo 'lineas[0].tipo' tiene un tipo o valor inválido"));

        verifyNoInteractions(ventas);
    }

    @Test
    void identificadorDecimalEnRutaIndicaQueSeEsperaUnEntero() throws Exception {
        mvc.perform(get("/api/v1/contratos/1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'id' debe ser un número entero, sin decimales y dentro del rango permitido"));

        verifyNoInteractions(contratos);
    }

}
