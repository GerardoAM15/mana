package pe.edu.upeu.mana.repository;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.mana.entity.AuditoriaEvento;
import pe.edu.upeu.mana.enums.AccionAuditoria;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {

    @Query("select e from AuditoriaEvento e where (:localId is null or e.localId=:localId) and (:accion is null or e.accion=:accion) and (:usuario is null or e.usuario=:usuario) and (:desde is null or e.fecha>=:desde) and (:hasta is null or e.fecha<:hasta)")
    Page<AuditoriaEvento> filtrar(@Param("localId") Long localId, @Param("accion") AccionAuditoria accion, @Param("usuario") String usuario,
                                  @Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta, Pageable pageable);
}
