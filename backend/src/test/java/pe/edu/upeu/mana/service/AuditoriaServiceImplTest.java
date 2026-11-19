package pe.edu.upeu.mana.service;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.upeu.mana.entity.AuditoriaEvento;
import pe.edu.upeu.mana.enums.AccionAuditoria;
import pe.edu.upeu.mana.exception.DatosInvalidosException;
import pe.edu.upeu.mana.repository.AuditoriaEventoRepository;
import pe.edu.upeu.mana.service.impl.AuditoriaServiceImpl;
import pe.edu.upeu.mana.service.service.ContextoLocalService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceImplTest {

    @Mock AuditoriaEventoRepository eventos;
    @Mock ContextoLocalService contexto;

    final Clock clock = Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneId.of("America/Lima"));

    AuditoriaServiceImpl servicio() {
        return new AuditoriaServiceImpl(eventos, contexto, clock);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void registraActorFechaAccionEntidadCambioYRequestId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@mana.pe", null, List.of()));
        MDC.put("requestId", "req-12345678");

        servicio().registrar(AccionAuditoria.ANULAR_VENTA, "Venta", 7L, 1L, "estado: PAGADA -> ANULADA | motivo: error de cobro");

        var captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(eventos).save(captor.capture());
        var e = captor.getValue();
        assertEquals("admin@mana.pe", e.getUsuario());
        assertEquals(OffsetDateTime.now(clock), e.getFecha());
        assertEquals(AccionAuditoria.ANULAR_VENTA, e.getAccion());
        assertEquals("Venta", e.getEntidad());
        assertEquals(7L, e.getEntidadId());
        assertEquals(1L, e.getLocalId());
        assertTrue(e.getDetalle().contains("PAGADA -> ANULADA"));
        assertEquals("req-12345678", e.getRequestId());
    }

    @Test
    void sinUsuarioAutenticadoRegistraSistemaYRecortaDetalleLargo() {
        servicio().registrar(AccionAuditoria.CERRAR_CAJA, "SesionCaja", 3L, 1L, "x".repeat(1500));

        var captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(eventos).save(captor.capture());
        assertEquals("sistema", captor.getValue().getUsuario());
        assertEquals(1000, captor.getValue().getDetalle().length());
        assertNull(captor.getValue().getRequestId());
    }

    @Test
    void listarFiltraPorElLocalPermitidoYRangoDeFechas() {
        when(contexto.localId(null)).thenReturn(1L);
        when(eventos.filtrar(eq(1L), eq(AccionAuditoria.ANULAR_CONTRATO), isNull(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        var r = servicio().listar(null, AccionAuditoria.ANULAR_CONTRATO, " ", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-19"), 0, 20, "fecha", "desc");

        assertEquals(0, r.getTotalElementos());
        verify(eventos).filtrar(eq(1L), eq(AccionAuditoria.ANULAR_CONTRATO), isNull(),
                eq(OffsetDateTime.parse("2026-11-01T00:00-05:00")), eq(OffsetDateTime.parse("2026-11-20T00:00-05:00")), any());
    }

    @Test
    void rangoInvertidoSeRechaza() {
        assertThrows(DatosInvalidosException.class, () -> servicio().listar(null, null, null,
                LocalDate.parse("2026-11-19"), LocalDate.parse("2026-11-01"), 0, 20, "fecha", "desc"));
    }
}
