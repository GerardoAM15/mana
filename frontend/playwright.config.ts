import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas E2E (punta de la pirámide): un navegador real usa la aplicación como lo haría el usuario.
 *
 * - Levanta el frontend con `ng serve` (o reutiliza uno que ya esté corriendo en el puerto 4200).
 * - Las pruebas que inician sesión necesitan el backend corriendo (puerto 8080) y las variables
 *   E2E_CORREO y E2E_CLAVE con un usuario de prueba. Si faltan, esas pruebas se marcan como omitidas.
 *
 * Ejecutar:  npx.cmd playwright test        (reporte: npx.cmd playwright show-report)
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npx ng serve --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
