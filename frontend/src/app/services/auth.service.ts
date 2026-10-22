import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, finalize, timeout } from 'rxjs';
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
  readonly currentUser = signal<UsuarioAutenticado | null>(null);

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
    this.http.post<void>(`${this.apiUrl}/logout`, {}, { withCredentials: true }).pipe(
      catchError(() => of(undefined)), finalize(() => this.limpiarSesion()),
    ).subscribe();
  }

  limpiarSesion(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.currentUser.set(null);
    void this.router.navigate(['/login']);
  }

  hasRole(roles: RolUsuario[]): boolean {
    const rolActual = this.userRole();
    return rolActual !== null && roles.includes(rolActual);
  }

  guardarUsuario(usuario: UsuarioAutenticado): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario));
    this.currentUser.set(usuario);
  }

  restaurarSesion(): Observable<UsuarioAutenticado | null> {
    return this.http.get<UsuarioAutenticado>(`${this.apiUrl}/me`, { withCredentials: true }).pipe(
      timeout(10000),
      catchError(() => of(null)),
      tap(usuario => {
        if (usuario) this.guardarUsuario(usuario);
        else { localStorage.removeItem(STORAGE_KEY); this.currentUser.set(null); }
      }),
    );
  }
}
