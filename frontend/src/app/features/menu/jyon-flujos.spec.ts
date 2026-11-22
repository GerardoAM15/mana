import { HttpErrorResponse } from '@angular/common/http';
import { signal, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CatalogoService } from '../../core/services/catalogo-service';
import { PlatoForm } from './pages/plato-form/plato-form';
import { ReglaCobroForm } from './pages/regla-cobro-form/regla-cobro-form';
import { MenuSemana } from './pages/menu-semana/menu-semana';
import { Tarifas } from './components/tarifas/tarifas';
import { PlanForm } from '../planes/pages/plan-form/plan-form';
import { DiaNoAtencionList } from '../calendario/pages/dia-no-atencion-list/dia-no-atencion-list';
import { PlatoService } from './services/plato-service';
import { ReglaCobroService } from './services/regla-cobro-service';
import { MenuService } from './services/menu-service';
import { TarifaService } from './services/tarifa-service';
import { PlanService } from '../planes/services/plan-service';
import { DiaNoAtencionService } from '../calendario/services/dia-no-atencion-service';

const servicios = [{ codigo: 'D', nombre: 'Desayuno', activo: true }, { codigo: 'A', nombre: 'Almuerzo', activo: true }, { codigo: 'C', nombre: 'Cena', activo: true }];
const categorias = [{ codigo: 'ENTRADA', nombre: 'Entrada', activo: true }, { codigo: 'SEGUNDO', nombre: 'Segundo', activo: true }];
const errorApi = () => throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Conflicto de prueba' } }));
const api = () => ({ listar: vi.fn(() => of([] as any[])), crear: vi.fn(() => of({ id: 91 } as any)), actualizar: vi.fn(() => of({ id: 91 } as any)), obtener: vi.fn(() => of({} as any)), eliminar: vi.fn(() => of(undefined)), guardar: vi.fn(() => of({} as any)), clonar: vi.fn(() => of([] as any[])), copiarACena: vi.fn(() => of({} as any)) });

async function preparar<T>(tipo: Type<T>, opciones: { id?: string; gestiona?: boolean; antes?: (apis: ReturnType<typeof api>[]) => void } = {}) {
  const apis = [api(), api(), api(), api(), api(), api()];
  apis[0].listar.mockReturnValue(of([
    { id: 3, nombre: 'Arroz con pollo', categoria: 'SEGUNDO', activo: true },
    { id: 4, nombre: 'Lentejas', categoria: 'SEGUNDO', activo: true },
    { id: 5, nombre: 'Ensalada', categoria: 'ENTRADA', activo: true },
    { id: 6, nombre: 'Quinua', categoria: 'BEBIDA_DESAYUNO', activo: true },
    { id: 7, nombre: 'Pan francés', categoria: 'PAN', activo: true },
  ]));
  apis[1].listar.mockReturnValue(of([
    { id: 1, servicio: 'D', activo: true, componentes: [{ categoria: 'PAN', cantidad: 2 }, { categoria: 'BEBIDA_DESAYUNO', cantidad: 1 }] },
    { id: 2, servicio: 'A', activo: true, componentes: [{ categoria: 'ENTRADA', cantidad: 1 }, { categoria: 'SEGUNDO', cantidad: 1 }] },
    { id: 3, servicio: 'C', activo: true, componentes: [{ categoria: 'SEGUNDO', cantidad: 1 }] },
  ]));
  opciones.antes?.(apis);
  const catalogos = { servicios: signal(servicios), categorias: signal(categorias),
    cargar: vi.fn(() => of({ servicios, categorias })), nombreServicio: (c: string) => servicios.find(s => s.codigo === c)?.nombre ?? c,
    nombreCategoria: (c: string) => categorias.find(s => s.codigo === c)?.nombre ?? c,
    seAtiende: (s: string, dia: number) => dia >= 1 && dia <= (s === 'C' ? 4 : 5) };
  await TestBed.configureTestingModule({ imports: [tipo], providers: [provideRouter([]),
    { provide: AuthService, useValue: { tienePermiso: () => opciones.gestiona !== false, usuario: () => ({ localNombre: 'Local QA' }) } },
    { provide: CatalogoService, useValue: catalogos },
    ...[PlatoService, ReglaCobroService, MenuService, TarifaService, PlanService, DiaNoAtencionService].map((token, i) => ({ provide: token, useValue: apis[i] })),
  ] }).compileComponents();
  const fixture = TestBed.createComponent(tipo);
  if (opciones.id) fixture.componentRef.setInput('id', opciones.id);
  const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
  return { fixture, c: fixture.componentInstance as any, apis, navegar, html: fixture.nativeElement as HTMLElement };
}

