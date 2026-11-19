# Observabilidad

## Endpoints de salud y monitoreo (Spring Boot Actuator)

| Endpoint | Acceso | Qué muestra |
|---|---|---|
| `GET /api/health` | Público | `Backend funcionando correctamente` (proceso HTTP activo) |
| `GET /actuator/health` | Público | Estado de la aplicación y de sus dependencias, incluida la conexión a Oracle (`UP` / `DOWN`) |
| `GET /actuator/info` | Público | Nombre y descripción de la aplicación |
| `GET /actuator/metrics` | Requiere permiso `USUARIO_GESTIONAR` (Dueño o Administrador) | Métricas: peticiones HTTP, memoria, hilos, conexiones a la base de datos |

`/actuator/health` no muestra detalles internos (`show-details: never`) para no exponer información de la
infraestructura.

Ejemplo de métrica: `GET /actuator/metrics/http.server.requests` devuelve cuántas peticiones atendió la API y
cuánto tardaron.

## Logs estructurados y correlación

- Cada petición recibe un identificador (`requestId`) en el filtro `RequestIdFilter`. Se guarda en el MDC de SLF4J,
  así que **todas las líneas de log de esa petición llevan el mismo id**, y se devuelve al cliente en la cabecera
  `X-Request-Id`. Si el usuario reporta un error, con ese id se encuentran todos los logs relacionados.
- **Perfil `dev`:** logs legibles con el id entre corchetes:

  ```
  INFO [3f2a9c1e-...] p.e.u.m.service.impl.ContratoServiceImpl : Contrato registrado | numero=C-000124 | ...
  ```

- **Perfil `prod`:** logs en **JSON con formato ECS** (`logging.structured.format.console: ecs`), listos para
  herramientas como Elastic, Grafana Loki o CloudWatch. Cada línea incluye fecha, nivel, logger, mensaje y
  `requestId`.
- Los servicios registran inicio y fin de cada operación con su duración (`duracionMs`), y los eventos de negocio
  importantes (venta registrada, contrato anulado, comprobante aceptado o rechazado).
- Nunca se escriben en el log contraseñas, tokens ni el JWT.
