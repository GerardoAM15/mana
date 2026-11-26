package pe.edu.upeu.mana.dto.auth;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    private String token;

    private String tipo;

    private Long expiraEnSegundos;

    private Long usuarioId;

    private String nombreCompleto;

    private String rol;

    private Long localId;

    private String localNombre;

    private List<String> permisos;

    // true cuando la contraseña con la que inició sesión ya no cumple la política: el frontend
    // obliga a cambiarla antes de continuar. Se omite del JSON cuando no aplica.
    private Boolean debeCambiarClave;
}
