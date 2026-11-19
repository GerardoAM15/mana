package pe.edu.upeu.mana.service.service;

import java.time.LocalDate;
import pe.edu.upeu.mana.dto.PaginaResponseDTO;
import pe.edu.upeu.mana.dto.auditoria.AuditoriaEventoResponseDTO;
import pe.edu.upeu.mana.enums.AccionAuditoria;

public interface AuditoriaService {

    /**
     * Registra una operación crítica dentro de la transacción en curso: si la operación se revierte,
     * el registro de auditoría también.
     */
    void registrar(AccionAuditoria accion, String entidad, Long entidadId, Long localId, String detalle);

    PaginaResponseDTO<AuditoriaEventoResponseDTO> listar(Long localId, AccionAuditoria accion, String usuario, LocalDate desde, LocalDate hasta,
                                                        int pagina, int tamanio, String ordenarPor, String direccion);
}
