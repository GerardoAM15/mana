package pe.edu.upeu.mana.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.mana.dto.PaginaResponseDTO;
import pe.edu.upeu.mana.dto.auditoria.AuditoriaEventoResponseDTO;
import pe.edu.upeu.mana.entity.AuditoriaEvento;
import pe.edu.upeu.mana.enums.AccionAuditoria;
import pe.edu.upeu.mana.exception.DatosInvalidosException;
import pe.edu.upeu.mana.repository.AuditoriaEventoRepository;
import pe.edu.upeu.mana.service.service.AuditoriaService;
import pe.edu.upeu.mana.service.service.ContextoLocalService;
import pe.edu.upeu.mana.util.PaginacionUtil;

@Service
public class AuditoriaServiceImpl implements AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaServiceImpl.class);
    private static final int MAX_DETALLE = 1000;
    private static final Set<String> ORDENABLES = Set.of("id", "fecha", "usuario", "accion");

    private final AuditoriaEventoRepository eventos;
    private final ContextoLocalService contexto;
    private final Clock clock;

    public AuditoriaServiceImpl(AuditoriaEventoRepository eventos, ContextoLocalService contexto, Clock clock) {
        this.eventos = eventos;
        this.contexto = contexto;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AccionAuditoria accion, String entidad, Long entidadId, Long localId, String detalle) {
        var e = new AuditoriaEvento();
        e.setFecha(OffsetDateTime.now(clock));
        e.setUsuario(usuarioActual());
        e.setAccion(accion);
        e.setEntidad(entidad);
        e.setEntidadId(entidadId);
        e.setLocalId(localId);
        e.setDetalle(detalle.length() > MAX_DETALLE ? detalle.substring(0, MAX_DETALLE) : detalle);
        e.setRequestId(MDC.get("requestId"));
        eventos.save(e);
        log.info("Auditoría | accion={} | entidad={} | id={} | usuario={}", accion, entidad, entidadId, e.getUsuario());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponseDTO<AuditoriaEventoResponseDTO> listar(Long localId, AccionAuditoria accion, String usuario, LocalDate desde, LocalDate hasta,
                                                               int pagina, int tamanio, String ordenarPor, String direccion) {
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new DatosInvalidosException("Rango de fechas inválido");
        ZoneId zona = clock.getZone();
        var pageable = PaginacionUtil.construir(pagina, tamanio, ordenarPor, direccion, ORDENABLES, "fecha");
        // El dueño sin filtro ve todos los locales; el administrador, solo el suyo.
        var pagina_ = eventos.filtrar(contexto.localIdOTodos(localId), accion,
                        usuario == null || usuario.isBlank() ? null : usuario.trim(),
                        desde == null ? null : desde.atStartOfDay(zona).toOffsetDateTime(),
                        hasta == null ? null : hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime(), pageable)
                .map(AuditoriaServiceImpl::respuesta);
        return PaginaResponseDTO.de(pagina_);
    }

    private static AuditoriaEventoResponseDTO respuesta(AuditoriaEvento e) {
        return new AuditoriaEventoResponseDTO(e.getId(), e.getFecha(), e.getUsuario(), e.getAccion(), e.getEntidad(), e.getEntidadId(),
                e.getLocalId(), e.getDetalle(), e.getRequestId());
    }

    private static String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? auth.getName() : "sistema";
    }
}
