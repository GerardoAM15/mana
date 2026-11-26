import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { Venta } from '../../models/venta.model';
import { VentaService } from '../../services/venta-service';
import { REFRESCO_MS, VentaList } from './venta-list';

const pagina = (contenido: Partial<Venta>[]) =>
  of({ contenido, pagina: 0, tamanio: 20, totalElementos: contenido.length, totalPaginas: 1, ultima: true });

async function crear(entradas: Record<string, string> = {}) {
  const buscar = vi.fn().mockReturnValue(pagina([{ id: 1, numero: 'V-000001', estado: 'PENDIENTE', total: 9, cajaId: 3, servicio: 'A', paraLlevar: false, fecha: '2026-11-21T12:00:00' }]));
  await TestBed.configureTestingModule({
    imports: [VentaList],
    providers: [
      provideRouter([]),
      { provide: VentaService, useValue: { buscar } },
      { provide: CatalogoService, useValue: { cargar: () => of({}), nombreServicio: () => 'Almuerzo' } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(VentaList);
  for (const [k, v] of Object.entries(entradas)) fixture.componentRef.setInput(k, v);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, buscar };
}

describe('VentaList', () => {
  afterEach(() => vi.useRealTimers());

  it('abre en "En curso" pidiendo solo las ventas por cobrar', async () => {
    const { el, buscar } = await crear();
    const filtro = buscar.mock.calls[0][0];
    expect(filtro.estado).toBe('PENDIENTE');
    expect(filtro.historial).toBeUndefined();
    expect(filtro.desde).toBeNull();
    expect(el.textContent).toContain('Ventas por cobrar');
    expect(el.textContent).toContain('Cobrar');
  });

  it('"Historial" pide pagadas y anuladas con el filtro de fechas', async () => {
    const { fixture, el, buscar } = await crear();
    const pestanas = el.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    pestanas[1].click();
    fixture.detectChanges();
    const filtro = buscar.mock.calls.at(-1)![0];
    expect(filtro.historial).toBe(true);
    expect(filtro.estado).toBeNull();
    expect(filtro.desde).toBeTruthy();
    expect(pestanas[1].getAttribute('aria-selected')).toBe('true');
    expect(el.textContent).toContain('Pagadas y anuladas');
  });

  it('el enlace desde "Cerrar caja" con ?estado=PENDIENTE abre "En curso" filtrado por caja', async () => {
    const { buscar } = await crear({ estado: 'PENDIENTE', cajaId: '3' });
    expect(buscar.mock.calls[0][0]).toMatchObject({ estado: 'PENDIENTE', cajaId: 3 });
  });

  it('"En curso" se actualiza sola; "Historial" no', async () => {
    vi.useFakeTimers();
    const { fixture, el, buscar } = await crear();
    vi.advanceTimersByTime(REFRESCO_MS);
    expect(buscar).toHaveBeenCalledTimes(2);
    el.querySelectorAll<HTMLButtonElement>('[role="tab"]')[1].click();
    fixture.detectChanges();
    const llamadas = buscar.mock.calls.length;
    vi.advanceTimersByTime(REFRESCO_MS * 2);
    expect(buscar).toHaveBeenCalledTimes(llamadas);
  });
});
