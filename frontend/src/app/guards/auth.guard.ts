import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { RolUsuario } from '../models/auth.model';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  // Si no está autenticado, lo redirige forzosamente al login
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return true;
  }

  // Si ya tiene sesión activa, no le muestra el login y lo lleva a las pantallas de trabajo
  return router.createUrlTree(['/emergencias']);
};

export const roleGuard = (allowedRoles: RolUsuario[]): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      return router.createUrlTree(['/login']);
    }

    if (authService.hasRole(allowedRoles)) {
      return true;
    }

    // Si está autenticado pero no tiene el rol correspondiente, redirige a emergencias
    return router.createUrlTree(['/emergencias']);
  };
};
