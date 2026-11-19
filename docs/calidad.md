# Paginación, auditoría y pruebas

## Paginación

Los listados de alto volumen (ventas, contratos, consumos, cajas, comprobantes, movimientos de stock, clientes)
son paginados en el backend y devuelven `PaginaResponseDTO` con:

| Parámetro / campo | Uso |
|---|---|
| `pagina`, `tamanio` | Página solicitada y cantidad de filas (por defecto 20) |
| `ordenarPor`, `direccion` | Columna y sentido del orden |
| Filtros propios | Fechas, local, estado, DNI, etc. |
| Metadatos de respuesta | Total de elementos, total de páginas, página actual |

El frontend conserva los filtros y el orden al cambiar de página.

## Auditoría

- Todas las entidades transaccionales heredan de `EntidadAuditable`: guardan **quién** creó y modificó el registro
  y **cuándo** (`AuditoriaConfig` toma el usuario autenticado).
- Las operaciones críticas registran además la **acción y el cambio**:
    - anulación de contrato, venta o consumo: estado `ANULADO`, fecha y **motivo** obligatorio (10 a 250 caracteres);
    - cierre de caja: montos esperados, contados y diferencia por método de pago;
    - comprobantes: estado SUNAT, código, mensaje, hash, XML y CDR de cada envío.
- Los eventos se escriben también en el log con el `requestId` (ver [Observabilidad](observabilidad.md)).

## Pruebas automatizadas

| Capa | Herramienta | Cantidad |
|---|---|---|
| Backend | JUnit 5, Mockito, MockRestServiceServer | 257 pruebas (23 de integración con Oracle se omiten si no hay base de datos) |
| Frontend | Vitest | 19 archivos de prueba |

```powershell
cd backend;  .\mvnw.cmd test
cd frontend; npm test
```

Ejemplos de lo que se prueba: cálculo de totales e IGV, reglas de anulación, respuestas de SUNAT (aceptado,
observado, rechazado, caída, reintento y código 1033), envío de correo, permisos por rol y el filtro de `requestId`.

## Plan de pruebas final

| Tipo | Caso | Resultado esperado |
|---|---|---|
| Funcional | Registrar una venta en el POS | Se guarda y aparece en el listado de ventas |
| Seguridad | Llamar a `/api/v1/ventas` sin token | 401; con rol Cajero a `/api/v1/usuarios`: 403 |
| Integración | SPA → API → Oracle al registrar un contrato | Contrato y pagos guardados; la caja refleja el ingreso |
| Validación | Anular un contrato con motivo de 3 letras | 400 con el mensaje del motivo |
| Paginación | Listar ventas y pasar a la página 2 | Se mantienen filtros y orden; metadatos correctos |
| Auditoría | Anular una venta | Queda usuario, fecha, estado ANULADO y motivo |
| Observabilidad | Una operación correcta y un error | Logs con el mismo `requestId`; `/actuator/health` en `UP` |
| End-to-end | Iniciar sesión → abrir caja → vender → consultar | Flujo completo sin errores |
