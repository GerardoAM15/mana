package pe.edu.upeu.mana.dto.auditoria;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.upeu.mana.enums.AccionAuditoria;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaEventoResponseDTO {

    private Long id;

    private OffsetDateTime fecha;

    private String usuario;

    private AccionAuditoria accion;

    private String entidad;

    private Long entidadId;

    private Long localId;

    private String detalle;

    private String requestId;
}
