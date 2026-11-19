import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Cliente } from '../../../clientes/models/cliente.model';
import { ClienteService } from '../../../clientes/services/cliente-service';
import { SelectorCliente } from './selector-cliente';

const cliente = (dni: string, activo = true): Cliente => ({
  id: 7, dni, nombre: 'Lucía', apellido: 'Quispe', telefono: null, activo, version: 0,
});

async function crear(resultado: Cliente[]) {
  const buscar = vi.fn().mockReturnValue(of({ contenido: resultado, pagina: 0, tamanio: 5, totalElementos: resultado.length, totalPaginas: 1, ultima: true }));
  await TestBed.configureTestingModule({
    imports: [SelectorCliente],
    providers: [provideRouter([]), { provide: ClienteService, useValue: { buscar } }],
  }).compileComponents();
  const fixture = TestBed.createComponent(SelectorCliente);
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const escribir = (texto: string) => {
    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const pulsarBuscar = () => {
    el.querySelector<HTMLButtonElement>('.busqueda button')!.click();
    fixture.detectChanges();
  };
  return { fixture, el, buscar, escribir, pulsarBuscar, comp: fixture.componentInstance };
}

describe('SelectorCliente', () => {
  it('vincula al cliente activo con el DNI exacto', async () => {
    const { el, buscar, escribir, pulsarBuscar, comp } = await crear([cliente('70000001')]);
    escribir('70000001');
    pulsarBuscar();
    expect(buscar).toHaveBeenCalledWith('70000001', 0, 5);
    expect(comp.cliente()?.id).toBe(7);
    expect(el.textContent).toContain('Lucía Quispe · DNI 70000001');
  });

  it('avisa si no hay un cliente activo con ese DNI y ofrece registrarlo', async () => {
    const { el, escribir, pulsarBuscar, comp } = await crear([cliente('70000001', false)]);
    escribir('70000001');
    pulsarBuscar();
    expect(comp.cliente()).toBeNull();
    expect(el.textContent).toContain('No hay un cliente activo con ese DNI');
    expect(el.querySelector('a')?.textContent).toContain('Registrar cliente');
  });

  it('no busca si el DNI no tiene 8 dígitos', async () => {
    const { el, buscar, escribir, pulsarBuscar } = await crear([]);
    escribir('1234');
    pulsarBuscar();
    expect(buscar).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Ingrese un DNI de 8 dígitos');
  });

  it('quitar deja la venta sin cliente', async () => {
    const { fixture, el, comp } = await crear([]);
    comp.cliente.set(cliente('70000001'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.quitar')!.click();
    fixture.detectChanges();
    expect(comp.cliente()).toBeNull();
    expect(el.querySelector('input')).not.toBeNull();
  });
});