describe('Jyon CU_11: banco de platos', () => {
  it('no envía un formulario vacío', async () => { const { c, apis } = await preparar(PlatoForm); c.guardar(); expect(apis[0].crear).not.toHaveBeenCalled(); });
  it('registra y vuelve al banco de platos', async () => { const { c, apis, navegar } = await preparar(PlatoForm); c.form.setValue({ nombre: '  Pollo  ', categoria: 'SEGUNDO', activo: true }); c.guardar(); expect(apis[0].crear).toHaveBeenCalledWith({ nombre: 'Pollo', categoria: 'SEGUNDO', activo: true }); expect(navegar).toHaveBeenCalledWith(['/menu/platos']); });
  it('edita un plato existente sin crear otro', async () => { const { c, apis } = await preparar(PlatoForm, { id: '7', antes: a => a[0].obtener.mockReturnValue(of({ nombre: 'Pollo', categoria: 'SEGUNDO', activo: true })) }); c.guardar(); expect(apis[0].actualizar).toHaveBeenCalledWith(7, expect.any(Object)); expect(apis[0].crear).not.toHaveBeenCalled(); });
  it('conserva los campos si el backend rechaza la categoría', async () => { const { c, apis, navegar } = await preparar(PlatoForm); apis[0].crear.mockReturnValue(errorApi()); c.form.setValue({ nombre: 'Pollo', categoria: 'SEGUNDO', activo: true }); c.guardar(); expect(c.error()).toBe('Conflicto de prueba'); expect(c.guardando()).toBe(false); expect(c.form.controls.nombre.value).toBe('Pollo'); expect(navegar).not.toHaveBeenCalled(); });
  it('rechaza nombres de más de 120 caracteres', async () => { const { c, apis } = await preparar(PlatoForm); c.form.setValue({ nombre: 'x'.repeat(121), categoria: 'SEGUNDO', activo: true }); c.guardar(); expect(apis[0].crear).not.toHaveBeenCalled(); });
});

