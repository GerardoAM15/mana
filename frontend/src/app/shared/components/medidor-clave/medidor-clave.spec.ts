import { TestBed } from '@angular/core/testing';
import { MedidorClave } from './medidor-clave';

async function crear(clave: string) {
  await TestBed.configureTestingModule({ imports: [MedidorClave] }).compileComponents();
  const fixture = TestBed.createComponent(MedidorClave);
  fixture.componentRef.setInput('clave', clave);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('MedidorClave', () => {
  it('no muestra nada sin contraseña', async () => {
    const el = await crear('');
    expect(el.querySelector('.medidor')).toBeNull();
  });

  it('marca como débil y lista lo que falta', async () => {
    const el = await crear('12345678');
    expect(el.querySelector('.medidor')?.classList).toContain('debil');
    expect(el.textContent).toContain('Débil');
    expect(el.textContent).toContain('Al menos una letra');
  });

  it('marca como fuerte una contraseña segura y larga', async () => {
    const el = await crear('Comedor#Rio27');
    expect(el.querySelector('.medidor')?.classList).toContain('fuerte');
    expect(el.querySelector('.faltantes')).toBeNull();
  });
});
