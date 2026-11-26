package pe.edu.upeu.mana.dto.contrato;
import java.time.LocalDate;
public record CompensacionRacionResponseDTO(Long id,LocalDate fechaOrigen,LocalDate fechaCompensacion,boolean consumida) {}
