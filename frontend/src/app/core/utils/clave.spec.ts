import { FormControl } from '@angular/forms';
import { claveSegura, nivelClave, problemasClave } from './clave';

const datos = { correo: 'lucia.quispe@mana.pe', dni: '70000001', nombre: 'Lucía', apellido: 'Quispe Mamani' };

describe('problemasClave', () => {
  it('acepta contraseñas con letras, números y símbolo', () => {
    for (const clave of ['Comedor#Rio27', 'Tarde.Lluviosa9', 'm4ng0-Verde!', 'Sol$Andino2026']) {
      expect(problemasClave(clave, datos)).toEqual([]);
    }
  });

  it('rechaza el ejemplo del docente y las contraseñas comunes', () => {
    for (const clave of ['12345678', 'password', 'qwerty123', 'Password123!', 'Mana2026$', 'Admin12345']) {
      expect(problemasClave(clave, datos).length).toBeGreaterThan(0);
    }
    expect(problemasClave('12345678')).toContain('No usar una contraseña común');
  });

  it('pide longitud, letra, número y símbolo', () => {
    expect(problemasClave('Ab1!')).toContain('Al menos 8 caracteres');
    expect(problemasClave('12345678!')).toContain('Al menos una letra');
    expect(problemasClave('Cielo!Azul')).toContain('Al menos un número');
    expect(problemasClave('CieloAzul27')).toContain('Al menos un símbolo (! @ # $ % . -)');
  });

  it('rechaza secuencias, repeticiones y datos personales', () => {
    for (const clave of ['Rio-aaaa-27', 'Rio#abcdeX9', 'Sol!54321x']) {
      expect(problemasClave(clave, datos)).toContain('Sin secuencias ni repeticiones (1234, abcd, aaaa)');
    }
    for (const clave of ['Lucia#2026x', 'Quispe!Rio9', 'X70000001!a']) {
      expect(problemasClave(clave, datos)).toContain('Sin su correo, DNI, nombre ni apellido');
    }
  });
});

describe('nivelClave y claveSegura', () => {
  it('clasifica la fuerza', () => {
    expect(nivelClave('')).toBe('vacia');
    expect(nivelClave('12345678')).toBe('debil');
    expect(nivelClave('Rio#2026x')).toBe('media');
    expect(nivelClave('Comedor#Rio27')).toBe('fuerte');
  });

  it('el validador deja vacío a "required" y marca las claves inseguras', () => {
    const control = new FormControl('', claveSegura(() => datos));
    expect(control.errors).toBeNull();
    control.setValue('12345678');
    expect(control.errors?.['claveInsegura']).toBeTruthy();
    control.setValue('Comedor#Rio27');
    expect(control.errors).toBeNull();
  });
});
