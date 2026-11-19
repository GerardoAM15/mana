package pe.edu.upeu.mana.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import pe.edu.upeu.mana.enums.AccionAuditoria;

/** Un registro del historial de auditoría: quién, cuándo, qué acción, sobre qué registro y qué cambió. */
@Getter
@Setter
@Entity
@Table(name = "auditoria_eventos")
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, precision = 19, scale = 0, columnDefinition = "number(19,0)")
    private Long id;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "fecha", nullable = false, columnDefinition = "timestamp(9) with time zone")
    private OffsetDateTime fecha;

    @Column(name = "usuario", nullable = false, length = 160)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 40)
    private AccionAuditoria accion;

    @Column(name = "entidad", nullable = false, length = 60)
    private String entidad;

    @Column(name = "entidad_id", nullable = false, precision = 19, scale = 0, columnDefinition = "number(19,0)")
    private Long entidadId;

    @Column(name = "local_id", precision = 19, scale = 0, columnDefinition = "number(19,0)")
    private Long localId;

    @Column(name = "detalle", nullable = false, length = 1000)
    private String detalle;

    @Column(name = "request_id", length = 64)
    private String requestId;
}
