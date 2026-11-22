import { Routes } from '@angular/router';
import { AuditoriaList } from './pages/auditoria-list/auditoria-list';

// La ruta padre exige AUDITORIA_VER (dueño y administrador).
export const AUDITORIA_ROUTES: Routes = [{ path: '', component: AuditoriaList, title: 'Auditoría' }];
