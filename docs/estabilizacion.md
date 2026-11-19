# Bitácora de estabilización

Errores reales encontrados al probar el sistema, cómo se corrigieron y qué prueba evita que vuelvan
(prueba de no regresión).

| # | Hallazgo | Prioridad | Causa | Corrección | Prueba de no regresión |
|---|---|---|---|---|---|
| 1 | La boleta B001-1 quedaba **"Con error"** y nunca llegaba a SUNAT | Alta | El backend enviaba `fechaEmision` con nanosegundos y APIsPERU respondía 400 | `ComprobantePayloadMapper.fechaSunat` trunca a segundos y usa el formato `yyyy-MM-dd'T'HH:mm:ssXXX`; además se muestra el mensaje exacto de APIsPERU en vez de un error genérico | `EnvioComprobanteTest.fechaSinFraccionNiSegundosOmitidos`, `error400MuestraElMensajeDeApisPeru` |
| 2 | Boletas **aceptadas por SUNAT** aparecían como **RECHAZADO** | Alta | El CDR de APIsPERU no siempre trae el campo `accepted` y se tomaba como falso | La aceptación se decide por el código del CDR: `0` o `≥ 4000` aceptado (OBSERVADO si hay notas), `2000–3999` rechazado | `EnvioComprobanteTest.cdrSinAcceptedSeDecidePorCodigo` |
| 3 | SUNAT devolvía las **observaciones 4096 a 4098** | Media | Faltaban departamento, provincia y distrito en la dirección del emisor | Nuevas variables `FACTURACION_EMPRESA_DEPARTAMENTO/PROVINCIA/DISTRITO/COD_LOCAL` que se envían en el comprobante | `EnvioComprobanteTest.enviaDepartamentoProvinciaYDistritoDelEmisor` |
| 4 | Riesgo de **reintentos infinitos o duplicados** cuando SUNAT ya tenía el comprobante | Media | Si se perdía la respuesta, SUNAT respondía `1033` (ya registrado) en el reintento | Ante 1033 no se reenvía: se consulta el CDR con `GET /invoice/status` y se aplica; sin CDR se detienen los reintentos | `yaRegistradoConsultaElCdrYLoAplica`, `yaRegistradoSinCdrDejaDeReintentar` |
| 5 | Los textos de la interfaz mostraban **"DUENO"** | Baja | Se mostraba el código interno del rol | Los mensajes y la documentación muestran "Dueño"; el código interno no cambia | Revisión en el PR #1 de los repositorios anteriores |
| 6 | **Logs imposibles de relacionar** entre sí cuando varios usuarios trabajan a la vez | Media | No había un identificador por petición | `RequestIdFilter` agrega un `requestId` a todos los logs de la petición y lo devuelve en `X-Request-Id` | `RequestIdFilterTest` (3 casos) |
| 7 | Consultas de reportes **lentas con mucho volumen** (hallazgo de BD2) | Media | Sin índices adecuados Oracle leía la tabla completa (`TABLE ACCESS FULL`) | Índices B-tree por `(local_id, fecha)` y por caja, y particionamiento mensual recomendado (benchmark de 1 000 000 de ventas en AWS) | Medición de BD2: de ~48 ms a menos de 1 ms en búsquedas puntuales |

!!! note "Cómo agregar un hallazgo"
    Cada integrante registra aquí el error que encontró y corrigió en su módulo, con el enlace a su pull request
    y la prueba que lo cubre.
