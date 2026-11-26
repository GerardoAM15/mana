package pe.edu.upeu.mana.repository;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.mana.entity.CompensacionRacion;

public interface CompensacionRacionRepository extends JpaRepository<CompensacionRacion,Long> {
    List<CompensacionRacion> findByDetalleContratoIdOrderByFechaCompensacion(Long id);
    List<CompensacionRacion> findByDiaNoAtencionId(Long id);
    Optional<CompensacionRacion> findByDetalleContratoIdAndFechaCompensacion(Long id, LocalDate fecha);
    Optional<CompensacionRacion> findByConsumoId(Long id);
    @Query("select count(r)>0 from CompensacionRacion r,DetalleContrato d where r.detalleContratoId=d.id and d.contratoId=:contrato and d.servicio=:servicio and r.fechaCompensacion=:fecha and r.consumoId is null")
    boolean disponible(@Param("contrato") Long contrato,@Param("servicio") String servicio,@Param("fecha") LocalDate fecha);
    boolean existsByDetalleContratoIdAndDiaNoAtencionId(Long detalle,Long cierre);

    @Query("select r from CompensacionRacion r,DetalleContrato d,Contrato c where r.detalleContratoId=d.id and d.contratoId=c.id and c.localId=:local and c.estado<>pe.edu.upeu.mana.enums.EstadoContrato.ANULADO order by r.fechaCompensacion,r.id")
    List<CompensacionRacion> porLocal(@Param("local") Long local);

    @Query("select r.fechaCompensacion from CompensacionRacion r,DetalleContrato d,Contrato c where r.detalleContratoId=d.id and d.contratoId=c.id and c.clienteId=:cliente and d.servicio=:servicio and c.estado<>pe.edu.upeu.mana.enums.EstadoContrato.ANULADO and r.fechaCompensacion between :desde and :hasta")
    List<LocalDate> fechasReservadas(@Param("cliente") Long cliente,@Param("servicio") String servicio,@Param("desde") LocalDate desde,@Param("hasta") LocalDate hasta);

    @Query("select count(r)>0 from CompensacionRacion r,DetalleContrato d,Contrato c where r.detalleContratoId=d.id and d.contratoId=c.id and c.clienteId=:cliente and d.servicio=:servicio and c.id<>:contrato and c.estado<>pe.edu.upeu.mana.enums.EstadoContrato.ANULADO and r.fechaCompensacion=:fecha")
    boolean reservaAjena(@Param("cliente") Long cliente,@Param("servicio") String servicio,@Param("contrato") Long contrato,@Param("fecha") LocalDate fecha);

    @Query("select c.id from CompensacionRacion r,DetalleContrato d,Contrato c where r.detalleContratoId=d.id and d.contratoId=c.id and c.clienteId=:cliente and c.localId=:local and c.estado<>pe.edu.upeu.mana.enums.EstadoContrato.ANULADO and d.saldoRaciones>0 and r.consumoId is null and r.fechaCompensacion=:fecha order by c.id")
    List<Long> contratosParaHoy(@Param("cliente") Long cliente,@Param("local") Long local,@Param("fecha") LocalDate fecha);
}
