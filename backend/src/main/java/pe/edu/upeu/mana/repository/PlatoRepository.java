package pe.edu.upeu.mana.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.mana.entity.*;

public interface PlatoRepository extends JpaRepository<Plato, Long> {

    @Query(value = "select count(*) from platos where categoria = :categoria and id <> :id "
            + "and upper(trim(regexp_replace(nombre, '[[:space:]]+', ' '))) = upper(:nombre)", nativeQuery = true)
    long contarDuplicados(@Param("nombre") String nombre, @Param("categoria") String categoria, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Plato e where e.id = :id")
    Optional<Plato> buscarParaActualizar(@Param("id") Long id);
}
