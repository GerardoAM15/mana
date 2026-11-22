import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { diaSemanaIso, hoy, inicioSemana, sumarDias } from '../../../../core/utils/fechas';
import { mensajeError } from '../../../../core/utils/http-error';
import { ConfirmarDialogo } from '../../../../shared/components/confirmar-dialogo/confirmar-dialogo';
import { SiPermiso } from '../../../../shared/directives/si-permiso';
import { DiaNoAtencion } from '../../../calendario/models/dia-no-atencion.model';
import { DiaNoAtencionService } from '../../../calendario/services/dia-no-atencion-service';
import { MenuTabs } from '../../components/menu-tabs/menu-tabs';
import { MenuDia } from '../../models/menu.model';
import { Plato } from '../../models/plato.model';
import { ReglaCobro } from '../../models/regla-cobro.model';
import { MenuService } from '../../services/menu-service';
import { PlatoService } from '../../services/plato-service';
import { ReglaCobroService } from '../../services/regla-cobro-service';
import { mensajeClonado, serviciosOmitidos } from '../../utils/clonado';
import { enlaceWhatsApp, mensajeMenuDelDia } from '../../utils/mensaje-whatsapp';

const SERVICIOS = ['D', 'A', 'C'];
type CodigoServicio = 'D' | 'A' | 'C';

interface Celda {
  fecha: string;
  servicio: string;
}

interface GrupoBanco {
  categoria: string;
  platos: Plato[];
}

