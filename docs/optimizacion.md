# Optimización y caché

## Lazy loading y code splitting

Cada módulo funcional de la SPA se carga bajo demanda con `loadChildren` / `loadComponent` (21 rutas diferidas).
Angular genera un archivo independiente por módulo, que el navegador descarga solo cuando el usuario entra a esa
pantalla.

### Medición del build (`npm run build`, 19/11/2026)

| Concepto | Tamaño sin comprimir | Transferencia estimada (gzip) |
|---|---|---|
| **Carga inicial** (lo que se descarga al abrir la app) | **335,98 kB** | **92,68 kB** |
| Total de JavaScript de la aplicación (49 archivos) | 808,1 kB | — |
| Módulo más grande diferido: `menu-routes` | 61,56 kB | 13,07 kB |
| Otros módulos diferidos: inventario 43,47 kB · reportes 36,26 kB · contratos 30,81 kB · caja 27,07 kB · POS 22,59 kB | | |

**Antes / después:** sin code splitting, todo el JavaScript (≈ 808 kB) se descargaría al abrir la aplicación.
Con lazy loading la carga inicial es de ≈ 336 kB, es decir, **≈ 58 % menos**; el resto se descarga solo cuando se
usa cada módulo.

## Caché del navegador

| Recurso | Política | Motivo |
|---|---|---|
| JS y CSS del build | Caché larga e inmutable | Angular agrega un hash al nombre (`outputHashing: all`, p. ej. `main-SSCHAN5H.js`): si el código cambia, cambia el nombre y el navegador descarga el nuevo |
| `index.html` | Sin caché (`no-cache`) | Debe pedirse siempre para que apunte a los archivos con el hash vigente |
| Respuestas de la API | `no-store` | Spring Security añade por defecto `Cache-Control: no-cache, no-store`: los datos de ventas, caja y contratos son sensibles y cambian constantemente |

Ejemplo de configuración del servidor web que sirva la SPA (Nginx):

```nginx
location = /index.html { add_header Cache-Control "no-cache"; }
location ~* \.(js|css|woff2?)$ { add_header Cache-Control "public, max-age=31536000, immutable"; }
```

## Decisión sobre Redis

**Decisión: no se usa Redis en esta versión.**

| Criterio | Situación en MANÁ |
|---|---|
| Volumen | Un restaurante con pocos locales y decenas de usuarios simultáneos, no miles |
| Tipo de datos | Transaccionales (ventas, caja, consumos) que deben leerse siempre actualizados; cachearlos podría mostrar saldos o stock desactualizados |
| Rendimiento medido | En BD2 se midió con 1 000 000 de ventas: con índices B-tree las búsquedas puntuales bajan de ~48 ms a menos de 1 ms. El cuello de botella se resolvió en la base de datos |
| Caché que ya existe | Las consultas DNI/RUC se guardan 12 horas en memoria del backend y la configuración de facturación se cachea en el frontend (`shareReplay`) |
| Costo | Redis agrega un servidor más que instalar, asegurar y monitorear |

**Cuándo se reconsideraría:** si se agregan muchos locales o un catálogo público de alto tráfico (p. ej. el menú
del día visible para clientes), Redis sería útil para cachear datos de solo lectura como el menú y las tarifas.
