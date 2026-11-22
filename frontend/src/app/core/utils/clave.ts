import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Mismas reglas que PoliticaClave del backend: si se cambia una, cambiar la otra.
export const CLAVE_MINIMO = 8;
export const CLAVE_MAXIMO_BYTES = 72;

const COMUNES = new Set([
  'password', 'contrasena', 'contrasenia', 'clave', 'secreto', 'qwerty', 'qwertyuiop', 'asdfgh',
  'asdfghjkl', 'zxcvbn', 'zxcvbnm', 'abc', 'abcd', 'abcdef', 'admin', 'administrador', 'root',
  'usuario', 'user', 'login', 'welcome', 'bienvenido', 'hola', 'holamundo', 'iloveyou', 'teamo',
  'letmein', 'dragon', 'monkey', 'football', 'futbol', 'superman', 'batman', 'princesa', 'master',
  'mana', 'comedor', 'upeu', 'peru', 'lima', 'dueno', 'cajero', 'test', 'prueba', 'demo',
  '12345678', '123456789', '1234567890', '87654321', '11111111', '00000000', '123123123',
  'qwerty123', 'password1', 'abc123', 'aa123456', 'passw0rd',
]);

export interface DatosPersonales {
  correo?: string | null;
  dni?: string | null;
  nombre?: string | null;
  apellido?: string | null;
}

export type NivelClave = 'vacia' | 'debil' | 'media' | 'fuerte';

const normalizar = (texto: string) => texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
const esLetra = (c: string) => /\p{L}/u.test(c);
const esDigito = (c: string) => /\p{Nd}/u.test(c);

function esComun(clave: string): boolean {
  const base = normalizar(clave).replace(/[^a-z0-9]/g, '');
  // Se recortan los dígitos de los extremos a mano (sin regex con backtracking, regla S8786 de SonarQube).
  let fin = base.length;
  while (fin > 0 && esDigito(base[fin - 1])) fin--;
  let inicio = 0;
  while (inicio < base.length && esDigito(base[inicio])) inicio++;
  return COMUNES.has(base) || COMUNES.has(base.slice(0, fin)) || COMUNES.has(base.slice(inicio));
}

function tieneRepeticion(clave: string): boolean {
  let seguidos = 1;
  for (let i = 1; i < clave.length; i++) {
    seguidos = clave[i] === clave[i - 1] ? seguidos + 1 : 1;
    if (seguidos >= 4) return true;
  }
  return false;
}

function tieneSecuencia(clave: string): boolean {
  const t = normalizar(clave);
  let sube = 1;
  let baja = 1;
  for (let i = 1; i < t.length; i++) {
    const a = t.charCodeAt(i - 1);
    const b = t.charCodeAt(i);
    const ca = t[i - 1];
    const cb = t[i];
    const mismoTipo = /[\p{L}\p{Nd}]/u.test(ca) && /[\p{L}\p{Nd}]/u.test(cb) && esDigito(ca) === esDigito(cb);
    sube = mismoTipo && b === a + 1 ? sube + 1 : 1;
    baja = mismoTipo && b === a - 1 ? baja + 1 : 1;
    if (sube >= 5 || baja >= 5) return true;
  }
  return false;
}

function contieneDatoPersonal(clave: string, datos: DatosPersonales): boolean {
  const t = normalizar(clave);
  const partes: string[] = [];
  if (datos.correo?.includes('@')) partes.push(datos.correo.slice(0, datos.correo.indexOf('@')));
  if (datos.dni) partes.push(datos.dni);
  for (const texto of [datos.nombre, datos.apellido]) if (texto) partes.push(...texto.trim().split(/\s+/));
  return partes.map(p => normalizar(p).replace(/[^a-z0-9]/g, '')).some(p => p.length >= 3 && t.includes(p));
}

// Lista de reglas que la clave no cumple; vacía si es segura.
export function problemasClave(clave: string, datos: DatosPersonales = {}): string[] {
  if (!clave) return ['La contraseña es obligatoria'];
  const caracteres = [...clave];
  const errores: string[] = [];
  if (caracteres.length < CLAVE_MINIMO) errores.push(`Al menos ${CLAVE_MINIMO} caracteres`);
  if (new TextEncoder().encode(clave).length > CLAVE_MAXIMO_BYTES) errores.push(`Hasta ${CLAVE_MAXIMO_BYTES} bytes`);
  if (!caracteres.some(esLetra)) errores.push('Al menos una letra');
  if (!caracteres.some(esDigito)) errores.push('Al menos un número');
  if (caracteres.every(c => esLetra(c) || esDigito(c))) errores.push('Al menos un símbolo (! @ # $ % . -)');
  if (esComun(clave)) errores.push('No usar una contraseña común');
  if (tieneRepeticion(clave) || tieneSecuencia(clave)) errores.push('Sin secuencias ni repeticiones (1234, abcd, aaaa)');
  if (contieneDatoPersonal(clave, datos)) errores.push('Sin su correo, DNI, nombre ni apellido');
  return errores;
}

export function nivelClave(clave: string, datos: DatosPersonales = {}): NivelClave {
  if (!clave) return 'vacia';
  if (problemasClave(clave, datos).length > 0) return 'debil';
  return [...clave].length >= 12 ? 'fuerte' : 'media';
}

// Validador de formulario. `datos` se lee en cada validación para usar lo que el usuario ya escribió.
export function claveSegura(datos: () => DatosPersonales = () => ({})): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor = (control.value as string | null) ?? '';
    if (!valor) return null; // "required" se valida aparte
    const problemas = problemasClave(valor, datos());
    return problemas.length ? { claveInsegura: problemas } : null;
  };
}
