import { expect, Page, test } from '@playwright/test';

// Usuario de prueba para las pruebas que entran al sistema. Nunca se escribe en el código:
// se pasa por variables de entorno (ver playwright.config.ts).
export const CORREO = process.env['E2E_CORREO'] ?? '';
export const CLAVE = process.env['E2E_CLAVE'] ?? '';

export function requiereUsuario(): void {
  test.skip(!CORREO || !CLAVE, 'Defina E2E_CORREO y E2E_CLAVE y levante el backend para esta prueba');
}

export async function iniciarSesion(page: Page, correo = CORREO, clave = CLAVE): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Correo').fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

export async function entrar(page: Page): Promise<void> {
  await iniciarSesion(page);
  await expect(page).toHaveURL(/\/inicio$/);
}
