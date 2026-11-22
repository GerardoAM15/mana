package pe.edu.upeu.mana.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.upeu.mana.dto.auth.*;
import pe.edu.upeu.mana.entity.*;
import pe.edu.upeu.mana.enums.RolUsuario;
import pe.edu.upeu.mana.exception.DatosInvalidosException;
import pe.edu.upeu.mana.mapper.UsuarioMapper;
import pe.edu.upeu.mana.repository.*;
import pe.edu.upeu.mana.security.*;
import pe.edu.upeu.mana.service.impl.AuthServiceImpl;

@ExtendWith(MockitoExtension.class)
class AuthCambioClaveTest {
    @Mock UsuarioRepository usuarios;
    @Mock EmpleadoRepository empleados;
    @Mock LocalRepository locales;
    @Mock TokenRestablecimientoRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock ObjectProvider<JavaMailSender> correo;
    @Mock Environment environment;
    Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Lima"));
    AuthServiceImpl service;
    Usuario usuario;
    Empleado empleado;

    @BeforeEach
    void preparar() {
        service = new AuthServiceImpl(usuarios, empleados, locales, tokens, encoder, jwt, new UsuarioMapper(), clock, correo,
                environment, "http://localhost:4200", new LimitadorIntentos(clock));
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmpleadoId(2L);
        usuario.setCorreo("lucia.quispe@mana.pe");
        usuario.setClaveHash("hash-actual");
        usuario.setRol(RolUsuario.CAJERO);
        usuario.setActivo(true);
        usuario.setTokenVersion(4L);
        empleado = new Empleado();
        empleado.setId(2L);
        empleado.setLocalId(3L);
        empleado.setDni("70000001");
        empleado.setNombre("Lucía");
        empleado.setApellido("Quispe");
        empleado.setActivo(true);
    }

    private void loginValido(String clave) {
        var local = new Local();
        local.setId(3L);
        local.setActivo(true);
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        when(encoder.matches(clave, "hash-actual")).thenReturn(true);
        when(empleados.findById(2L)).thenReturn(Optional.of(empleado));
        when(locales.findById(3L)).thenReturn(Optional.of(local));
    }

    @Test
    void loginConClaveDebilPideCambiarla() {
        loginValido("12345678");
        var respuesta = service.login(new LoginRequestDTO(usuario.getCorreo(), "12345678"));
        assertEquals(Boolean.TRUE, respuesta.getDebeCambiarClave());
    }

    @Test
    void loginConClaveSeguraNoPideNada() {
        loginValido("Comedor#Rio27");
        var respuesta = service.login(new LoginRequestDTO(usuario.getCorreo(), "Comedor#Rio27"));
        assertNull(respuesta.getDebeCambiarClave());
    }

    @Test
    void cambiarClaveGuardaLaNuevaEInvalidaLasOtrasSesiones() {
        loginValido("12345678");
        when(usuarios.buscarParaActualizar(1L)).thenReturn(Optional.of(usuario));
        when(encoder.encode("Comedor#Rio27")).thenReturn("hash-nuevo");
        when(jwt.generarToken(usuario)).thenReturn("token-nuevo");
        var respuesta = service.cambiarClave(usuario.getCorreo(), new CambiarClaveRequestDTO("12345678", "Comedor#Rio27"));
        assertEquals("hash-nuevo", usuario.getClaveHash());
        assertEquals(5L, usuario.getTokenVersion());
        assertEquals("token-nuevo", respuesta.getToken());
    }

    @Test
    void cambiarClaveRechazaLaActualIncorrectaYLaNuevaInsegura() {
        when(usuarios.findByCorreoIgnoreCase(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        when(usuarios.buscarParaActualizar(1L)).thenReturn(Optional.of(usuario));
        when(encoder.matches("otra", "hash-actual")).thenReturn(false);
        when(encoder.matches("12345678", "hash-actual")).thenReturn(true);
        when(empleados.findById(2L)).thenReturn(Optional.of(empleado));
        var actualIncorrecta = assertThrows(DatosInvalidosException.class,
                () -> service.cambiarClave(usuario.getCorreo(), new CambiarClaveRequestDTO("otra", "Comedor#Rio27")));
        assertEquals("La contraseña actual no es correcta", actualIncorrecta.getMessage());
        assertThrows(DatosInvalidosException.class,
                () -> service.cambiarClave(usuario.getCorreo(), new CambiarClaveRequestDTO("12345678", "Lucia#2026x")));
        assertEquals("hash-actual", usuario.getClaveHash());
        assertEquals(4L, usuario.getTokenVersion());
        verify(encoder, never()).encode(any());
    }
}
