import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { claveGuard } from '../../../../core/guards/clave.guard';
import { CambiarClave } from './cambiar-clave';

function authFalso(debeCambiar: boolean) {
  return {
    debeCambiarClave: signal(debeCambiar),
    usuario: signal({ nombreCompleto: 'Lucía Quispe' }),
    cambiarClave: vi.fn().mockReturnValue(of({})),
    cerrarSesion: vi.fn(),
  };
}

async function crear(debeCambiar = true) {
  const auth = authFalso(debeCambiar);
  await TestBed.configureTestingModule({
    imports: [CambiarClave],
    providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
  }).compileComponents();
  const fixture = TestBed.createComponent(CambiarClave);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const escribir = (id: string, valor: string) => {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  return { fixture, el, auth, escribir };
}

describe('CambiarClave', () => {
  it('explica que el cambio es obligatorio si la clave es débil', async () => {
    const { el } = await crear(true);
    expect(el.textContent).toContain('ya no cumple las reglas de seguridad');
  });

  it('no envía una nueva contraseña insegura', async () => {
    const { el, auth, escribir } = await crear();
    escribir('claveActual', '12345678');
    escribir('nuevaClave', 'Lucia#2026x');
    escribir('confirmacion', 'Lucia#2026x');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(auth.cambiarClave).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Sin su correo, DNI, nombre ni apellido');
  });

  it('envía la clave actual y la nueva, y entra al sistema', async () => {
    const { el, auth, escribir } = await crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    escribir('claveActual', '12345678');
    escribir('nuevaClave', 'Comedor#Rio27');
    escribir('confirmacion', 'Comedor#Rio27');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(auth.cambiarClave).toHaveBeenCalledWith({ claveActual: '12345678', nuevaClave: 'Comedor#Rio27' });
    expect(navegar).toHaveBeenCalledWith('/inicio');
  });
});

describe('claveGuard', () => {
  it('manda a /cambiar-clave solo cuando la clave es débil', async () => {
    for (const [debeCambiar, esperado] of [[true, '/cambiar-clave'], [false, true]] as const) {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: authFalso(debeCambiar) }] });
      const resultado = TestBed.runInInjectionContext(() => claveGuard({} as never, {} as never));
      expect(typeof resultado === 'boolean' ? resultado : TestBed.inject(Router).serializeUrl(resultado as never)).toBe(esperado);
    }
  });
});