describe('Jyon CU_12: programación semanal', () => {
  const lunes = '2030-01-07';
  it('guarda los IDs seleccionados y cierra el editor', async () => { const { c, apis } = await preparar(MenuSemana); c.abrirEditor(lunes, 'A'); c.alternar(3); c.alternar(4); c.guardarCelda(); expect(apis[2].guardar).toHaveBeenCalledWith({ fecha: lunes, servicio: 'A', platoIds: [3, 4] }); expect(c.editando()).toBeNull(); expect(c.mensaje()).toBe('Menú guardado.'); expect(apis[2].listar).toHaveBeenCalledTimes(2); });
  it('mantiene el editor y la selección ante un error', async () => { const { c, apis } = await preparar(MenuSemana); apis[2].guardar.mockReturnValue(errorApi()); c.abrirEditor(lunes, 'A'); c.alternar(3); c.guardarCelda(); expect(c.editando()).toEqual({ fecha: lunes, servicio: 'A' }); expect(c.elegidos().has(3)).toBe(true); expect(c.errorModal()).toBe('Conflicto de prueba'); expect(c.guardando()).toBe(false); });
  it('un lector no abre el editor', async () => { const { c } = await preparar(MenuSemana, { gestiona: false }); c.abrirEditor(lunes, 'A'); expect(c.editando()).toBeNull(); });
  it('no abre la cena del viernes', async () => { const { c } = await preparar(MenuSemana); c.abrirEditor('2030-01-11', 'C'); expect(c.editando()).toBeNull(); });
  it('no clona sobre la misma fecha', async () => { const { c, apis } = await preparar(MenuSemana); c.formClonar.patchValue({ fechaOrigen: lunes, fechaDestino: lunes }); c.clonar(); expect(apis[2].clonar).not.toHaveBeenCalled(); expect(c.errorModal()).toContain('distinta'); });
  it('no copia segundos a una cena sin atención', async () => { const { c, apis } = await preparar(MenuSemana); c.abrirCopiar('2030-01-11'); c.copiarACena(); expect(apis[2].copiarACena).not.toHaveBeenCalled(); });
  it('avisa cuando el backend omite servicios al clonar', async () => { const { c, apis } = await preparar(MenuSemana); c.formClonar.patchValue({ fechaOrigen: lunes, fechaDestino: '2030-01-08', D: true, A: true, C: true }); apis[2].clonar.mockReturnValue(of([{ servicio: 'A' }])); c.clonar(); expect(c.mensajeAviso()).toBe(true); expect(c.mensaje()).toContain('Se omitió'); });
  it('WhatsApp no abre un cuadro si no hay platos', async () => { const { c } = await preparar(MenuSemana); c.abrirCompartir(lunes); expect(c.compartiendo()).toBeNull(); });
  it('desayuno solo muestra pan y bebida, no platos de almuerzo', async () => { const { c, fixture, html } = await preparar(MenuSemana); c.abrirEditor(lunes, 'D'); fixture.detectChanges(); expect(c.gruposBanco().map((g: any) => g.categoria)).toEqual(['PAN', 'BEBIDA_DESAYUNO']); expect(html.textContent).toContain('Pan francés'); expect(html.textContent).not.toContain('Arroz con pollo'); });
  it('almuerzo ofrece varias alternativas de segundo sin mezclar desayuno', async () => { const { c } = await preparar(MenuSemana); c.abrirEditor(lunes, 'A'); expect(c.gruposBanco().flatMap((g: any) => g.platos.map((p: any) => p.id))).toEqual([5, 3, 4]); c.alternar(3); c.alternar(4); expect(c.elegidos().size).toBe(2); });
  it('cena puede reutilizar los mismos segundos sin entradas', async () => { const { c } = await preparar(MenuSemana); c.abrirEditor(lunes, 'C'); expect(c.gruposBanco().flatMap((g: any) => g.platos.map((p: any) => p.id))).toEqual([3, 4]); });
  it('no permite seleccionar un plato ajeno al turno', async () => { const { c } = await preparar(MenuSemana); c.abrirEditor(lunes, 'D'); c.alternar(3); expect(c.elegidos().has(3)).toBe(false); });
  it('sin composición bloquea el guardado y explica cómo configurarla', async () => { const { c, apis } = await preparar(MenuSemana, { antes: a => a[1].listar.mockReturnValue(of([])) }); c.abrirEditor(lunes, 'A'); c.guardarCelda(); expect(apis[2].guardar).not.toHaveBeenCalled(); expect(c.errorModal()).toContain('Configure primero'); });
  it('permite retirar explícitamente platos históricos que ya no corresponden', async () => { const { c } = await preparar(MenuSemana); c.menus.set([{ fecha: lunes, servicio: 'D', platos: [{ platoId: 3 }, { platoId: 7 }] }]); c.abrirEditor(lunes, 'D'); expect(c.seleccionFueraDelTurno()).toBe(true); c.retirarPlatosFueraDelTurno(); expect([...c.elegidos()]).toEqual([7]); expect(c.seleccionFueraDelTurno()).toBe(false); });
  it('una respuesta antigua con error tampoco modifica la semana nueva', async () => { const { c, apis } = await preparar(MenuSemana); const antigua = new Subject<any[]>(); apis[2].listar.mockReturnValueOnce(antigua).mockReturnValueOnce(of([])); c.cargarSemana(); c.cambiarSemana(1); antigua.error(new HttpErrorResponse({ status: 500 })); expect(c.error()).toBeNull(); expect(c.cargando()).toBe(false); });
});

describe('Jyon CU_13: menú público', () => {
  function llenar(c: any) { c.form.patchValue({ nombre: 'Menú QA', servicio: 'A', precioCombo: 12, recargoLlevar: 2 }); c.form.controls.componentes.at(0).patchValue({ categoria: 'SEGUNDO', cantidad: 1 }); }
  it('envía cabecera y composición', async () => { const { c, apis, navegar } = await preparar(ReglaCobroForm); llenar(c); c.guardar(); expect(apis[1].crear).toHaveBeenCalledWith({ nombre: 'Menú QA', servicio: 'A', precioCombo: 12, recargoLlevar: 2, activo: true, componentes: [{ categoria: 'SEGUNDO', cantidad: 1 }] }); expect(navegar).toHaveBeenCalledWith(['/menu/precios']); });
  it('bloquea categorías repetidas', async () => { const { c, apis } = await preparar(ReglaCobroForm); llenar(c); c.agregarComponente(); c.form.controls.componentes.at(1).patchValue({ categoria: 'SEGUNDO', cantidad: 2 }); c.guardar(); expect(apis[1].crear).not.toHaveBeenCalled(); expect(c.categoriaRepetida()).toBe(true); });
  it.each([0, -1])('bloquea precio %s', async precio => { const { c, apis } = await preparar(ReglaCobroForm); llenar(c); c.form.patchValue({ precioCombo: precio }); c.guardar(); expect(apis[1].crear).not.toHaveBeenCalled(); });
  it('bloquea recargo negativo', async () => { const { c, apis } = await preparar(ReglaCobroForm); llenar(c); c.form.patchValue({ recargoLlevar: -1 }); c.guardar(); expect(apis[1].crear).not.toHaveBeenCalled(); });
  it('no permite quitar la última categoría', async () => { const { c } = await preparar(ReglaCobroForm); c.quitarComponente(0); expect(c.form.controls.componentes.length).toBe(1); });
  it('muestra conflicto por otro menú activo sin abandonar el formulario', async () => { const { c, apis, navegar } = await preparar(ReglaCobroForm); llenar(c); apis[1].crear.mockReturnValue(errorApi()); c.guardar(); expect(c.error()).toBe('Conflicto de prueba'); expect(navegar).not.toHaveBeenCalled(); expect(c.guardando()).toBe(false); });
});

