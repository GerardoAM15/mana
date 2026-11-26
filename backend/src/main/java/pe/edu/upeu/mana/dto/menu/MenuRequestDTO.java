package pe.edu.upeu.mana.dto.menu;

import java.time.LocalDate;
import java.util.Set;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuRequestDTO {

    public MenuRequestDTO(LocalDate fecha, String servicio, Set<Long> platoIds) {
        this(fecha, servicio, platoIds, -1L);
    }

    @NotNull(message = "Fecha obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "Servicio obligatorio")
    @Size(max = 32, message = "Máximo 32 caracteres")
    private String servicio;

    @NotEmpty(message = "Seleccione platos")
    private Set<@NotNull(message = "Plato obligatorio") @Positive(message = "Plato inválido") Long> platoIds;

    @NotNull(message = "Versión obligatoria")
    @Min(value = -1, message = "Versión inválida")
    private Long version = -1L;
}
