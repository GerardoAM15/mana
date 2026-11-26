package pe.edu.upeu.mana.service.impl;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.mana.entity.DiaNoAtencion;
import pe.edu.upeu.mana.repository.DiaNoAtencionRepository;
import pe.edu.upeu.mana.mapper.DiaNoAtencionMapper;
import pe.edu.upeu.mana.service.*;
import pe.edu.upeu.mana.exception.*;
import pe.edu.upeu.mana.service.service.*;
import pe.edu.upeu.mana.dto.calendario.DiaNoAtencionRequestDTO;
import pe.edu.upeu.mana.dto.calendario.DiaNoAtencionResponseDTO;

@Service
public class CalendarioServiceImpl implements CalendarioService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CalendarioServiceImpl.class);

    private final DiaNoAtencionRepository dias;

    private final ContextoLocalService contexto;

    private final DiaNoAtencionMapper mapper;
    private final CompensacionesRacion compensaciones;
    private final java.time.Clock clock;

    public CalendarioServiceImpl(DiaNoAtencionRepository dias, ContextoLocalService contexto, DiaNoAtencionMapper mapper, CompensacionesRacion compensaciones, java.time.Clock clock) {
        this.dias = dias;
        this.contexto = contexto;
        this.mapper = mapper;
        this.compensaciones = compensaciones;
        this.clock = clock;
    }

    @Transactional
    public DiaNoAtencionResponseDTO crear(DiaNoAtencionRequestDTO r) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio CalendarioServiceImpl.crear");
        try {
            validarFecha(r.getFecha());
            compensaciones.bloquearLocal(contexto.localId(null));
            var e = new DiaNoAtencion();
            e.setLocalId(contexto.localId(null));
            e.setFecha(r.getFecha());
            e.setMotivo(r.getMotivo());
            dias.saveAndFlush(e);
            compensaciones.aplicarCierre(e);
            return mapper.toResponse(e);
        } finally {
            log.debug("Fin CalendarioServiceImpl.crear | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    @Transactional
    public DiaNoAtencionResponseDTO actualizar(Long id, DiaNoAtencionRequestDTO r) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio CalendarioServiceImpl.actualizar");
        try {
            compensaciones.bloquearLocal(contexto.localId(null));
            var e = obtener(id);
            validarFecha(e.getFecha());
            validarFecha(r.getFecha());
            boolean cambiaFecha = !e.getFecha().equals(r.getFecha());
            if (cambiaFecha) compensaciones.quitarCierre(id);
            e.setFecha(r.getFecha());
            e.setMotivo(r.getMotivo());
            dias.flush();
            if (cambiaFecha) compensaciones.aplicarCierre(e);
            return mapper.toResponse(e);
        } finally {
            log.debug("Fin CalendarioServiceImpl.actualizar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    @Transactional(readOnly = true)
    public List<DiaNoAtencionResponseDTO> listar(Long localId, LocalDate desde, LocalDate hasta) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio CalendarioServiceImpl.listar");
        try {
            if (desde != null && hasta != null && desde.isAfter(hasta))
                throw new DatosInvalidosException("El inicio no puede ser posterior al fin");
            return dias.filtrar(contexto.localId(localId), desde, hasta).stream().map(mapper::toResponse).toList();
        } finally {
            log.debug("Fin CalendarioServiceImpl.listar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio CalendarioServiceImpl.eliminar");
        try {
            compensaciones.bloquearLocal(contexto.localId(null));
            var e = obtener(id);
            validarFecha(e.getFecha());
            compensaciones.quitarCierre(id);
            dias.delete(e);
        } finally {
            log.debug("Fin CalendarioServiceImpl.eliminar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    private DiaNoAtencion obtener(Long id) {
        var e = dias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Día no encontrado"));
        contexto.validarLocal(e.getLocalId());
        return e;
    }

    private void validarFecha(LocalDate fecha) {
        if (fecha == null || !fecha.isAfter(LocalDate.now(clock)))
            throw new DatosInvalidosException("Solo se permiten cierres posteriores al día actual (desde mañana)");
    }
}
