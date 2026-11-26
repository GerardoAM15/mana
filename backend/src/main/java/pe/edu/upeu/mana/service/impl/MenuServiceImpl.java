package pe.edu.upeu.mana.service.impl;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.exception.*;
import pe.edu.upeu.mana.repository.*;
import pe.edu.upeu.mana.mapper.MenuMapper;
import pe.edu.upeu.mana.service.*;
import pe.edu.upeu.mana.service.service.*;
import pe.edu.upeu.mana.dto.menu.ClonarMenuRequestDTO;
import pe.edu.upeu.mana.dto.menu.CopiarCenaRequestDTO;
import pe.edu.upeu.mana.dto.menu.MenuRequestDTO;
import pe.edu.upeu.mana.dto.menu.MenuResponseDTO;

@Service
public class MenuServiceImpl implements MenuService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MenuServiceImpl.class);

    private final ProgramacionMenuRepository menus;

    private final MenuPlatoRepository relaciones;

    private final PlatoRepository platos;

    private final ServicioRepository servicios;

    private final ContextoLocalService contexto;

    private final MenuMapper mapper;

    private final ValidadorAtencion atencion;
    private final ReglaCobroRepository reglas;
    private final ComponenteReglaRepository componentes;
    private final LocalRepository locales;

    public MenuServiceImpl(ProgramacionMenuRepository menus, MenuPlatoRepository relaciones, PlatoRepository platos, ServicioRepository servicios, ContextoLocalService contexto, MenuMapper mapper, ValidadorAtencion atencion, ReglaCobroRepository reglas, ComponenteReglaRepository componentes, LocalRepository locales) {
        this.menus = menus;
        this.relaciones = relaciones;
        this.platos = platos;
        this.servicios = servicios;
        this.contexto = contexto;
        this.mapper = mapper;
        this.atencion = atencion;
        this.reglas = reglas;
        this.componentes = componentes;
        this.locales = locales;
    }

    @Transactional
    public MenuResponseDTO guardar(MenuRequestDTO r) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio MenuServiceImpl.guardar");
        try {
            return respuesta(guardar(contexto.localId(null), r.getFecha(), r.getServicio(), r.getPlatoIds(), r.getVersion()));
        } finally {
            log.debug("Fin MenuServiceImpl.guardar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    private ProgramacionMenu guardar(Long localId, LocalDate fecha, String servicio, Set<Long> ids, Long version) {
        if (!servicios.findById(servicio).map(s -> Boolean.TRUE.equals(s.getActivo())).orElse(false))
            throw new ReglaNegocioException("Servicio inexistente o inactivo");
        atencion.validar(localId, fecha, servicio);
        // Una celda nueva todavía no tiene fila que bloquear. El local permite
        // detectar también dos creaciones simultáneas con versión -1.
        locales.buscarParaActualizar(localId).orElseThrow(() -> new RecursoNoEncontradoException("Local no encontrado"));
        if (ids == null || ids.isEmpty())
            throw new DatosInvalidosException("Seleccione al menos un plato");
        // El mismo bloqueo se usa al desactivar un plato: no puede quedar una baja
        // concurrente entre la validación y la publicación de la programación.
        var seleccion = ids.stream().sorted().map(id -> platos.buscarParaActualizar(id)
                .orElseThrow(() -> new ReglaNegocioException("Hay platos inexistentes o inactivos"))).toList();
        if (seleccion.stream().anyMatch(p -> !Boolean.TRUE.equals(p.getActivo())))
            throw new ReglaNegocioException("Hay platos inexistentes o inactivos");
        var activas = reglas.findByServicioAndActivoTrueOrderById(servicio);
        if (activas.size() != 1)
            throw new ReglaNegocioException("Configure un único menú público activo con su composición para este turno antes de programar");
        var categoriasPermitidas = componentes.findByReglaId(activas.getFirst().getId()).stream()
                .map(ComponenteRegla::getCategoria).collect(java.util.stream.Collectors.toSet());
        if (categoriasPermitidas.isEmpty())
            throw new ReglaNegocioException("El menú público del turno no tiene composición configurada");
        if (seleccion.stream().anyMatch(p -> !categoriasPermitidas.contains(p.getCategoria())))
            throw new ReglaNegocioException("Hay platos de categorías que no corresponden a la composición de este turno");
        var presentes = seleccion.stream().map(Plato::getCategoria).collect(java.util.stream.Collectors.toSet());
        var faltantes = categoriasPermitidas.stream().filter(c -> !presentes.contains(c)).sorted().toList();
        if (!faltantes.isEmpty())
            throw new ReglaNegocioException("Complete la programación: falta al menos un plato de cada categoría: " + String.join(", ", faltantes));
        var e = menus.buscarFechaParaActualizar(localId, fecha, servicio).orElseGet(ProgramacionMenu::new);
        long actual = e.getId() == null ? -1L : e.getVersion();
        if (version == null || version != actual)
            throw new ReglaNegocioException("Esta programación fue modificada por otro usuario. Recárguela y revise los cambios antes de guardar");
        e.setLocalId(localId);
        e.setFecha(fecha);
        e.setServicio(servicio);
        // Marca también el guardado de relaciones: así JPA actualiza la raíz y
        // avanza @Version aunque fecha, local y servicio sigan siendo iguales.
        if (e.getId() != null) e.setActualizadoEn(e.getActualizadoEn().plusNanos(1));
        menus.saveAndFlush(e);
        relaciones.deleteAll(relaciones.findByMenuId(e.getId()));
        relaciones.flush();
        for (Long id : ids.stream().sorted().toList()) {
            var p = new MenuPlato();
            p.setMenuId(e.getId());
            p.setPlatoId(id);
            relaciones.save(p);
        }
        relaciones.flush();
        return e;
    }

    @Transactional(readOnly = true)
    public List<MenuResponseDTO> listar(Long localId, LocalDate desde, LocalDate hasta, String servicio) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio MenuServiceImpl.listar");
        try {
            if (desde != null && hasta != null && desde.isAfter(hasta))
                throw new DatosInvalidosException("Rango de fechas inválido");
            return menus.filtrar(contexto.localId(localId), desde, hasta, servicio).stream().map(this::respuesta).toList();
        } finally {
            log.debug("Fin MenuServiceImpl.listar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    @Transactional
    public List<MenuResponseDTO> clonar(ClonarMenuRequestDTO r) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio MenuServiceImpl.clonar");
        try {
            if (r.getFechaOrigen().equals(r.getFechaDestino()))
                throw new DatosInvalidosException("Las fechas origen y destino deben ser distintas");
            Long local = contexto.localId(null);
            var origen = menus.filtrar(local, r.getFechaOrigen(), r.getFechaOrigen(), null).stream().filter(m -> r.getServicios() == null || r.getServicios().isEmpty() || r.getServicios().contains(m.getServicio())).toList();
            if (origen.isEmpty())
                throw new RecursoNoEncontradoException("No hay menús en la fecha origen");
            var resultado = new ArrayList<MenuResponseDTO>();
            for (var e : origen) {
                if (!atencion.seAtiende(local, r.getFechaDestino(), e.getServicio())) continue;
                var ids = new HashSet<>(relaciones.findByMenuId(e.getId()).stream().map(MenuPlato::getPlatoId).toList());
                resultado.add(respuesta(guardar(local, r.getFechaDestino(), e.getServicio(), ids,
                        r.getVersionesDestino().getOrDefault(e.getServicio(), -1L))));
            }
            return resultado;
        } finally {
            log.debug("Fin MenuServiceImpl.clonar | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    @Transactional
    public MenuResponseDTO copiarACena(CopiarCenaRequestDTO r) {
        long inicioOperacion = System.nanoTime();
        log.debug("Inicio MenuServiceImpl.copiarACena");
        try {
            Long local = contexto.localId(null);
            atencion.validar(local, r.getFecha(), "C");
            var almuerzo = menus.findByLocalIdAndFechaAndServicio(local, r.getFecha(), "A").orElseThrow(() -> new RecursoNoEncontradoException("No hay menú de almuerzo"));
            var ids = new HashSet<Long>();
            for (var p : obtenerPlatos(almuerzo)) if (p.getCategoria().equals("SEGUNDO"))
                ids.add(p.getId());
            if (ids.isEmpty())
                throw new ReglaNegocioException("El almuerzo no tiene segundos");
            menus.findByLocalIdAndFechaAndServicio(local, r.getFecha(), "C").ifPresent(c -> relaciones.findByMenuId(c.getId()).forEach(p -> ids.add(p.getPlatoId())));
            return respuesta(guardar(local, r.getFecha(), "C", ids, r.getVersion()));
        } finally {
            log.debug("Fin MenuServiceImpl.copiarACena | duracionMs={}", (System.nanoTime() - inicioOperacion) / 1_000_000);
        }
    }

    private List<Plato> obtenerPlatos(ProgramacionMenu e) {
        var ids = relaciones.findByMenuId(e.getId()).stream().map(MenuPlato::getPlatoId).sorted().toList();
        return platos.findAllById(ids).stream().sorted(Comparator.comparing(Plato::getId)).toList();
    }

    private MenuResponseDTO respuesta(ProgramacionMenu e) {
        var respuesta = mapper.toResponse(e, obtenerPlatos(e));
        respuesta.setAtiende(atencion.seAtiende(e.getLocalId(), e.getFecha(), e.getServicio()));
        return respuesta;
    }
}
