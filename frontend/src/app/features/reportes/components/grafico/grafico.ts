import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  InjectionToken,
  input,
  viewChild,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';

Chart.register(...registerables);

export interface Serie {
  nombre: string;
  // Color del tema: primario, acento, exito, peligro, info.
  tono: string;
}

export interface FilaGrafico {
  etiqueta: string;
  valores: number[];
}

// barras: verticales agrupadas; horizontal: ranking; linea: evolución; dona: participación de cada serie.
export type TipoGrafico = 'barras' | 'horizontal' | 'linea' | 'dona';

export interface Dibujo {
  update(): void;
  destroy(): void;
  data: ChartConfiguration['data'];
  options: ChartConfiguration['options'];
}

// Se inyecta para poder probar el componente sin un canvas real.
export const DIBUJAR_GRAFICO = new InjectionToken<(canvas: HTMLCanvasElement, config: ChartConfiguration) => Dibujo>(
  'DIBUJAR_GRAFICO',
  { factory: () => (canvas, config) => new Chart(canvas, config) },
);

const RESPALDO: Record<string, string> = {
  primario: '#5b1f2e',
  acento: '#a8844c',
  exito: '#2f6b4f',
  peligro: '#a3382c',
  info: '#3a5a80',
};

const soles = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' });
const enteros = new Intl.NumberFormat('es-PE');

export function color(tono: string, raiz: Element = document.documentElement): string {
  const valor = getComputedStyle(raiz).getPropertyValue(`--color-${tono}`).trim();
  return valor || RESPALDO[tono] || RESPALDO['primario'];
}

export function formatear(valor: number, moneda: boolean): string {
  return moneda ? soles.format(valor) : enteros.format(valor);
}

// Arma la configuración de Chart.js a partir de las series y filas del reporte.
export function configurar(tipo: TipoGrafico, series: Serie[], filas: FilaGrafico[], moneda: boolean): ChartConfiguration {
  const colores = series.map(s => color(s.tono));
  const formato = (v: number | string) => formatear(Number(v), moneda);
  const tooltip = {
    callbacks: {
      label: (c: { dataset: { label?: string }; label: string; raw: unknown }) =>
        `${tipo === 'dona' ? c.label : c.dataset.label}: ${formato(c.raw as number)}`,
    },
  };

  if (tipo === 'dona') {
    return {
      type: 'doughnut',
      data: {
        labels: series.map(s => s.nombre),
        datasets: [{ data: series.map((_, i) => filas.reduce((suma, f) => suma + (f.valores[i] ?? 0), 0)), backgroundColor: colores }],
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' }, tooltip } },
    };
  }

  const linea = tipo === 'linea';
  return {
    type: linea ? 'line' : 'bar',
    data: {
      labels: filas.map(f => f.etiqueta),
      datasets: series.map((s, i) => ({
        label: s.nombre,
        data: filas.map(f => f.valores[i] ?? 0),
        backgroundColor: colores[i],
        borderColor: colores[i],
        borderRadius: linea ? 0 : 4,
        tension: 0.3,
        pointRadius: 3,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: tipo === 'horizontal' ? 'y' : 'x',
      plugins: { legend: { display: series.length > 1, position: 'bottom' }, tooltip },
      scales: {
        [tipo === 'horizontal' ? 'x' : 'y']: { beginAtZero: true, ticks: { callback: formato } },
      },
    },
  };
}

@Component({
  selector: 'app-grafico',
  templateUrl: './grafico.html',
  styleUrl: './grafico.css',
})
export class Grafico {
  private readonly dibujar = inject(DIBUJAR_GRAFICO);
  private readonly lienzo = viewChild<ElementRef<HTMLCanvasElement>>('lienzo');

  readonly tipo = input<TipoGrafico>('barras');
  readonly series = input.required<Serie[]>();
  readonly filas = input.required<FilaGrafico[]>();
  readonly moneda = input(false);
  readonly titulo = input('Gráfico del reporte');

  private grafico: Dibujo | null = null;
  private canvas: HTMLCanvasElement | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.grafico?.destroy());
    afterRenderEffect(() => {
      const canvas = this.lienzo()?.nativeElement;
      const config = configurar(this.tipo(), this.series(), this.filas(), this.moneda());
      // Sin datos el canvas desaparece; al volver es otro elemento y se dibuja de nuevo.
      if (canvas !== this.canvas) {
        this.grafico?.destroy();
        this.grafico = null;
        this.canvas = canvas;
      }
      if (!canvas) return;
      if (this.grafico) {
        this.grafico.data = config.data;
        this.grafico.options = config.options;
        this.grafico.update();
      } else {
        this.grafico = this.dibujar(canvas, config);
      }
    });
  }
}
