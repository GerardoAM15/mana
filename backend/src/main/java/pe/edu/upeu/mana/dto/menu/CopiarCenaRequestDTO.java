package pe.edu.upeu.mana.dto.menu;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CopiarCenaRequestDTO {

    public CopiarCenaRequestDTO(LocalDate fecha) { this(fecha, -1L); }

    @NotNull(message = "Fecha obligatoria")
    private LocalDate fecha;

    @NotNull(message = "Versión destino obligatoria")
    @Min(-1)
    private Long version = -1L;
}
