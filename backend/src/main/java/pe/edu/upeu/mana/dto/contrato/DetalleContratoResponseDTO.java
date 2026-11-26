package pe.edu.upeu.mana.dto.contrato;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetalleContratoResponseDTO {

    public DetalleContratoResponseDTO(Long id,String servicio,Long racionesContratadas,Long saldoRaciones,BigDecimal precioRacionPactado,BigDecimal subtotal) {
        this(id,servicio,racionesContratadas,saldoRaciones,precioRacionPactado,subtotal,null,java.util.List.of());
    }

    private Long id;

    private String servicio;

    private Long racionesContratadas;

    private Long saldoRaciones;

    private BigDecimal precioRacionPactado;

    private BigDecimal subtotal;

    private Boolean disponibleHoy;

    private java.util.List<CompensacionRacionResponseDTO> compensaciones = java.util.List.of();
}
