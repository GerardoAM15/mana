package pe.edu.upeu.mana.security;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import pe.edu.upeu.mana.exception.DatosInvalidosException;

/**
 * Reglas de contraseña segura, iguales a las del frontend (core/utils/clave.ts):
 * 8 a 72 bytes, letras + números + símbolo, sin contraseñas comunes, secuencias,
 * repeticiones ni datos personales del usuario (correo, DNI, nombre o apellido).
 */
public final class PoliticaClave {

    public static final int MINIMO = 8;
    public static final int MAXIMO_BYTES = 72;

    // Contraseñas y palabras base más usadas (se comparan sin símbolos ni números finales).
    private static final Set<String> COMUNES = Set.of(
            "password", "contrasena", "contrasenia", "clave", "secreto", "qwerty", "qwertyuiop", "asdfgh",
            "asdfghjkl", "zxcvbn", "zxcvbnm", "abc", "abcd", "abcdef", "admin", "administrador", "root",
            "usuario", "user", "login", "welcome", "bienvenido", "hola", "holamundo", "iloveyou", "teamo",
            "letmein", "dragon", "monkey", "football", "futbol", "superman", "batman", "princesa", "master",
            "mana", "comedor", "upeu", "peru", "lima", "dueno", "cajero", "test", "prueba", "demo",
            "12345678", "123456789", "1234567890", "87654321", "11111111", "00000000", "123123123",
            "qwerty123", "password1", "abc123", "aa123456", "passw0rd");

    private PoliticaClave() {
    }

    /** Lista de problemas encontrados; vacía si la clave cumple todas las reglas. */
    public static List<String> problemas(String clave, String correo, String dni, String nombre, String apellido) {
        var errores = new ArrayList<String>();
        if (clave == null || clave.isEmpty()) {
            errores.add("La contraseña es obligatoria");
            return errores;
        }
        if (clave.length() < MINIMO)
            errores.add("Debe tener al menos " + MINIMO + " caracteres");
        if (clave.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES)
            errores.add("Admite hasta " + MAXIMO_BYTES + " bytes");
        if (clave.chars().noneMatch(Character::isLetter))
            errores.add("Debe incluir al menos una letra");
        if (clave.chars().noneMatch(Character::isDigit))
            errores.add("Debe incluir al menos un número");
        if (clave.chars().allMatch(Character::isLetterOrDigit))
            errores.add("Debe incluir al menos un símbolo (por ejemplo ! @ # $ % . -)");
        if (esComun(clave))
            errores.add("Es una contraseña demasiado común");
        if (tieneRepeticion(clave) || tieneSecuencia(clave))
            errores.add("No debe tener secuencias ni caracteres repetidos (1234, abcd, aaaa)");
        if (contieneDatoPersonal(clave, correo, dni, nombre, apellido))
            errores.add("No debe contener su correo, DNI, nombre ni apellido");
        return errores;
    }

    public static boolean cumple(String clave, String correo, String dni, String nombre, String apellido) {
        return problemas(clave, correo, dni, nombre, apellido).isEmpty();
    }

    public static void validar(String clave, String correo, String dni, String nombre, String apellido) {
        var errores = problemas(clave, correo, dni, nombre, apellido);
        if (!errores.isEmpty())
            throw new DatosInvalidosException("Contraseña insegura: " + String.join("; ", errores));
    }

    private static String normalizar(String texto) {
        var sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    private static boolean esComun(String clave) {
        String base = normalizar(clave).replaceAll("[^a-z0-9]", "");
        if (COMUNES.contains(base))
            return true;
        // "Password123!" o "Mana2026$" siguen siendo la misma palabra común con números al final.
        int fin = base.length();
        while (fin > 0 && Character.isDigit(base.charAt(fin - 1)))
            fin--;
        int inicio = 0;
        while (inicio < base.length() && Character.isDigit(base.charAt(inicio)))
            inicio++;
        return COMUNES.contains(base.substring(0, fin)) || COMUNES.contains(base.substring(inicio));
    }

    // 4 o más caracteres iguales seguidos: aaaa, 1111, !!!!
    private static boolean tieneRepeticion(String clave) {
        int seguidos = 1;
        for (int i = 1; i < clave.length(); i++) {
            seguidos = clave.charAt(i) == clave.charAt(i - 1) ? seguidos + 1 : 1;
            if (seguidos >= 4)
                return true;
        }
        return false;
    }

    // 5 o más caracteres consecutivos subiendo o bajando: 12345, abcde, 54321, edcba
    private static boolean tieneSecuencia(String clave) {
        String t = normalizar(clave);
        int sube = 1;
        int baja = 1;
        for (int i = 1; i < t.length(); i++) {
            char a = t.charAt(i - 1);
            char b = t.charAt(i);
            boolean mismoTipo = Character.isLetterOrDigit(a) && Character.isLetterOrDigit(b)
                    && Character.isDigit(a) == Character.isDigit(b);
            sube = mismoTipo && b == a + 1 ? sube + 1 : 1;
            baja = mismoTipo && b == a - 1 ? baja + 1 : 1;
            if (sube >= 5 || baja >= 5)
                return true;
        }
        return false;
    }

    private static boolean contieneDatoPersonal(String clave, String correo, String dni, String nombre, String apellido) {
        String t = normalizar(clave);
        var datos = new ArrayList<String>();
        if (correo != null && correo.contains("@"))
            datos.add(correo.substring(0, correo.indexOf('@')));
        if (dni != null)
            datos.add(dni);
        for (String texto : new String[] { nombre, apellido }) {
            if (texto != null)
                datos.addAll(List.of(texto.trim().split("\\s+")));
        }
        return datos.stream().map(PoliticaClave::normalizar).map(d -> d.replaceAll("[^a-z0-9]", ""))
                .anyMatch(d -> d.length() >= 3 && t.contains(d));
    }
}
