# MANÁ

Sistema web para un restaurante con varios locales: pensiones prepagadas, consumos diarios, punto de venta, caja,
menú, inventario de bebidas, gastos, reportes y comprobantes electrónicos SUNAT.

Proyecto integrador de Ingeniería de Sistemas, Universidad Peruana Unión, campus Lima, 2026-2. El mismo dominio se
trabaja en tres cursos:

| Curso | Qué cubre en MANÁ |
|---|---|
| **ADS** | Requerimientos (26 casos de uso), iteraciones, diagramas UML y decisiones de arquitectura (ADR) |
| **BD2** | Esquema Oracle, usuarios y roles, indexación y particionamiento medidos con 1 000 000 de filas |
| **LP2** | Backend Spring Boot y frontend Angular integrados |

## Repositorio

| Carpeta | Contenido |
|---|---|
| `backend/` | API REST (Java 21, Spring Boot 4, Oracle) |
| `frontend/` | SPA (Angular 22) |
| `docs/` | Esta documentación (MkDocs) |

## Módulos y responsables

| Módulo | Casos de uso | Responsable |
|---|---|---|
| Seguridad y caja | CU_10, CU_18 a CU_20, CU_24 | Ian Saul Sucapuca Espinoza |
| Menú y planes | CU_11 a CU_14, CU_22, CU_23 | Jyon Yefer Huaccha Callocsa |
| Pensiones | CU_01 a CU_06 | Michael Rodriguez |
| Ventas y facturación | CU_07 a CU_09, CU_26 y consulta DNI/RUC | Gerardo Alarcon |
| Inventario y reportes | CU_15 a CU_17, CU_21, CU_25 | Jair Apaza |

## Alcance de la documentación

- **Funcionalidad obligatoria:** los 26 casos de uso implementados en backend y frontend.
- **Preparado / opcional:** la facturación electrónica funciona en el ambiente BETA de SUNAT y se activa con
  `FACTURACION_HABILITADA=true`; Redis no se usa (ver [Optimización y caché](optimizacion.md)).
