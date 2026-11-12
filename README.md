# MANÁ

Sistema web de gestión de pensiones prepagadas, consumos, punto de venta, caja, menú, inventario, gastos, reportes y comprobantes electrónicos (SUNAT) para un restaurante con varios locales.

Proyecto integrador del curso Análisis y Diseño de Sistemas · Ingeniería de Sistemas · Universidad Peruana Unión, campus Lima · 2026-2.

## Estructura

| Carpeta | Contenido | Tecnología |
|---|---|---|
| [`backend/`](backend) | API REST | Java 21, Spring Boot 4, Oracle Database |
| [`frontend/`](frontend) | Aplicación web | Angular 22 |

Cada carpeta tiene su propio README con los pasos para ejecutarla:

- [backend/README.md](backend/README.md): base de datos, variables de entorno y arranque de la API.
- [frontend/README.md](frontend/README.md): instalación y arranque de la aplicación web.

## Ejecutar en local

1. Crear el esquema Oracle con los scripts de `backend/docs` (ver el README del backend).
2. Levantar la API desde `backend/` (`.\mvnw.cmd spring-boot:run`), en `http://localhost:8080`.
3. Levantar la web desde `frontend/` (`npm install` y `npm start`), en `http://localhost:4200`.

## Equipo y módulos

Proyecto desarrollado en conjunto por los cinco integrantes. Cada commit tiene como autor al responsable del módulo y como coautores al resto del equipo.

| Integrante | Módulo | Casos de uso |
|---|---|---|
| Gerardo Alarcon | Estructura base, ventas y facturación | CU_07, CU_08, CU_09, CU_26 y consulta DNI/RUC |
| Michael Rodriguez | Pensiones | CU_01 a CU_06 |
| Jyon Yefer Huaccha Callocsa | Menú y planes | CU_11 a CU_14, CU_22, CU_23 |
| Ian Saul Sucapuca Espinoza | Seguridad y caja | CU_10, CU_18 a CU_20, CU_24 |
| Jair Apaza | Inventario y reportes | CU_15 a CU_17, CU_21, CU_25 |