describe('Jyon CU_14: tarifas', () => {
  it('muestra Crear cuando todavía no existe tarifa', async () => { const { c, fixture, html, apis } = await preparar(Tarifas); expect(html.textContent).toContain('Crear'); const fila = c.filas()[0]; fila.form.patchValue({ precio: 3.2 }); c.guardar(fila); expect(apis[3].crear).toHaveBeenCalledWith({ servicio: 'D', precio: 3.2, activo: true }); fixture.detectChanges(); expect(c.filas()[0].tarifa.id).toBe(91); });
  it('actualiza una tarifa existente', async () => { const { c, apis } = await preparar(Tarifas, { antes: a => a[3].listar.mockReturnValue(of([{ id: 7, servicio: 'D', precio: 3.2, activo: true }])) }); const fila = c.filas()[0]; fila.form.patchValue({ precio: 4 }); c.guardar(fila); expect(apis[3].actualizar).toHaveBeenCalledWith(7, { servicio: 'D', precio: 4, activo: true }); expect(apis[3].crear).not.toHaveBeenCalled(); });
  it('bloquea precio cero', async () => { const { c, apis } = await preparar(Tarifas); const fila = c.filas()[0]; fila.form.patchValue({ precio: 0 }); c.guardar(fila); expect(apis[3].crear).not.toHaveBeenCalled(); });
  it('un lector no ve botones Crear o Guardar y sus campos están deshabilitados', async () => { const { c, html } = await preparar(Tarifas, { gestiona: false }); expect(html.querySelectorAll('button').length).toBe(0); expect(c.filas()[0].form.disabled).toBe(true); });
});

describe('Jyon CU_22: planes', () => {
  it('rechaza un plan sin servicios', async () => { const { c, apis } = await preparar(PlanForm); c.form.patchValue({ nombre: 'Plan QA' }); c.guardar(); expect(apis[4].crear).not.toHaveBeenCalled(); expect(c.intento()).toBe(true); });
  it('crea un plan mensual con varios servicios', async () => { const { c, apis, navegar } = await preparar(PlanForm); c.form.patchValue({ nombre: '  Plan QA  ', periodo: 'MENSUAL' }); c.alternar('D', true); c.alternar('A', true); c.guardar(); expect(apis[4].crear).toHaveBeenCalledWith({ nombre: 'Plan QA', periodo: 'MENSUAL', activo: true, servicios: ['D', 'A'] }); expect(navegar).toHaveBeenCalledWith(['/planes']); });
  it('carga la edición y elimina un servicio de la selección', async () => { const { c, apis } = await preparar(PlanForm, { id: '8', antes: a => a[4].obtener.mockReturnValue(of({ nombre: 'Plan QA', periodo: 'SEMANAL', activo: true, servicios: ['D', 'A'] })) }); c.alternar('D', false); c.guardar(); expect(apis[4].actualizar).toHaveBeenCalledWith(8, expect.objectContaining({ servicios: ['A'] })); });
  it('conserva selección y muestra errores de API', async () => { const { c, apis, navegar } = await preparar(PlanForm); c.form.patchValue({ nombre: 'Plan QA' }); c.alternar('A', true); apis[4].crear.mockReturnValue(errorApi()); c.guardar(); expect(c.servicios()).toEqual(['A']); expect(c.error()).toBe('Conflicto de prueba'); expect(navegar).not.toHaveBeenCalled(); });
});

