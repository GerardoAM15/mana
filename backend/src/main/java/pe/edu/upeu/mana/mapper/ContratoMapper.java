package pe.edu.upeu.mana.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.service.EstadoContratoActual;
import pe.edu.upeu.mana.dto.consumo.ConsumoResponseDTO;
import pe.edu.upeu.mana.dto.contrato.ContratoResponseDTO;
import pe.edu.upeu.mana.dto.contrato.DetalleContratoResponseDTO;
import pe.edu.upeu.mana.dto.contrato.PagoContratoResponseDTO;
import pe.edu.upeu.mana.dto.contrato.PensionistaResponseDTO;

@Component
public class ContratoMapper {

    public ContratoMapper(EstadoContratoActual estados, pe.edu.upeu.mana.repository.CompensacionRacionRepository compensaciones, java.time.Clock clock) {
        this.estados = estados;
        this.compensaciones = compensaciones;
        this.clock = clock;
    }

    private final EstadoContratoActual estados;
    private final pe.edu.upeu.mana.repository.CompensacionRacionRepository compensaciones;
    private final java.time.Clock clock;

    public DetalleContratoResponseDTO toDetalle(DetalleContrato d) {
        return new DetalleContratoResponseDTO(d.getId(), d.getServicio(), d.getRacionesContratadas(), d.getSaldoRaciones(), d.getPrecioRacionPactado(), d.getSubtotal());
    }

    private DetalleContratoResponseDTO toDetalle(DetalleContrato d, Contrato c) {
        var dto = toDetalle(d);
        var reservas = compensaciones.findByDetalleContratoIdOrderByFechaCompensacion(d.getId());
        dto.setCompensaciones(reservas.stream().map(r -> new pe.edu.upeu.mana.dto.contrato.CompensacionRacionResponseDTO(r.getId(),r.getFechaOrigen(),r.getFechaCompensacion(),r.getConsumoId()!=null)).toList());
        var hoy = java.time.LocalDate.now(clock);
        boolean ordinario = estados.calcular(c) == pe.edu.upeu.mana.enums.EstadoContrato.ACTIVO;
        boolean recupera = reservas.stream().anyMatch(r -> r.getFechaCompensacion().equals(hoy) && r.getConsumoId()==null);
        dto.setDisponibleHoy(c.getEstado()!=pe.edu.upeu.mana.enums.EstadoContrato.ANULADO && d.getSaldoRaciones()>0 && (ordinario || recupera)
                && !compensaciones.reservaAjena(c.getClienteId(),d.getServicio(),c.getId(),hoy));
        return dto;
    }

    public ContratoResponseDTO toResponse(Contrato c, Cliente cliente, TipoPlan plan, List<DetalleContrato> detalles, List<PagoContrato> pagos, List<ConsumoResponseDTO> consumos) {
        return new ContratoResponseDTO(c.getId(), c.getNumero(), c.getLocalId(), c.getClienteId(), cliente.getDni(), cliente.getNombre() + " " + cliente.getApellido(), c.getPlanId(), plan.getNombre(), estados.calcular(c), c.getFechaInicio(), c.getFechaFin(), c.getMontoTotal(), c.getAnteriorId(), c.getAnuladoEn(), c.getMotivoAnulacion(), detalles.stream().map(d -> toDetalle(d, c)).toList(), pagos.stream().map(p -> new PagoContratoResponseDTO(p.getId(), p.getCajaId(), p.getTipo(), p.getMetodo(), p.getMonto(), p.getFecha(), p.getReferencia())).toList(), consumos, c.getVersion());
    }

    public PensionistaResponseDTO toPensionista(Contrato c, Cliente p, List<DetalleContrato> detalles) {
        return new PensionistaResponseDTO(p.getId(), p.getDni(), p.getNombre() + " " + p.getApellido(), c.getId(), c.getNumero(), estados.calcular(c), c.getFechaInicio(), c.getFechaFin(), detalles.stream().map(d -> toDetalle(d, c)).toList());
    }
}
