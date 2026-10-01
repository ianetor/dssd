import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap, finalize } from 'rxjs';
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

  // Signal reactivo para usuario logueado
  readonly currentUser = signal<UsuarioAutenticado | null>(this.obtenerUsuarioAlmacenado());

  // Propiedades computadas para templates
  readonly isLoggedIn = computed(() => !!this.currentUser());
  readonly userRole = computed(() => this.currentUser()?.rol ?? null);
  readonly userFullName = computed(() => this.currentUser()?.nombreCompleto ?? '');
  readonly entityName = computed(() => this.currentUser()?.entidadNombre ?? '');

  login(credentials: LoginCredentials): Observable<UsuarioAutenticado> {
    return this.http.post<UsuarioAutenticado>(`${this.apiUrl}/login`, credentials, { withCredentials: true }).pipe(
      tap((usuario) => {
        this.guardarUsuario(usuario);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.currentUser.set(null);
    this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true }).pipe(
      finalize(() => this.router.navigate(['/login']))
    ).subscribe({ error: () => {} });
  }

  hasRole(roles: RolUsuario[]): boolean {
    const rolActual = this.userRole();
    return rolActual !== null && roles.includes(rolActual);
  }

  guardarUsuario(usuario: UsuarioAutenticado): void {
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
