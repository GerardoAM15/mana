package pe.edu.upeu.mana.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.enums.EstadoContrato;
import pe.edu.upeu.mana.exception.*;
import pe.edu.upeu.mana.repository.*;

/** Se invoca en la transacción de calendario/contratos/consumos. El local serializa las reservas. */
@Service
public class CompensacionesRacion {
    private final CompensacionRacionRepository reservas;
    private final ContratoRepository contratos;
    private final DetalleContratoRepository detalles;
    private final LocalRepository locales;
    private final ServicioDiaAtencionRepository dias;
    private final ValidadorAtencion atencion;
    private final Clock clock;
    private final ClienteRepository clientes;
    private final ServicioRepository servicios;

    public CompensacionesRacion(CompensacionRacionRepository reservas,ContratoRepository contratos,
            DetalleContratoRepository detalles,LocalRepository locales,ServicioDiaAtencionRepository dias,
            ValidadorAtencion atencion,Clock clock,ClienteRepository clientes,ServicioRepository servicios) {
        this.reservas=reservas;this.contratos=contratos;this.detalles=detalles;this.locales=locales;
        this.dias=dias;this.atencion=atencion;this.clock=clock;
        this.clientes=clientes;this.servicios=servicios;
    }
    public void bloquearLocal(Long local) {
        locales.buscarParaActualizar(local).orElseThrow(()->new RecursoNoEncontradoException("Local no encontrado"));
    }
    public void quitarCierre(Long cierre) {
        var afectadas=reservas.findByDiaNoAtencionId(cierre);
        if(afectadas.stream().anyMatch(r->r.getConsumoId()!=null || !r.getFechaCompensacion().isAfter(LocalDate.now(clock))))
            throw new ReglaNegocioException("No se puede modificar un cierre cuya compensación ya comenzó");
        reservas.deleteAll(afectadas);reservas.flush();
    }
    public void aplicarCierre(DiaNoAtencion cierre) {
        var afectados=contratos.afectadosPorCierre(cierre.getLocalId(),cierre.getFecha());
        var existentes=reservas.porLocal(cierre.getLocalId());
        var personas=new TreeSet<Long>();afectados.forEach(c->personas.add(c.getClienteId()));
        existentes.stream().filter(r->r.getFechaCompensacion().equals(cierre.getFecha())).forEach(r->{
            var d=detalles.findById(r.getDetalleContratoId()).orElseThrow();personas.add(contratos.findById(d.getContratoId()).orElseThrow().getClienteId());
        });
        // También serializa reservas del mismo cliente en locales distintos.
        personas.forEach(id->clientes.buscarParaActualizar(id).orElseThrow());
        // Un cierre nuevo puede caer sobre una recuperación ya asignada: se mueve esa
        // misma oportunidad, sin generar otra ración ni modificar las ya utilizadas.
        for(var r:existentes) {
            if(r.getConsumoId()==null && r.getFechaCompensacion().equals(cierre.getFecha())) {
                var d=detalles.findById(r.getDetalleContratoId()).orElseThrow();
                var c=contratos.findById(d.getContratoId()).orElseThrow();
                r.setFechaCompensacion(siguiente(c,d,r.getFechaCompensacion().plusDays(1)));
                reservas.saveAndFlush(r);
            }
        }
        for(var c:afectados) {
            for(var d:detalles.findByContratoIdOrderById(c.getId())) {
                if(d.getSaldoRaciones()<=0 || reservas.existsByDetalleContratoIdAndDiaNoAtencionId(d.getId(),cierre.getId()))continue;
                boolean habitual=dias.findByServicio(d.getServicio()).stream()
                        .anyMatch(dia->dia.getDiaSemana()==cierre.getFecha().getDayOfWeek().getValue());
                if(!habitual)continue;
                long disponibles=c.getFechaInicio().datesUntil(c.getFechaFin().plusDays(1))
                        .filter(fecha->atencion.seAtiende(c.getLocalId(),fecha,d.getServicio()))
                        .filter(fecha->!reservas.reservaAjena(c.getClienteId(),d.getServicio(),c.getId(),fecha)).count();
                long asignadas=reservas.findByDetalleContratoIdOrderByFechaCompensacion(d.getId()).size();
                if(d.getRacionesContratadas()<=disponibles+asignadas)continue;
                var r=new CompensacionRacion();r.setDetalleContratoId(d.getId());r.setDiaNoAtencionId(cierre.getId());
                r.setFechaOrigen(cierre.getFecha());r.setFechaCompensacion(siguiente(c,d,c.getFechaFin().plusDays(1)));
                reservas.saveAndFlush(r);
            }
        }
    }
    private LocalDate siguiente(Contrato contrato,DetalleContrato detalle,LocalDate desde) {
        if(dias.findByServicio(detalle.getServicio()).isEmpty())
            throw new ReglaNegocioException("Configure días de atención para compensar el servicio " + detalle.getServicio());
        if(!servicios.findById(detalle.getServicio()).map(s->Boolean.TRUE.equals(s.getActivo())).orElse(false))
            throw new ReglaNegocioException("Active el servicio para poder asignar su fecha de compensación: " + detalle.getServicio());
        var otros=contratos.periodosDelServicio(contrato.getClienteId(),detalle.getServicio());
        LocalDate candidato=desde;
        for(int n=0;n<3660;n++,candidato=candidato.plusDays(1)) {
            if(!atencion.seAtiende(contrato.getLocalId(),candidato,detalle.getServicio()))continue;
            if(!reservas.fechasReservadas(contrato.getClienteId(),detalle.getServicio(),candidato,candidato).isEmpty())continue;
            LocalDate fecha=candidato;
            // No desplaza raciones de renovaciones ya cobradas. Una renovación nueva
            // descontará del cálculo las fechas que ya están reservadas para recuperar.
            if(otros.stream().anyMatch(c->!c.getId().equals(contrato.getId()) && !fecha.isBefore(c.getFechaInicio()) && !fecha.isAfter(c.getFechaFin())))continue;
            return candidato;
        }
        throw new ReglaNegocioException("No hay una fecha de atención disponible para compensar " + detalle.getServicio());
    }
    public List<LocalDate> fechasReservadas(Long cliente,String servicio,LocalDate desde,LocalDate hasta) {
        return reservas.fechasReservadas(cliente,servicio,desde,hasta);
    }
    public Optional<CompensacionRacion> paraHoy(Long detalle) {
        return reservas.findByDetalleContratoIdAndFechaCompensacion(detalle,LocalDate.now(clock)).filter(r->r.getConsumoId()==null);
    }
    public boolean tieneParaHoy(Long contrato,String servicio) {
        return reservas.disponible(contrato,servicio,LocalDate.now(clock));
    }
    public void validarReservaAjena(Contrato c,String servicio) {
        if(reservas.reservaAjena(c.getClienteId(),servicio,c.getId(),LocalDate.now(clock)))
            throw new ReglaNegocioException("Hoy corresponde recuperar una ración de otro contrato del pensionista. Seleccione ese contrato");
    }
    public void usar(CompensacionRacion r,Long consumo) {r.setConsumoId(consumo);reservas.save(r);}
    public void devolver(Long consumo) {reservas.findByConsumoId(consumo).ifPresent(r->r.setConsumoId(null));}
    public Optional<Long> contratoParaHoy(Long cliente,Long local) {
        return reservas.contratosParaHoy(cliente,local,LocalDate.now(clock)).stream().findFirst();
    }
}
