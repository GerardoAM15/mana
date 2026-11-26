# Versión de escritorio

MANÁ se instala en las cajas como una app de Windows (`MANA_1.0.0_x64-setup.exe`), hecha con [Tauri 2](https://tauri.app/).
La app es solo la interfaz: los datos viven en **un servidor por local**, al que las cajas se conectan por la red.

```
             Red local del restaurante (Wi-Fi o cable)
 ┌──────────────────────────┐
 │ PC servidor              │◄──── Caja 1 (app MANÁ)
 │ - Oracle (Docker)        │◄──── Caja 2 (app MANÁ)
 │ - Backend :8080          │◄──── Navegador (opcional)
 └──────────────────────────┘
```

## 1. Preparar la PC servidor

1. Levantar Oracle y el backend como en [Ejecución y despliegue](despliegue.md).
2. Dejar una **IP fija** en la red (por ejemplo `192.168.1.10`), desde el router o en la configuración de red de Windows.
3. Abrir el puerto 8080 en el firewall de Windows (PowerShell como administrador):

    ```powershell
    New-NetFirewallRule -DisplayName "MANA backend" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
    ```

4. Comprobar desde otra PC de la red que responde: abrir `http://192.168.1.10:8080/api/health` en el navegador.

El backend ya acepta el origen de la app de escritorio (`http://tauri.localhost`), así que no hay que tocar `CORS_ORIGINS`.

## 2. Instalar en cada caja

1. Ejecutar `MANA_1.0.0_x64-setup.exe` y seguir el asistente (si falta WebView2, el instalador lo descarga).
2. Abrir **MANÁ** desde el menú Inicio.
3. La primera vez pide la **dirección del servidor**: escribir `192.168.1.10:8080` y pulsar **Probar y guardar**.
4. Iniciar sesión con el usuario de la caja.

Para cambiar de servidor, usar el enlace **Servidor: … · Cambiar** debajo del formulario de inicio de sesión.

## 3. Generar el instalador

Requisitos (solo en la PC donde se compila): Node.js 20+, [Rust](https://rustup.rs/) y las *Build Tools* de Visual Studio con C++.

```powershell
cd escritorio
npm install
npm run build
```

El comando compila el frontend de Angular y lo empaqueta. El instalador queda en
`escritorio/src-tauri/target/release/bundle/nsis/`.

Para probar sin instalar, con el frontend en modo desarrollo: `npm run dev`.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| "No se pudo conectar con …" | El servidor está apagado, la IP cambió o el firewall bloquea el puerto 8080. |
| Inicia sesión pero las pantallas no cargan | La caja quedó apuntando a otro servidor: cambiarlo desde el login. |
| El instalador no abre | Windows SmartScreen: **Más información → Ejecutar de todas formas** (el instalador no está firmado). |
