package pe.edu.upeu.mana.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.mana.entity.*;

public interface ProgramacionMenuRepository extends JpaRepository<ProgramacionMenu, Long> {

    @Query("select m from ProgramacionMenu m, MenuPlato p where p.menuId=m.id and p.platoId=:platoId and m.fecha>=:hoy and not exists (select c.id from DiaNoAtencion c where c.localId=m.localId and c.fecha=m.fecha) order by m.fecha,m.servicio")
    java.util.List<ProgramacionMenu> futurasConPlato(@Param("platoId") Long platoId, @Param("hoy") java.time.LocalDate hoy);

    Optional<ProgramacionMenu> findByLocalIdAndFechaAndServicio(Long localId, java.time.LocalDate fecha, String servicio);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProgramacionMenu m where m.localId=:localId and m.fecha=:fecha and m.servicio=:servicio")
    Optional<ProgramacionMenu> buscarFechaParaActualizar(@Param("localId") Long localId, @Param("fecha") java.time.LocalDate fecha, @Param("servicio") String servicio);

    @Query("select m from ProgramacionMenu m where m.localId=:localId and (:desde is null or m.fecha>=:desde) and (:hasta is null or m.fecha<=:hasta) and (:servicio is null or m.servicio=:servicio) order by m.fecha,m.servicio")
    java.util.List<ProgramacionMenu> filtrar(@Param("localId") Long localId, @Param("desde") java.time.LocalDate desde, @Param("hasta") java.time.LocalDate hasta, @Param("servicio") String servicio);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProgramacionMenu e where e.id = :id")
    Optional<ProgramacionMenu> buscarParaActualizar(@Param("id") Long id);
}
