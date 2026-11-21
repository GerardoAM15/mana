import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PaginaResponse } from '../../../../core/models/pagina-response';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { hoy } from '../../../../core/utils/fechas';
import { mensajeError } from '../../../../core/utils/http-error';
import { EstadoBadge } from '../../../../shared/components/estado-badge/estado-badge';
import { Paginador } from '../../../../shared/components/paginador/paginador';
import { EstadoVenta, FiltroVentas, Venta } from '../../models/venta.model';
import { VentaService } from '../../services/venta-service';

const TAMANIO = 20;
// Cada cuánto se refresca "En curso" para ver lo que registran las otras cajas.
export const REFRESCO_MS = 15_000;

export type Pestana = 'curso' | 'historial';

// "En curso": solo las ventas por cobrar; al cobrarlas salen de esta vista (no se borran).
// "Historial": las pagadas y anuladas, con filtros por fecha, estado y caja.
@Component({
  selector: 'app-venta-list',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, EstadoBadge, Paginador],
  templateUrl: './venta-list.html',
  styleUrl: './venta-list.css',
})
export class VentaList implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly ventaService = inject(VentaService);
  protected readonly catalogos = inject(CatalogoService);

  // ?estado=PENDIENTE&cajaId=... desde "Cerrar caja".
  readonly estado = input<string>();
  readonly cajaId = input<string>();

  protected readonly pestana = signal<Pestana>('curso');
  protected readonly estadosHistorial: EstadoVenta[] = ['PAGADA', 'ANULADA'];
  protected readonly resultado = signal<PaginaResponse<Venta> | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly actualizadoEn = signal<Date | null>(null);
  protected readonly filtros = this.fb.group({
    desde: [hoy()],
    hasta: [hoy()],
    estado: this.fb.control<EstadoVenta | ''>(''),
    cajaId: [''],
  });

  private pagina = 0;
  private readonly temporizador = setInterval(() => this.refrescarEnCurso(), REFRESCO_MS);

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.temporizador));
  }

  ngOnInit(): void {
    this.catalogos.cargar().subscribe({ error: () => undefined });
    const estado = this.estado() as EstadoVenta | undefined;
    const cajaId = this.cajaId() ?? '';
    if (estado && this.estadosHistorial.includes(estado)) {
      this.pestana.set('historial');
      this.filtros.patchValue({ estado, cajaId, desde: '', hasta: '' });
    } else {
      this.filtros.patchValue({ cajaId });
    }
    this.buscar(0);
  }

  cambiarPestana(pestana: Pestana): void {
    if (this.pestana() === pestana) return;
    this.pestana.set(pestana);
    this.resultado.set(null);
    this.buscar(0);
  }

  buscar(pagina = 0, silencioso = false): void {
    this.pagina = pagina;
    if (!silencioso) this.cargando.set(true);
    this.error.set(null);
    this.ventaService.buscar(this.filtro(), pagina, TAMANIO).subscribe({
      next: resultado => {
        this.resultado.set(resultado);
        this.cargando.set(false);
        this.actualizadoEn.set(new Date());
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeError(err));
        this.cargando.set(false);
      },
    });
  }

  limpiar(): void {
    this.filtros.reset({ desde: hoy(), hasta: hoy(), estado: '', cajaId: '' });
    this.buscar(0);
  }

  // Solo se refresca "En curso", sin parpadear la tabla ni cambiar de página.
  private refrescarEnCurso(): void {
    if (this.pestana() === 'curso' && !this.cargando()) this.buscar(this.pagina, true);
  }

  private filtro(): FiltroVentas {
    const v = this.filtros.getRawValue();
    const cajaId = v.cajaId ? Number(v.cajaId) : null;
    if (this.pestana() === 'curso') {
      return { desde: null, hasta: null, estado: 'PENDIENTE', cajaId, localId: null };
    }
    return {
      desde: v.desde || null,
      hasta: v.hasta || null,
      estado: v.estado || null,
      cajaId,
      localId: null,
      historial: true,
    };
  }
}
