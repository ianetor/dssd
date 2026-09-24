import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../enviroments/environment';
import { LoginCredentials, RolUsuario, UsuarioAutenticado } from '../models/auth.model';

const STORAGE_KEY = 'rescuesync_user';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  // Signal que mantiene el estado reactivo del usuario logueado
  readonly currentUser = signal<UsuarioAutenticado | null>(this.obtenerUsuarioAlmacenado());

  // Propiedades computadas para fácil consulta en templates
  readonly isLoggedIn = computed(() => !!this.currentUser());
  readonly userRole = computed(() => this.currentUser()?.rol ?? null);
  readonly userFullName = computed(() => this.currentUser()?.nombreCompleto ?? '');
  readonly entityName = computed(() => this.currentUser()?.entidadNombre ?? '');

  login(credentials: LoginCredentials): Observable<UsuarioAutenticado> {
    return this.http.post<UsuarioAutenticado>(`${this.apiUrl}/login`, credentials).pipe(
      tap((usuario) => {
        this.guardarUsuario(usuario);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(roles: RolUsuario[]): boolean {
    const rolActual = this.userRole();
    return rolActual !== null && roles.includes(rolActual);
  }

  private guardarUsuario(usuario: UsuarioAutenticado): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario));
    this.currentUser.set(usuario);
  }

  private obtenerUsuarioAlmacenado(): UsuarioAutenticado | null {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? (JSON.parse(data) as UsuarioAutenticado) : null;
    } catch {
      return null;
    }
  }
}
