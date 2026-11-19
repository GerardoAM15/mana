# Arquitectura

## Visión general

```
SPA Angular (frontend/)  ──HTTP/JSON + JWT──▶  API Spring Boot (backend/)  ──JDBC──▶  Oracle
                                                    │
                                                    ├──▶ APIsPERU (consulta DNI/RUC)
                                                    ├──▶ APIsPERU Facturación (SUNAT)
                                                    └──▶ Gmail SMTP
```

- **Un solo ejecutable** Spring Boot (`mana-backend`) ensambla todos los módulos.
- **Una sola SPA** Angular organizada en `core` (servicios transversales, guards, interceptores), `shared`
  (componentes reutilizables), `layout` y `features` (un directorio por módulo funcional).

## Capas del backend

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Controlador | `controller` | Recibe la petición REST, valida el DTO y delega |
| Servicio | `service`, `service.impl` | Reglas de negocio y transacciones |
| Repositorio | `repository` | Acceso a datos con Spring Data JPA |
| Entidad | `entity` | Mapeo de las tablas Oracle |
| DTO / Mapper | `dto`, `mapper` | Contratos de entrada y salida de la API |
| Seguridad | `security`, `config` | JWT, permisos por rol, CORS, filtros |

## Seguridad

- Autenticación con **JWT** propio (`JwtService`, `JwtAuthenticationFilter`).
- Autorización por **permisos** derivados del rol (`PermisosPorRol`): Dueño > Administrador > Cajero.
- En el frontend, las rutas se protegen con guards y el interceptor agrega el token y maneja 401/403.

## Decisiones de arquitectura

Las decisiones (ADR) se documentan en el curso ADS. La más compleja es la **ADR-7, integración con sistemas
externos**: el envío a SUNAT ocurre **después del commit** de la venta (`@TransactionalEventListener(AFTER_COMMIT)`)
y se reintenta cada 5 minutos (`@Scheduled`), de modo que una caída de SUNAT nunca bloquea al cajero.
