import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { LocalService } from '../../../locales/services/local-service';
import { EventoAuditoria } from '../../models/auditoria.model';
import { AuditoriaService } from '../../services/auditoria-service';
import { AuditoriaList } from './auditoria-list';

const evento: EventoAuditoria = {
  id: 1, fecha: '2026-11-20T10:15:00-05:00', usuario: 'admin@mana.pe', accion: 'ANULAR_VENTA', entidad: 'VENTA',
  entidadId: 12, localId: 2, detalle: 'Venta V-000012 | estado: PAGADA -> ANULADA | total 18.00 | motivo: cliente se retiró',
  requestId: 'abc12345',
};

async function crear(rol: 'DUENO' | 'ADMINISTRADOR') {
  const buscar = vi.fn().mockReturnValue(of({ contenido: [evento], pagina: 0, tamanio: 20, totalElementos: 1, totalPaginas: 1, ultima: true }));
  const listarLocales = vi.fn().mockReturnValue(of([{ id: 1, nombre: 'Central', activo: true }, { id: 2, nombre: 'Norte', activo: true }]));
  await TestBed.configureTestingModule({
    imports: [AuditoriaList],
    providers: [
      { provide: AuditoriaService, useValue: { buscar } },
      { provide: LocalService, useValue: { listar: listarLocales } },
      { provide: AuthService, useValue: { usuario: signal({ rol, localId: 2, localNombre: 'Norte' }) } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(AuditoriaList);
  fixture.detectChanges();
  return { el: fixture.nativeElement as HTMLElement, buscar, listarLocales };
}

describe('AuditoriaList', () => {
  it('el dueño ve todos los locales y la columna Local', async () => {
    const { el, buscar, listarLocales } = await crear('DUENO');
    expect(listarLocales).toHaveBeenCalled();
    expect(buscar.mock.calls[0][0].localId).toBeNull();
    expect(el.querySelector('select[formcontrolname="localId"]')).not.toBeNull();
    expect(el.textContent).toContain('Todos los locales');
    expect(el.textContent).toContain('Norte');
    expect(el.textContent).toContain('Anular venta');
    expect(el.textContent).toContain('PAGADA -> ANULADA');
  });

  it('el administrador no tiene selector de local ni pide otros locales', async () => {
    const { el, buscar, listarLocales } = await crear('ADMINISTRADOR');
    expect(listarLocales).not.toHaveBeenCalled();
    expect(el.querySelector('select[formcontrolname="localId"]')).toBeNull();
    expect(buscar.mock.calls[0][0].localId).toBeNull();
    expect(el.textContent).toContain('admin@mana.pe');
  });
});