@Component({
  selector: 'app-menu-semana',
  imports: [ReactiveFormsModule, DatePipe, ConfirmarDialogo, SiPermiso, MenuTabs],
  templateUrl: './menu-semana.html',
  styleUrl: './menu-semana.css',
})
export class MenuSemana implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly menuService = inject(MenuService);
  private readonly platoService = inject(PlatoService);
  private readonly reglaService = inject(ReglaCobroService);
  private readonly diaNoAtencionService = inject(DiaNoAtencionService);
  private readonly auth = inject(AuthService);
  protected readonly catalogos = inject(CatalogoService);

  protected readonly servicios = SERVICIOS;
  protected readonly hoy = hoy();
  protected readonly gestiona = this.auth.tienePermiso('MENU_GESTIONAR');
  protected readonly lunes = signal(inicioSemana(this.hoy));
  protected readonly dias = computed(() => Array.from({ length: 7 }, (_, i) => sumarDias(this.lunes(), i)));
  protected readonly menus = signal<MenuDia[]>([]);
  protected readonly feriados = signal<DiaNoAtencion[]>([]);
  protected readonly platos = signal<Plato[]>([]);
  protected readonly reglas = signal<ReglaCobro[]>([]);
  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  // true si el mensaje es un aviso (p. ej. un servicio omitido al clonar) y no un éxito simple.
  protected readonly mensajeAviso = signal(false);

  private readonly porCelda = computed(() => new Map(this.menus().map(m => [`${m.fecha}|${m.servicio}`, m])));
  private readonly feriadoPorFecha = computed(() => new Map(this.feriados().map(f => [f.fecha, f.motivo])));
  private consultaSemana = 0;

  // Editor de una celda.
  protected readonly editando = signal<Celda | null>(null);
  protected readonly elegidos = signal<Set<number>>(new Set());
  protected readonly guardando = signal(false);
  protected readonly errorModal = signal<string | null>(null);
  protected readonly categoriasDelTurno = computed(() => {
    const celda = this.editando();
    return celda ? this.reglas().find(r => r.servicio === celda.servicio && r.activo)?.componentes.map(c => c.categoria) ?? [] : [];
  });
  protected readonly seleccionFueraDelTurno = computed(() => {
    const permitidas = new Set(this.categoriasDelTurno());
    return [...this.elegidos()].some(id => !this.platos().some(p => p.id === id && p.activo && permitidas.has(p.categoria)));
  });
  protected readonly gruposBanco = computed<GrupoBanco[]>(() => {
    const visibles = this.platos().filter(p => p.activo);
    return this.categoriasDelTurno()
      .map(categoria => ({
        categoria,
        platos: visibles.filter(p => p.categoria === categoria).sort((a, b) => a.nombre.localeCompare(b.nombre)),
      }))
      .filter(g => g.platos.length > 0);
  });

  // Clonar día y copiar segundos a la cena.
  protected readonly clonando = signal(false);
  protected readonly copiando = signal(false);
  protected readonly formClonar = this.fb.group({
    fechaOrigen: [this.hoy, Validators.required],
    fechaDestino: [sumarDias(this.hoy, 1), Validators.required],
    D: [true],
    A: [true],
    C: [true],
  });
  protected readonly formCopiar = this.fb.group({ fecha: [this.hoy, Validators.required] });
  private readonly fechaCopiar = toSignal(this.formCopiar.controls.fecha.valueChanges, { initialValue: this.hoy });
  // Por qué no se puede copiar a la cena en la fecha elegida (null si se puede).
  protected readonly motivoSinCena = computed(() => {
    const fecha = this.fechaCopiar();
    if (!fecha) return null;
    if (this.feriado(fecha)) return `Ese día es feriado (${this.feriado(fecha)}): no se atiende la cena.`;
    if (!this.catalogos.seAtiende('C', diaSemanaIso(fecha))) return 'La cena no se atiende ese día de la semana.';
    return null;
  });

  constructor() {
    // Al cambiar la fecha destino se recalculan los servicios que se pueden clonar.
    this.formClonar.controls.fechaDestino.valueChanges.pipe(takeUntilDestroyed()).subscribe(fecha => {
      this.actualizarServiciosClonar();
      this.cargarFeriado(fecha, () => this.actualizarServiciosClonar());
    });
    this.formCopiar.controls.fecha.valueChanges.pipe(takeUntilDestroyed()).subscribe(fecha => this.cargarFeriado(fecha));
  }

  ngOnInit(): void {
    // Los días de atención vienen de los catálogos: al tenerlos se recalculan los servicios del clon.
    this.catalogos.cargar().subscribe({ next: () => this.actualizarServiciosClonar(), error: () => undefined });
    forkJoin([this.platoService.listar(), this.reglaService.listar()]).subscribe({
      next: ([platos, reglas]) => {
        this.platos.set(platos);
        this.reglas.set(reglas);
      },
      error: (err: HttpErrorResponse) => this.error.set(mensajeError(err)),
    });
    this.cargarSemana();
  }

  cambiarSemana(semanas: number): void {
    this.lunes.update(lunes => sumarDias(lunes, semanas * 7));
    this.cargarSemana();
  }

  irAHoy(): void {
    this.lunes.set(inicioSemana(this.hoy));
    this.cargarSemana();
  }

  cargarSemana(): void {
    const consulta = ++this.consultaSemana;
    const desde = this.lunes();
    const hasta = sumarDias(desde, 6);
    this.cargando.set(true);
    this.error.set(null);
    this.menus.set([]);
    this.feriados.set([]);
    this.menuService.listar({ desde, hasta, servicio: null, localId: null }).subscribe({
      next: menus => {
        if (consulta !== this.consultaSemana) return;
        this.menus.set(menus);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (consulta !== this.consultaSemana) return;
        this.error.set(mensajeError(err));
        this.cargando.set(false);
      },
    });
    // Los feriados exigen CALENDARIO_GESTIONAR; sin ese permiso solo se grisan los días sin atención.
    if (this.auth.tienePermiso('CALENDARIO_GESTIONAR')) {
      this.diaNoAtencionService.listar({ desde, hasta, localId: null }).subscribe({
        next: feriados => { if (consulta === this.consultaSemana) this.feriados.set(feriados); },
        error: () => { if (consulta === this.consultaSemana) this.feriados.set([]); },
      });
    }
  }

  protected menu(fecha: string, servicio: string): MenuDia | undefined {
    return this.porCelda().get(`${fecha}|${servicio}`);
  }

  protected feriado(fecha: string): string | undefined {
    return this.feriadoPorFecha().get(fecha);
  }

  // Grisado si el servicio no se atiende ese día de la semana (p. ej. cena del viernes) o es feriado.
  protected atiende(fecha: string, servicio: string): boolean {
    return this.menu(fecha, servicio)?.atiende !== false
      && this.catalogos.seAtiende(servicio, diaSemanaIso(fecha)) && !this.feriado(fecha);
  }

  abrirEditor(fecha: string, servicio: string): void {
    if (!this.gestiona || !this.atiende(fecha, servicio)) return;
    this.editando.set({ fecha, servicio });
    this.elegidos.set(new Set(this.menu(fecha, servicio)?.platos.map(p => p.platoId) ?? []));
    this.errorModal.set(null);
    if (this.categoriasDelTurno().length === 0) {
      this.errorModal.set('Configure primero un menú público activo con su composición para este turno, en Menú → Precios.');
    } else if (this.seleccionFueraDelTurno()) {
      this.errorModal.set('Esta programación contiene platos inactivos o de categorías que ya no corresponden al turno. Retírelos antes de guardar.');
    }
  }

  alternar(platoId: number): void {
    const plato = this.platos().find(p => p.id === platoId);
    if (!this.elegidos().has(platoId) && (!plato?.activo || !this.categoriasDelTurno().includes(plato.categoria))) return;
    this.elegidos.update(actual => {
      const nuevo = new Set(actual);
      if (nuevo.has(platoId)) nuevo.delete(platoId);
      else nuevo.add(platoId);
      return nuevo;
    });
  }

  retirarPlatosFueraDelTurno(): void {
    const permitidos = new Set(this.gruposBanco().flatMap(g => g.platos.map(p => p.id)));
    this.elegidos.update(ids => new Set([...ids].filter(id => permitidos.has(id))));
    this.errorModal.set(null);
  }

  guardarCelda(): void {
    const celda = this.editando();
    if (!celda) return;
    if (this.categoriasDelTurno().length === 0) {
      this.errorModal.set('Configure primero un menú público activo con su composición para este turno, en Menú → Precios.');
      return;
    }
    if (this.elegidos().size === 0) {
      this.errorModal.set('Seleccione al menos un plato para programar el menú.');
      return;
    }
    if (this.seleccionFueraDelTurno()) {
      this.errorModal.set('Retire los platos inactivos o que no corresponden a las categorías de este turno.');
      return;
    }
    this.ejecutar(
      this.menuService.guardar({ fecha: celda.fecha, servicio: celda.servicio, platoIds: [...this.elegidos()] }),
      () => this.editando.set(null),
      'Menú guardado.',
    );
  }

  abrirClonar(fecha?: string): void {
    const origen = fecha ?? this.hoy;
    this.formClonar.reset({ fechaOrigen: origen, fechaDestino: sumarDias(origen, 1), D: true, A: true, C: true });
    this.errorModal.set(null);
    this.clonando.set(true);
  }

  // Motivo por el que un servicio no se puede clonar a la fecha (null si se puede).
  protected motivoNoAtiende(fecha: string, servicio: string): string | null {
    if (!fecha) return null;
    if (this.feriado(fecha)) return 'feriado';
    if (!this.catalogos.seAtiende(servicio, diaSemanaIso(fecha))) return 'no se atiende ese día';
    return null;
  }

  clonar(): void {
    const v = this.formClonar.getRawValue();
    const servicios = SERVICIOS.filter(s => v[s as CodigoServicio] && !this.motivoNoAtiende(v.fechaDestino, s));
    if (this.formClonar.invalid || servicios.length === 0) {
      this.errorModal.set('Elija las fechas y al menos un servicio que se atienda en la fecha destino.');
      return;
    }
    if (v.fechaOrigen === v.fechaDestino) {
      this.errorModal.set('La fecha destino debe ser distinta de la de origen.');
      return;
    }
    this.ejecutar(
      this.menuService.clonar({ fechaOrigen: v.fechaOrigen, fechaDestino: v.fechaDestino, servicios }),
      () => this.clonando.set(false),
      // El backend puede omitir servicios: se compara lo pedido con lo devuelto.
      clonados => {
        const omitidos = serviciosOmitidos(servicios, clonados).map(s => ({
          nombre: this.catalogos.nombreServicio(s),
          motivo: this.motivoNoAtiende(v.fechaDestino, s) ?? 'no se clonó',
        }));
        this.mensajeAviso.set(omitidos.length > 0);
        return mensajeClonado(omitidos);
      },
    );
  }

  abrirCopiar(fecha?: string): void {
    this.formCopiar.reset({ fecha: fecha ?? this.hoy });
    this.errorModal.set(null);
    this.copiando.set(true);
  }

  copiarACena(): void {
    if (this.formCopiar.invalid || this.motivoSinCena()) return;
    this.ejecutar(
      this.menuService.copiarACena({ fecha: this.formCopiar.getRawValue().fecha }),
      () => this.copiando.set(false),
      'Segundos del almuerzo copiados a la cena.',
    );
  }

  cerrarModales(): void {
    if (this.guardando()) return;
    this.editando.set(null);
    this.clonando.set(false);
    this.copiando.set(false);
  }

  // ---- Compartir el menú del día por WhatsApp (se pega en el grupo o lista de difusión) ----
  protected readonly compartiendo = signal<{ fecha: string; texto: string; base: string } | null>(null);
  protected readonly copiado = signal<string | null>(null);

  // Solo anuncia turnos que siguen habilitados, aunque existan platos guardados.
  protected textoMenu(fecha: string): string | null {
    const disponibles = this.menus().filter(m => m.fecha === fecha && this.atiende(m.fecha, m.servicio));
    return mensajeMenuDelDia(fecha, disponibles, c => this.catalogos.nombreServicio(c), c => this.catalogos.nombreCategoria(c));
  }

  abrirCompartir(fecha: string): void {
    if (!this.textoMenu(fecha)) return;
    const consulta = this.consultaSemana;
    this.compartiendo.set(null);
    this.copiado.set(null);
    // Comprueba el calendario actual también para lectores sin acceso a los cierres.
    this.menuService.listar({ desde: fecha, hasta: fecha, servicio: null, localId: null }).subscribe({
      next: menus => {
        if (consulta !== this.consultaSemana) return;
        this.menus.update(actuales => [...actuales.filter(m => m.fecha !== fecha), ...menus]);
        const texto = this.textoMenu(fecha);
        if (!texto) {
          this.error.set('Ese día no tiene menús disponibles para compartir. Revise los días de atención y cierres.');
          return;
        }
        this.error.set(null);
        this.compartiendo.set({ fecha, texto, base: texto });
      },
      error: (err: HttpErrorResponse) => {
        if (consulta === this.consultaSemana) this.error.set(mensajeError(err));
      },
    });
  }

  // El texto se puede retocar en el cuadro antes de compartirlo.
  editarTextoCompartir(texto: string): void {
    this.compartiendo.update(c => (c ? { ...c, texto } : c));
  }

  async copiarTexto(): Promise<void> {
    const c = this.compartirVigente();
    if (!c) return;
    try {
      await navigator.clipboard.writeText(c.texto);
      this.copiado.set('Texto copiado. Péguelo en el grupo o la lista de difusión de pensionistas.');
    } catch {
      this.copiado.set('No se pudo copiar automáticamente: seleccione el texto y cópielo con Ctrl+C.');
    }
  }

  abrirWhatsApp(): void {
    const c = this.compartirVigente();
    if (!c) return;
    // wa.me abre WhatsApp (app o web) con el texto escrito; el usuario elige el chat y pulsa enviar.
    window.open(enlaceWhatsApp(c.texto), '_blank', 'noopener');
    this.compartiendo.set(null);
  }

  cerrarCompartir(): void {
    this.compartiendo.set(null);
  }

  private compartirVigente() {
    const c = this.compartiendo();
    if (c && this.textoMenu(c.fecha) === c.base) return c;
    if (c) this.error.set('La programación o la atención cambió. Vuelva a abrir el menú para compartir.');
    this.compartiendo.set(null);
    return null;
  }

  // Deshabilita (y desmarca) los servicios que no se atienden en la fecha destino o caen en feriado.
  private actualizarServiciosClonar(): void {
    const destino = this.formClonar.controls.fechaDestino.value;
    for (const s of SERVICIOS) {
      const control = this.formClonar.controls[s as CodigoServicio];
      if (this.motivoNoAtiende(destino, s)) {
        control.setValue(false, { emitEvent: false });
        control.disable({ emitEvent: false });
      } else if (control.disabled) {
        control.enable({ emitEvent: false });
        control.setValue(true, { emitEvent: false });
      }
    }
  }

  // Trae los feriados de una fecha fuera de la semana visible para que feriado() la conozca.
  private cargarFeriado(fecha: string, alTerminar?: () => void): void {
    if (!fecha || !this.auth.tienePermiso('CALENDARIO_GESTIONAR')) return;
    this.diaNoAtencionService.listar({ desde: fecha, hasta: fecha, localId: null }).subscribe({
      next: nuevos => {
        this.feriados.update(actuales => [...actuales.filter(f => !nuevos.some(n => n.id === f.id)), ...nuevos]);
        alTerminar?.();
      },
      error: () => undefined,
    });
  }

  private ejecutar<T>(peticion: Observable<T>, alTerminar: () => void, exito: string | ((respuesta: T) => string)): void {
    this.guardando.set(true);
    this.errorModal.set(null);
    this.mensaje.set(null);
    this.mensajeAviso.set(false);
    peticion.subscribe({
      next: respuesta => {
        this.guardando.set(false);
        alTerminar();
        this.mensaje.set(typeof exito === 'string' ? exito : exito(respuesta));
        this.cargarSemana();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.errorModal.set(mensajeError(err));
      },
    });
  }
}
