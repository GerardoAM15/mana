import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { PaginaResponse } from '../../../../core/models/pagina-response';
import { hoy, primerDiaMes } from '../../../../core/utils/fechas';
import { mensajeError } from '../../../../core/utils/http-error';
import { Paginador } from '../../../../shared/components/paginador/paginador';
import { LocalService } from '../../../locales/services/local-service';
import { ACCIONES_AUDITORIA, AccionAuditoria, EventoAuditoria, FiltroAuditoria } from '../../models/auditoria.model';
import { AuditoriaService } from '../../services/auditoria-service';

const TAMANIO = 20;

// Historial de operaciones críticas. El dueño ve todos los locales (o elige uno);
// el administrador ve solo el suyo: el backend lo fuerza aunque se pida otro.
@Component({
  selector: 'app-auditoria-list',
  imports: [ReactiveFormsModule, DatePipe, Paginador],
  templateUrl: './auditoria-list.html',
  styleUrl: './auditoria-list.css',
})
export class AuditoriaList implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auditoria = inject(AuditoriaService);
  private readonly localService = inject(LocalService);
  private readonly auth = inject(AuthService);

  protected readonly acciones = ACCIONES_AUDITORIA;
  protected readonly esDueno = computed(() => this.auth.usuario()?.rol === 'DUENO');
  protected readonly locales = signal<{ id: number; nombre: string }[]>([]);
  protected readonly resultado = signal<PaginaResponse<EventoAuditoria> | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly filtros = this.fb.group({
    localId: this.fb.control<number | null>(null),
    accion: this.fb.control<AccionAuditoria | ''>(''),
    usuario: [''],
    desde: [primerDiaMes(hoy())],
    hasta: [hoy()],
  });

  ngOnInit(): void {
    const sesion = this.auth.usuario();
    if (this.esDueno()) {
      this.localService.listar().subscribe({
        next: locales => this.locales.set(locales.map(l => ({ id: l.id, nombre: l.nombre }))),
        error: () => undefined,
      });
    } else if (sesion) {
      this.locales.set([{ id: sesion.localId, nombre: sesion.localNombre }]);
    }
    this.buscar(0);
  }

  buscar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);
    this.auditoria.buscar(this.filtro(), pagina, TAMANIO).subscribe({
      next: resultado => {
        this.resultado.set(resultado);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeError(err));
        this.cargando.set(false);
      },
    });
  }

  limpiar(): void {
    this.filtros.reset({ localId: null, accion: '', usuario: '', desde: primerDiaMes(hoy()), hasta: hoy() });
    this.buscar(0);
  }

  protected etiquetaAccion(accion: AccionAuditoria): string {
    return this.acciones.find(a => a.valor === accion)?.etiqueta ?? accion;
  }

  protected nombreLocal(id: number): string {
    return this.locales().find(l => l.id === id)?.nombre ?? `Local ${id}`;
  }

  private filtro(): FiltroAuditoria {
    const v = this.filtros.getRawValue();
    return {
      localId: this.esDueno() ? v.localId : null,
      accion: v.accion || null,
      usuario: v.usuario.trim() || null,
      desde: v.desde || null,
      hasta: v.hasta || null,
    };
  }
}
