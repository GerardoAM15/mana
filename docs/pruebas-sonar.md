# Pirámide de pruebas, SonarQube y ciclo de corrección

## Pirámide de pruebas

Muchas pruebas rápidas en la base, menos de integración en el medio y pocas end-to-end en la punta.

```
                 ▲   E2E (Playwright)              5   navegador real → frontend → API
                ▲▲▲  Integración (Oracle real)     28  backend + base de datos
             ▲▲▲▲▲▲▲ Unitarias                     253 backend (JUnit 5 + Mockito)
                                                   102 frontend (Vitest)
```

| Nivel | Qué verifica | Herramienta | Cantidad | Cómo se ejecuta |
|---|---|---|---|---|
| **Unitarias** | Una clase o componente aislado; las dependencias se reemplazan por *mocks* | JUnit 5, Mockito, Vitest | 253 + 102 | `.\mvnw.cmd test` · `npm test` |
| **Integración** | El backend contra Oracle real: tablas, constraints, bloqueos, transacciones, login HTTP | Spring Boot Test | 28 | `$env:MANA_ORACLE_TEST="true"` y luego `.\mvnw.cmd test` |
| **E2E** | Un navegador usa la aplicación como el usuario | Playwright | 5 | `npm run e2e` |

Las de integración necesitan la base de datos y el usuario dueño de prueba
(`MANA_ADMIN_EMAIL`, `MANA_ADMIN_PASSWORD`); si `MANA_ORACLE_TEST` no está definida, se omiten.
Las E2E que inician sesión necesitan el backend levantado y `E2E_CORREO` / `E2E_CLAVE`.

Ejemplos por nivel:

- **Unitaria:** `CalculadoraTotalesVentaTest` (total e IGV), `VentaServiceImplTest` (no se anula dos veces y se audita),
  `selector-cliente.spec.ts` (no busca si el DNI no tiene 8 dígitos).
- **Integración:** `correlativosConcurrentesConBloqueoRealSinDuplicados` (dos ventas simultáneas no repiten la boleta),
  `compraVentaYDevolucionMantienenElKardexYElStock`, `loginIdentidadYErroresHttp`.
- **E2E:** sin sesión `/ventas` redirige al login; contraseña incorrecta muestra error; el buscador de clientes consulta la API real.

## SonarQube

Análisis estático de bugs, vulnerabilidades, *code smells*, duplicación y cobertura (JaCoCo en el backend, V8 en el frontend).

```powershell
docker run -d --name sonarqube -p 9000:9000 sonarqube:community      # http://localhost:9000

cd backend
.\mvnw.cmd test
.\mvnw.cmd org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar "-Dsonar.host.url=http://localhost:9000" "-Dsonar.token=<token>"

cd ..\frontend
npx.cmd ng test --watch=false --coverage --coverage-reporters=lcov
npx.cmd sonarqube-scanner "-Dsonar.host.url=http://localhost:9000" "-Dsonar.token=<token>"
```

El token se genera en SonarQube (*My Account → Security*) y **no se guarda en el repositorio**.

### Resultado (quality gate *Sonar way*)

| Proyecto | Antes | Después | Gate |
|---|---|---|---|
| Backend | 3 bugs · 1 vulnerabilidad (seguridad **D**) · 2 code smells | 0 · 0 · 0 — calificaciones **A** | ✅ Passed |
| Frontend | 39 bugs (fiabilidad **C**) · 1 code smell | 0 · 0 — calificaciones **A** | ✅ Passed |

Cobertura medida solo con pruebas unitarias: backend 49 %, frontend 55 % de líneas (Vitest).

## Ciclo de corrección

Cada defecto sigue el mismo camino, sin importar quién lo encuentre:

```
 Detectar ──► Registrar ──► Corregir en una rama ──► Prueba de regresión ──► PR + revisión ──► Merge ──► Verificar
 (prueba,     (hallazgo,    (feature/… o            (la prueba falla        (otro integrante   (main)    (re-ejecutar
  Sonar,       causa)        calidad/…)               antes y pasa después)  aprueba)                    pruebas y Sonar)
  usuario)
```

### Casos reales del proyecto

| # | Detectado por | Hallazgo | Corrección | Regresión | Integración |
|---|---|---|---|---|---|
| 1 | Prueba manual de un integrante (F01–F05) | La API aceptaba `1.5` como entero, devolvía 500 en vez de 415/406 y aceptaba fechas inválidas | `JsonConfig`, `FechaConfig`, `GlobalExceptionHandler` | `EntradaHttpTest`, `EntradaHttpOracleIntegrationTest` | PR #3 |
| 2 | SonarQube (java:S9351) | `BigDecimal.equals()` en 3 pruebas: `10.0` ≠ `10.00` por la escala | `compareTo(...) == 0` | Las mismas pruebas | rama `calidad/sonar-e2e` |
| 3 | SonarQube (java:S2699) | Prueba de esquema sin ninguna aserción | Verifica que Hibernate registró las entidades contra Oracle | `arrancaValidandoElEsquemaReal` | rama `calidad/sonar-e2e` |
| 4 | SonarQube (S8786, Java y TS) | Regex de correo con *backtracking* (lenta ante entradas maliciosas) | Validación sin regex, igual en backend y frontend | `validaElFormatoDelCorreoSinExpresionRegular`, `correoValido` | rama `calidad/sonar-e2e` |
| 5 | SonarQube (typescript:S9383) | 4 navegaciones con la promesa ignorada sin indicarlo | `void this.router.navigate(...)` | Build y pruebas del frontend | rama `calidad/sonar-e2e` |
| 6 | SonarQube (Web) | 24 celdas `<th>` sin `scope`, 10 campos sin etiqueta, tabla del ticket sin encabezado | `scope`, `aria-label`, encabezado solo para lectores de pantalla | Re-análisis: 0 hallazgos | rama `calidad/sonar-e2e` |
| 7 | SonarQube (java:S4502) | CSRF desactivado | **Revisado y aceptado**: la API es *stateless* con JWT en la cabecera, sin cookies; se documentó en `SecurityConfig` | — | rama `calidad/sonar-e2e` |
| 8 | Prueba de integración (401 en el login) | Una configuración local fija pisaba la variable `MANA_ADMIN_PASSWORD` | La configuración local usa la variable | Ejecución local con Oracle: 257/257 en verde | configuración local (no se versiona) |
