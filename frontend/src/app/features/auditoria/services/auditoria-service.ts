import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PaginaResponse } from '../../../core/models/pagina-response';
import { EventoAuditoria, FiltroAuditoria } from '../models/auditoria.model';

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/auditoria`;

  buscar(filtro: FiltroAuditoria, pagina: number, tamanio: number): Observable<PaginaResponse<EventoAuditoria>> {
    let params = new HttpParams()
      .set('pagina', pagina)
      .set('tamanio', tamanio)
      .set('ordenarPor', 'fecha')
      .set('direccion', 'desc');

    if (filtro.localId) params = params.set('localId', filtro.localId);
    if (filtro.accion) params = params.set('accion', filtro.accion);
    if (filtro.usuario) params = params.set('usuario', filtro.usuario);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);

    return this.http.get<PaginaResponse<EventoAuditoria>>(this.url, { params });
  }
}
