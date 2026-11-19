# Checklist final

Estado del producto frente al checklist de la Unidad 3. ✔ cumple · ◐ parcial · ✘ pendiente.

## Producto integrado

| Ítem | Estado | Evidencia |
|---|---|---|
| El producto resuelve el problema definido | ✔ | 26 casos de uso implementados |
| ADS, BD2 y LP2 sobre el mismo dominio | ✔ | MANÁ en los tres cursos |
| Flujo full-stack demostrable | ✔ | SPA → API → Oracle |
| La base Oracle soporta el flujo real | ✔ | `backend/docs/*.sql` |
| Documentación publicada y navegable | ✔ | Este sitio MkDocs |

## Aplicación full-stack

| Ítem | Estado | Evidencia |
|---|---|---|
| Backend REST funcional | ✔ | `backend/`, Swagger |
| Frontend SPA funcional | ✔ | `frontend/` |
| Un único Spring Boot ejecutable | ✔ | `mana-backend-1.0.0.jar` |
| SPA única con `core`, `shared` y funcionalidades | ✔ | `frontend/src/app/{core,shared,features}` |
| Módulos sin compartir repositorios ni llamadas HTTP internas | ◐ | Comunicación por servicios Java; falta verificación automática (Spring Modulith) |
| Seguridad backend/frontend | ✔ | JWT, permisos por rol, guards e interceptor ([Arquitectura](arquitectura.md)) |
| CRUD maestro y dependiente | ✔ | Platos / categorías, planes / servicios |
| Módulo transaccional | ✔ | Venta–DetalleOrden, Contrato–DetalleContrato |
| Consultas y reportes protegidos | ✔ | `/api/v1/reportes/**` con permiso |
| Lazy loading / code splitting con build optimizado | ✔ | [Optimización](optimizacion.md) |
| Caché del navegador y decisión sobre Redis | ✔ | [Optimización](optimizacion.md) |
| Logging estructurado, salud y monitoreo | ✔ | [Observabilidad](observabilidad.md) |
| Paginación de listados de alto volumen | ✔ | [Calidad](calidad.md) |
| Auditoría con actor, fecha, acción y cambio | ◐ | Quién y cuándo en todas las entidades; acción y motivo en anulaciones. Falta una tabla de historial de cambios |
| Pruebas end-to-end, regresión y estabilización | ◐ | Regresión y bitácora listas; faltan pruebas E2E automatizadas en navegador |
| Configuración por ambientes y guía de despliegue | ✔ | [Ejecución y despliegue](despliegue.md) |

## Sustentación

| Ítem | Estado | Evidencia |
|---|---|---|
| Repositorio con topics | ✔ | `github.com/GerardoAM15/mana` |
| Ramas y pull requests por integrante | ◐ | Ramas creadas; cada integrante abre su PR |
| MkDocs actualizado | ✔ | Este sitio |
| Pitch y demo preparados | ◐ | Presentación LP2 en Canva |
| Defensa técnica por integrante | ◐ | Cada uno con su hallazgo en la [bitácora](estabilizacion.md) |
