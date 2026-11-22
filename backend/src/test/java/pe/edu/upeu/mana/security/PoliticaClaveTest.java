package pe.edu.upeu.mana.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.upeu.mana.exception.DatosInvalidosException;

class PoliticaClaveTest {

    private static boolean cumple(String clave) {
        return PoliticaClave.cumple(clave, "lucia.quispe@mana.pe", "70000001", "Lucía", "Quispe Mamani");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Comedor#Rio27", "Tarde.Lluviosa9", "m4ng0-Verde!", "Sol$Andino2026" })
    void aceptaContraseñasConLetrasNumerosYSimbolo(String clave) {
        assertTrue(cumple(clave), clave);
    }

    @ParameterizedTest
    @ValueSource(strings = { "12345678", "password", "qwerty123", "Password123!", "Mana2026$", "Admin12345", "Dueno12345" })
    void rechazaLasContraseñasDelEjemploDelDocenteYLasComunes(String clave) {
        assertFalse(cumple(clave), clave);
    }

    @Test
    void exigeLongitudLetraNumeroYSimbolo() {
        assertTrue(PoliticaClave.problemas("Ab1!", null, null, null, null).contains("Debe tener al menos 8 caracteres"));
        assertTrue(PoliticaClave.problemas("12345678!", null, null, null, null).contains("Debe incluir al menos una letra"));
        assertTrue(PoliticaClave.problemas("Cielo!Azul", null, null, null, null).contains("Debe incluir al menos un número"));
        assertTrue(PoliticaClave.problemas("CieloAzul27", null, null, null, null)
                .contains("Debe incluir al menos un símbolo (por ejemplo ! @ # $ % . -)"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Rio-aaaa-27", "Rio-1111-Sol", "Clave#12345", "Rio#abcdeX9", "Sol!54321x" })
    void rechazaSecuenciasYRepeticiones(String clave) {
        assertFalse(cumple(clave), clave);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Lucia#2026x", "Quispe!Rio9", "X70000001!a", "lucia.quispe1!" })
    void rechazaContraseñasConDatosPersonales(String clave) {
        assertFalse(cumple(clave), clave);
    }

    @Test
    void validarLanzaUnErrorConTodosLosMotivos() {
        var error = assertThrows(DatosInvalidosException.class, () -> PoliticaClave.validar("12345678", null, null, null, null));
        assertTrue(error.getMessage().startsWith("Contraseña insegura:"));
        assertTrue(error.getMessage().contains("demasiado común"));
        assertTrue(error.getMessage().contains("símbolo"));
    }

    @Test
    void rechazaMasDe72Bytes() {
        assertFalse(cumple("Ñ".repeat(37) + "1!"));
    }
}
