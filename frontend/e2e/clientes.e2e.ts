import { expect, test } from '@playwright/test';
import { entrar, requiereUsuario } from './sesion';

test.describe('Clientes', () => {
  test('el listado carga y el buscador filtra contra el backend real', async ({ page }) => {
    requiereUsuario();
    await entrar(page);
    await page.goto('/clientes');

    const buscador = page.getByLabel('Buscar clientes');
    await expect(buscador).toBeVisible();

    // Un texto que no coincide con nadie: el backend responde una página vacía.
    const respuesta = page.waitForResponse(r => r.url().includes('/api/v1/clientes') && r.status() === 200);
    await buscador.fill('zzz-sin-coincidencias-e2e');
    await respuesta;
    await expect(page.getByText('No hay registros.')).toBeVisible();
  });
});
