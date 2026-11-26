// En la web, el frontend y la API van en el mismo origen (/api/v1).
// En la app de escritorio (Tauri) cada caja guarda la dirección del servidor de la red local.
export const CLAVE_SERVIDOR = 'mana.servidor';
const API = '/api/v1';

export function esEscritorio(): boolean {
  return '__TAURI_INTERNALS__' in globalThis;
}

export function servidorGuardado(): string | null {
  try {
    return localStorage.getItem(CLAVE_SERVIDOR);
  } catch {
    return null;
  }
}

// "192.168.1.10:8080" → "http://192.168.1.10:8080"; quita la barra final.
export function normalizarServidor(texto: string): string {
  let url = texto.trim();
  if (!/^https?:\/\//i.test(url)) url = `http://${url}`;
  while (url.endsWith('/')) url = url.slice(0, -1);
  return url;
}

export function guardarServidor(url: string): void {
  localStorage.setItem(CLAVE_SERVIDOR, normalizarServidor(url));
}

export function apiUrl(): string {
  const servidor = esEscritorio() ? servidorGuardado() : null;
  return servidor ? `${servidor}${API}` : API;
}
