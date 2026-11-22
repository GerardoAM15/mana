import { ChartConfiguration } from 'chart.js';
import { TestBed } from '@angular/core/testing';
import { color, configurar, DIBUJAR_GRAFICO, FilaGrafico, formatear, Grafico, Serie } from './grafico';

const series: Serie[] = [
  { nombre: 'Pensiones', tono: 'primario' },
  { nombre: 'Ventas', tono: 'acento' },
];
const filas: FilaGrafico[] = [
  { etiqueta: 'Ene', valores: [100, 40] },
  { etiqueta: 'Feb', valores: [60, 20] },
];

describe('configurar', () => {
  it('barras: una serie por dataset y etiquetas por fila', () => {
    const c = configurar('barras', series, filas, true);
    expect(c.type).toBe('bar');
    expect(c.data.labels).toEqual(['Ene', 'Feb']);
    expect(c.data.datasets.map(d => d.data)).toEqual([[100, 60], [40, 20]]);
    expect(c.options?.indexAxis).toBe('x');
  });

  it('horizontal usa el eje y como categorías', () => {
    const c = configurar('horizontal', series.slice(0, 1), filas, false);
    expect(c.options?.indexAxis).toBe('y');
    expect(c.options?.plugins?.legend?.display).toBe(false);
  });

  it('linea dibuja la evolución', () => {
    expect(configurar('linea', series, filas, true).type).toBe('line');
  });

  it('dona suma cada serie en todo el periodo', () => {
    const c = configurar('dona', series, filas, true);
    expect(c.type).toBe('doughnut');
    expect(c.data.labels).toEqual(['Pensiones', 'Ventas']);
    expect(c.data.datasets[0].data).toEqual([160, 60]);
  });

  it('el tooltip muestra montos en soles', () => {
    const c = configurar('barras', series, filas, true) as ChartConfiguration<'bar'>;
    const label = c.options!.plugins!.tooltip!.callbacks!.label as unknown as (x: object) => string;
    expect(label({ dataset: { label: 'Ventas' }, label: 'Ene', raw: 40 })).toMatch(/Ventas: S\/\s?40\.00/);
    const dona = configurar('dona', series, filas, false) as ChartConfiguration<'doughnut'>;
    const labelDona = dona.options!.plugins!.tooltip!.callbacks!.label as unknown as (x: object) => string;
    expect(labelDona({ dataset: {}, label: 'Pensiones', raw: 160 })).toBe('Pensiones: 160');
  });
});

describe('utilidades', () => {
  it('formatear soles o enteros', () => {
    expect(formatear(1500, false)).toBe('1,500');
    expect(formatear(12.5, true)).toMatch(/S\/\s?12\.50/);
  });

  it('color toma la variable del tema o un respaldo', () => {
    const raiz = document.createElement('div');
    raiz.style.setProperty('--color-exito', '#123456');
    document.body.appendChild(raiz);
    expect(color('exito', raiz)).toBe('#123456');
    expect(color('desconocido', raiz)).toBe('#5b1f2e');
    raiz.remove();
  });
});

describe('Grafico', () => {
  const dibujo = { update: vi.fn(), destroy: vi.fn(), data: {}, options: {} } as never as {
    update: ReturnType<typeof vi.fn>;
    destroy: ReturnType<typeof vi.fn>;
    data: unknown;
  };
  const dibujar = vi.fn().mockReturnValue(dibujo);

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: DIBUJAR_GRAFICO, useValue: dibujar }] });
  });

  function crear(f: FilaGrafico[]) {
    const fixture = TestBed.createComponent(Grafico);
    fixture.componentRef.setInput('series', series);
    fixture.componentRef.setInput('filas', f);
    fixture.componentRef.setInput('titulo', 'Ingresos');
    fixture.detectChanges();
    return fixture;
  }

  it('dibuja en un canvas accesible y actualiza sin recrear', () => {
    const fixture = crear(filas);
    const canvas = fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement;
    expect(fixture.nativeElement.querySelector('[role="img"]').getAttribute('aria-label')).toBe('Ingresos');
    expect(dibujar).toHaveBeenCalledTimes(1);
    expect(dibujar.mock.calls[0][0]).toBe(canvas);

    fixture.componentRef.setInput('filas', [{ etiqueta: 'Mar', valores: [5, 5] }]);
    fixture.detectChanges();
    expect(dibujar).toHaveBeenCalledTimes(1);
    expect(dibujo.update).toHaveBeenCalled();
    expect((dibujo.data as ChartConfiguration['data']).labels).toEqual(['Mar']);
  });

  it('sin datos muestra un mensaje y libera el gráfico anterior', () => {
    const fixture = crear(filas);
    fixture.componentRef.setInput('filas', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay datos para graficar.');
    expect(dibujo.destroy).toHaveBeenCalled();

    fixture.componentRef.setInput('filas', filas);
    fixture.detectChanges();
    expect(dibujar).toHaveBeenCalledTimes(2);
  });

  it('se destruye con el componente', () => {
    const fixture = crear(filas);
    fixture.destroy();
    expect(dibujo.destroy).toHaveBeenCalled();
  });
});
