import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { esEscritorio, servidorGuardado } from '../config/servidor';

// En la app de escritorio, antes de iniciar sesión hay que indicar el servidor de la red local.
export const servidorGuard: CanActivateFn = () =>
  esEscritorio() && !servidorGuardado() ? inject(Router).createUrlTree(['/servidor']) : true;
