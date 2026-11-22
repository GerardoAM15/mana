import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PlatoService } from './services/plato-service';
import { MenuService } from './services/menu-service';
import { ReglaCobroService } from './services/regla-cobro-service';
import { TarifaService } from './services/tarifa-service';
import { PrecioSueltoService } from './services/precio-suelto-service';
import { PlanService } from '../planes/services/plan-service';
import { DiaNoAtencionService } from '../calendario/services/dia-no-atencion-service';

describe('Jyon: contrato HTTP de los servicios Angular', () => {
  let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());

  for (const [nombre, token, ruta] of [
    ['CU_11 platos', PlatoService, 'platos'], ['CU_13 reglas', ReglaCobroService, 'reglas-cobro'],
    ['CU_14 tarifas', TarifaService, 'tarifas'], ['CU_22 planes', PlanService, 'planes'],
  ] as const) {
    it(`${nombre}: lista por GET`, () => { (TestBed.inject(token as any) as any).listar().subscribe(); const r = http.expectOne(`/api/v1/${ruta}`); expect(r.request.method).toBe('GET'); r.flush([]); });
    it(`${nombre}: alta por POST con el cuerpo original`, () => { const dto = { nombre: 'QA', servicio: 'A', precio: 5 }; (TestBed.inject(token as any) as any).crear(dto).subscribe(); const r = http.expectOne(`/api/v1/${ruta}`); expect(r.request.method).toBe('POST'); expect(r.request.body).toEqual(dto); r.flush({ id: 5 }); });
    it(`${nombre}: edita el ID solicitado por PUT`, () => { const dto = { nombre: 'QA editado' }; (TestBed.inject(token as any) as any).actualizar(7, dto).subscribe(); const r = http.expectOne(`/api/v1/${ruta}/7`); expect(r.request.method).toBe('PUT'); expect(r.request.body).toEqual(dto); r.flush({ id: 7 }); });
    it(`${nombre}: propaga conflictos al componente`, () => { let codigo = 0; (TestBed.inject(token as any) as any).crear({}).subscribe({ error: (e: any) => codigo = e.status }); http.expectOne(`/api/v1/${ruta}`).flush({ message: 'Duplicado' }, { status: 409, statusText: 'Conflict' }); expect(codigo).toBe(409); });
    it(`${nombre}: solicita baja por DELETE`, () => { (TestBed.inject(token as any) as any).eliminar(7).subscribe(); const r = http.expectOne(`/api/v1/${ruta}/7`); expect(r.request.method).toBe('DELETE'); r.flush(null, { status: 204, statusText: 'No Content' }); });
  }
  it('CU_12: consulta el rango, servicio y local seleccionados', () => { TestBed.inject(MenuService).listar({ desde: '2030-01-07', hasta: '2030-01-13', servicio: 'A', localId: 2 }).subscribe(); const r = http.expectOne(req => req.url === '/api/v1/menus'); expect(r.request.params.get('desde')).toBe('2030-01-07'); expect(r.request.params.get('hasta')).toBe('2030-01-13'); expect(r.request.params.get('servicio')).toBe('A'); expect(r.request.params.get('localId')).toBe('2'); r.flush([]); });
  it('CU_12: guarda la celda con PUT', () => { const dto = { fecha: '2030-01-07', servicio: 'A', platoIds: [1, 2] }; TestBed.inject(MenuService).guardar(dto).subscribe(); const r = http.expectOne('/api/v1/menus'); expect(r.request.method).toBe('PUT'); expect(r.request.body).toEqual(dto); r.flush({}); });
  it('CU_12: clona con POST y fechas explícitas', () => { const dto = { fechaOrigen: '2030-01-07', fechaDestino: '2030-01-08', servicios: ['A'] }; TestBed.inject(MenuService).clonar(dto).subscribe(); const r = http.expectOne('/api/v1/menus/clonar'); expect(r.request.method).toBe('POST'); expect(r.request.body).toEqual(dto); r.flush([]); });
  it('CU_12: copiar a cena usa su endpoint específico', () => { TestBed.inject(MenuService).copiarACena({ fecha: '2030-01-07' }).subscribe(); const r = http.expectOne('/api/v1/menus/copiar-a-cena'); expect(r.request.method).toBe('POST'); r.flush({}); });
  it('CU_14: actualizar un suelto conserva su clave compuesta', () => { const dto = { servicio: 'A', categoria: 'ENTRADA', precio: 4, recargoLlevar: 1, activo: true }; TestBed.inject(PrecioSueltoService).actualizar('A', 'ENTRADA', dto).subscribe(); const r = http.expectOne('/api/v1/precios-sueltos/A/ENTRADA'); expect(r.request.method).toBe('PUT'); expect(r.request.body).toEqual(dto); r.flush(dto); });
  it('CU_23: filtra cierres por fechas y local', () => { TestBed.inject(DiaNoAtencionService).listar({ desde: '2030-01-01', hasta: '2030-12-31', localId: 2 }).subscribe(); const r = http.expectOne(req => req.url === '/api/v1/dias-no-atencion'); expect(r.request.params.get('localId')).toBe('2'); expect(r.request.params.get('hasta')).toBe('2030-12-31'); r.flush([]); });
  it('CU_23: alta, edición y eliminación usan los endpoints correctos', () => { const s = TestBed.inject(DiaNoAtencionService), dto = { fecha: '2030-01-07', motivo: 'QA' }; s.crear(dto).subscribe(); const alta = http.expectOne('/api/v1/dias-no-atencion'); expect(alta.request.method).toBe('POST'); alta.flush({ id: 7 }); s.actualizar(7, dto).subscribe(); const edicion = http.expectOne('/api/v1/dias-no-atencion/7'); expect(edicion.request.method).toBe('PUT'); edicion.flush({ id: 7 }); s.eliminar(7).subscribe(); const baja = http.expectOne('/api/v1/dias-no-atencion/7'); expect(baja.request.method).toBe('DELETE'); baja.flush(null); });
});
