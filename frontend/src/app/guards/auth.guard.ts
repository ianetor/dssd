import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { RolUsuario } from '../models/auth.model';

export function getRutaPorRol(rol: RolUsuario | null): string {
  switch (rol) {
    case 'OPERADOR_MUNICIPAL':
      return '/operador';
    case 'COORDINADOR_REGIONAL':
      return '/coordinador';
    case 'REPRESENTANTE_ONG':
      return '/portal-ong';
    case 'AUDITOR_DIRECTIVO':
      return '/emergencias';
    default:
      return '/login';
  }
}

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  // Redirige estrictamente al login si no tiene sesión activa
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return true;
  }

  // Si ya inició sesión, redirige a la única pantalla de su rol
  return router.createUrlTree([getRutaPorRol(authService.userRole())]);
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

    // Si intenta acceder a una pantalla de otro rol, lo manda exclusivamente a la suya
    return router.createUrlTree([getRutaPorRol(authService.userRole())]);
  };
};

export const rootGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }

  return router.createUrlTree([getRutaPorRol(authService.userRole())]);
};
