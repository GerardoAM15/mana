package pe.edu.upeu.mana.service;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.upeu.mana.entity.Empleado;
import pe.edu.upeu.mana.entity.Usuario;
import pe.edu.upeu.mana.enums.RolUsuario;
import pe.edu.upeu.mana.repository.EmpleadoRepository;
import pe.edu.upeu.mana.repository.UsuarioRepository;
import pe.edu.upeu.mana.service.impl.ContextoLocalServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextoLocalServiceImplTest {
    @Mock UsuarioRepository usuarios;
    @Mock EmpleadoRepository empleados;

    @AfterEach void limpiar() {
        SecurityContextHolder.clearContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test void soloElDuenoPuedeConsultarOtrosLocales() {
        var service = preparar(RolUsuario.DUENO);
        assertEquals(1L, service.localId(null));
        assertEquals(2L, service.localId(2L));
        assertDoesNotThrow(() -> service.validarLocal(2L));
    }

    @ParameterizedTest
    @EnumSource(value = RolUsuario.class, names = {"ADMINISTRADOR", "CAJERO"})
    void administradorYCajeroSoloOperanEnSuLocal(RolUsuario rol) {
        var service = preparar(rol);
        assertEquals(1L, service.localId(null));
        assertEquals(1L, service.localId(1L));
        assertDoesNotThrow(() -> service.validarLocal(1L));
        assertThrows(AccessDeniedException.class, () -> service.localId(2L));
        assertThrows(AccessDeniedException.class, () -> service.validarLocal(2L));
    }

    @Test void elDuenoSinFiltroVeTodosLosLocalesYConFiltroElElegido() {
        var service = preparar(RolUsuario.DUENO);
        assertNull(service.localIdOTodos(null));
        assertEquals(2L, service.localIdOTodos(2L));
    }

    @Test void elAdministradorSiempreQuedaEnSuLocalAunqueNoFiltre() {
        var service = preparar(RolUsuario.ADMINISTRADOR);
        assertEquals(1L, service.localIdOTodos(null));
        assertThrows(AccessDeniedException.class, () -> service.localIdOTodos(2L));
    }

    @Test void cacheEsExclusivaDeLaPeticionYEvitaConsultasRepetidas() {
        var service=preparar(RolUsuario.CAJERO);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        service.usuario(); service.localId(null); service.validarLocal(1L); service.localId(1L);
        verify(usuarios,times(1)).findByCorreoIgnoreCase("actor@example.invalid"); verify(empleados,times(1)).findById(10L);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        service.localId(null);
        verify(usuarios,times(2)).findByCorreoIgnoreCase("actor@example.invalid"); verify(empleados,times(2)).findById(10L);
    }

    private ContextoLocalServiceImpl preparar(RolUsuario rol) {
        var usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setEmpleadoId(10L);
        var empleado = new Empleado();
        empleado.setLocalId(1L);
        when(usuarios.findByCorreoIgnoreCase("actor@example.invalid")).thenReturn(Optional.of(usuario));
        when(empleados.findById(10L)).thenReturn(Optional.of(empleado));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("actor@example.invalid", null, List.of()));
        return new ContextoLocalServiceImpl(usuarios, empleados);
    }
}
