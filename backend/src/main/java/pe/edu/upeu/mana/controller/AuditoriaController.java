package pe.edu.upeu.mana.controller;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.mana.dto.PaginaResponseDTO;
import pe.edu.upeu.mana.dto.auditoria.AuditoriaEventoResponseDTO;
import pe.edu.upeu.mana.enums.AccionAuditoria;
import pe.edu.upeu.mana.service.service.AuditoriaService;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponseDTO<AuditoriaEventoResponseDTO> listar(@RequestParam(required = false) Long localId,
                                                               @RequestParam(required = false) AccionAuditoria accion,
                                                               @RequestParam(required = false) String usuario,
                                                               @RequestParam(required = false) LocalDate desde,
                                                               @RequestParam(required = false) LocalDate hasta,
                                                               @RequestParam(defaultValue = "0") int pagina,
                                                               @RequestParam(defaultValue = "20") int tamanio,
                                                               @RequestParam(defaultValue = "fecha") String ordenarPor,
                                                               @RequestParam(defaultValue = "desc") String direccion) {
        return service.listar(localId, accion, usuario, desde, hasta, pagina, tamanio, ordenarPor, direccion);
    }
}
