import { expect, test } from '@playwright/test';
import { CORREO, entrar, iniciarSesion, requiereUsuario } from './sesion';

test.describe('Acceso al sistema', () => {
  test('sin sesión, una pantalla protegida redirige al login', async ({ page }) => {
    await page.goto('/ventas');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
  });

  test('valida el correo en el navegador antes de enviar', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Correo').fill('no-es-correo');
    await page.getByLabel('Correo').blur();
    await expect(page.getByText('Ingrese un correo válido.')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('con una contraseña incorrecta muestra un error y no entra', async ({ page }) => {
    requiereUsuario();
    await iniciarSesion(page, CORREO, 'clave-incorrecta-e2e');
    await expect(page.locator('.alerta')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('con credenciales válidas entra al inicio y abre una pantalla protegida', async ({ page }) => {
    requiereUsuario();
    await entrar(page);
    await page.goto('/ventas');
    await expect(page).toHaveURL(/\/ventas/);
  });
});
