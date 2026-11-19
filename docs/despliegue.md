# Ejecución y despliegue

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21 |
| Node.js | 20 o superior |
| Oracle Database | 19c o 21c XE |

## 1. Base de datos

Ejecutar, en este orden, los scripts de `backend/docs/`:

1. `mana-hibrido-v4.sql` (esquema base)
2. `mana-v5-comprobantes.sql` (comprobantes electrónicos)
3. `mana-v6-comprobante-correo.sql` (correo del comprobante)
4. Opcional: `mana-datos-ejemplo.sql` (datos de prueba)

La aplicación **solo valida** el esquema (`ddl-auto: validate`); no crea ni modifica tablas.

## 2. Backend

```powershell
cd backend
copy application-local.example.yaml application-local.yaml   # completar contraseñas y tokens
.\mvnw.cmd spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Swagger (solo perfil `dev`): `http://localhost:8080/swagger-ui.html`
- Salud: `http://localhost:8080/api/health` y `http://localhost:8080/actuator/health`

## 3. Frontend

```powershell
cd frontend
npm install
npm start
```

Abrir `http://localhost:4200`. El proxy envía `/api` al backend en el puerto 8080.

## Configuración por ambientes

| Perfil | Archivo | Uso |
|---|---|---|
| `dev` (predeterminado) | `application-dev.yaml` | Desarrollo local, Swagger activo, logs legibles |
| `prod` | `application-prod.yaml` | Producción: credenciales solo por variables de entorno, Swagger apagado, logs en JSON |

Se elige con `SPRING_PROFILES_ACTIVE`. Ningún secreto se guarda en el repositorio: contraseñas, `JWT_SECRET`,
tokens de APIsPERU y la clave de Gmail se pasan por variables de entorno o por `application-local.yaml`
(ignorado por Git). La lista completa de variables está en `backend/README.md`.

## Build de producción

```powershell
cd backend;  .\mvnw.cmd clean package        # genera target/mana-backend-1.0.0.jar
cd frontend; npm run build                   # genera dist/mana_frontend/browser
```
