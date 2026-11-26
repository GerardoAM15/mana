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
public class ClonarMenuRequestDTO {

    public ClonarMenuRequestDTO(LocalDate origen, LocalDate destino, Set<String> servicios) {
        this(origen, destino, servicios, java.util.Map.of());
    }

    @NotNull(message = "Fecha origen obligatoria")
    private LocalDate fechaOrigen;

    @NotNull(message = "Fecha destino obligatoria")
    private LocalDate fechaDestino;

    private Set<@NotBlank(message = "Servicio obligatorio") @Size(max = 32, message = "Máximo 32 caracteres") String> servicios;

    @NotNull(message = "Versiones destino obligatorias")
    private java.util.Map<String, @NotNull @Min(-1) Long> versionesDestino = java.util.Map.of();
}
