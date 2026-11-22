import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

// Si la contraseña actual no cumple la política, no deja entrar al sistema hasta cambiarla.
export const claveGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.debeCambiarClave() ? inject(Router).createUrlTree(['/cambiar-clave']) : true;
};
