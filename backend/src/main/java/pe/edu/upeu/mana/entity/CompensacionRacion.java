package pe.edu.upeu.mana.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Una oportunidad de recuperar una ración ya contratada; no agrega saldo ni cobro. */
@Getter @Setter @Entity @Table(name = "compensaciones_racion")
public class CompensacionRacion extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(precision=19, scale=0, columnDefinition="number(19,0)")
    private Long id;
    @Column(name="detalle_contrato_id", nullable=false, precision=19, scale=0, columnDefinition="number(19,0)")
    private Long detalleContratoId;
    @Column(name="dia_no_atencion_id", nullable=false, precision=19, scale=0, columnDefinition="number(19,0)")
    private Long diaNoAtencionId;
    @Column(name="fecha_origen", nullable=false, columnDefinition="date")
    private LocalDate fechaOrigen;
    @Column(name="fecha_compensacion", nullable=false, columnDefinition="date")
    private LocalDate fechaCompensacion;
    @Column(name="consumo_id", precision=19, scale=0, columnDefinition="number(19,0)")
    private Long consumoId;
}