describe('Jyon CU_23: calendario', () => {
  it('no registra fecha o motivo vacíos', async () => { const { c, apis } = await preparar(DiaNoAtencionList); c.guardar(); expect(apis[5].crear).not.toHaveBeenCalled(); });
  it('registra un cierre y limpia el formulario', async () => { const { c, apis } = await preparar(DiaNoAtencionList); c.form.setValue({ fecha: '2030-01-09', motivo: '  Feriado  ' }); c.guardar(); expect(apis[5].crear).toHaveBeenCalledWith({ fecha: '2030-01-09', motivo: 'Feriado' }); expect(c.form.controls.fecha.value).toBe(''); expect(apis[5].listar).toHaveBeenCalledTimes(2); });
  it('edita por ID sin crear otro cierre', async () => { const { c, apis } = await preparar(DiaNoAtencionList); c.editar({ id: 4, fecha: '2030-01-09', motivo: 'Cierre' }); c.form.patchValue({ motivo: 'Actualizado' }); c.guardar(); expect(apis[5].actualizar).toHaveBeenCalledWith(4, { fecha: '2030-01-09', motivo: 'Actualizado' }); expect(apis[5].crear).not.toHaveBeenCalled(); });
  it('solicita baja únicamente después de confirmar', async () => { const { c, apis } = await preparar(DiaNoAtencionList); c.porEliminar.set({ id: 4, fecha: '2030-01-09', motivo: 'Cierre' }); expect(apis[5].eliminar).not.toHaveBeenCalled(); c.confirmarEliminar(); expect(apis[5].eliminar).toHaveBeenCalledWith(4); expect(c.porEliminar()).toBeNull(); });
  it('mantiene los datos al rechazar una fecha duplicada', async () => { const { c, apis } = await preparar(DiaNoAtencionList); c.form.setValue({ fecha: '2030-01-09', motivo: 'Cierre' }); apis[5].crear.mockReturnValue(errorApi()); c.guardar(); expect(c.error()).toBe('Conflicto de prueba'); expect(c.form.controls.fecha.value).toBe('2030-01-09'); expect(c.guardando()).toBe(false); });
});

// Expectativas de UX: se conservan como regresiones si revelan un defecto.
describe('Jyon: escenarios límite y regresiones', () => {
  it('CU_11 no debería enviar un nombre compuesto solo por espacios', async () => { const { c, apis } = await preparar(PlatoForm); c.form.setValue({ nombre: '   ', categoria: 'SEGUNDO', activo: true }); c.guardar(); expect(apis[0].crear).not.toHaveBeenCalled(); });
  it('CU_12 no debería enviar una programación sin platos', async () => { const { c, apis } = await preparar(MenuSemana); c.abrirEditor('2030-01-07', 'A'); c.guardarCelda(); expect(apis[2].guardar).not.toHaveBeenCalled(); });
  it('CU_13 no debería enviar cantidades fraccionarias de platos', async () => { const { c, apis } = await preparar(ReglaCobroForm); c.form.patchValue({ nombre: 'Menú QA', servicio: 'A', precioCombo: 12, recargoLlevar: 0 }); c.form.controls.componentes.at(0).patchValue({ categoria: 'SEGUNDO', cantidad: 1.5 }); c.guardar(); expect(apis[1].crear).not.toHaveBeenCalled(); });
  it('CU_22 no debería enviar un nombre compuesto solo por espacios', async () => { const { c, apis } = await preparar(PlanForm); c.form.patchValue({ nombre: '   ' }); c.alternar('A', true); c.guardar(); expect(apis[4].crear).not.toHaveBeenCalled(); });
  it('CU_23 debería limpiar el error cuando una recarga posterior funciona', async () => { const { c, apis } = await preparar(DiaNoAtencionList); apis[5].listar.mockReturnValueOnce(errorApi()); c.cargar(); expect(c.error()).toBeTruthy(); apis[5].listar.mockReturnValue(of([])); c.cargar(); expect(c.error()).toBeNull(); });
  it('CU_12 una respuesta lenta de la semana anterior no debería sustituir la semana actual', async () => { const { c, apis } = await preparar(MenuSemana); const anterior = new Subject<any[]>(), actual = new Subject<any[]>(); apis[2].listar.mockReturnValueOnce(anterior).mockReturnValueOnce(actual); c.cargarSemana(); c.cambiarSemana(1); const nuevo = [{ id: 2, fecha: c.dias()[0], servicio: 'A', platos: [] }]; actual.next(nuevo); anterior.next([{ id: 1, fecha: '2020-01-01', servicio: 'A', platos: [] }]); expect(c.menus()).toEqual(nuevo); anterior.complete(); actual.complete(); });
});
