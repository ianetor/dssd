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

  // Signal reactivo para usuario logueado
  readonly currentUser = signal<UsuarioAutenticado | null>(this.obtenerUsuarioAlmacenado());

  // Propiedades computadas para templates
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

  guardarUsuario(usuario: UsuarioAutenticado): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario));
    this.currentUser.set(usuario);
  }

  // Fallback demo local para cuando se pruebe frontend sin backend de Bonita activo
  loginDemo(username: string): UsuarioAutenticado {
    let mockUser: UsuarioAutenticado;

    switch (username) {
      case 'anthony.nichols':
        mockUser = {
          id: 1,
          username: 'anthony.nichols',
          nombreCompleto: 'Anthony Nichols',
          rol: 'OPERADOR_MUNICIPAL',
          rolDisplayName: 'Operador Municipal',
          entidadId: 1,
          entidadNombre: 'Municipio de San Nicolás',
        };
        break;
      case 'daniela.angelo':
        mockUser = {
          id: 2,
          username: 'daniela.angelo',
          nombreCompleto: 'Daniela Angelo',
          rol: 'COORDINADOR_REGIONAL',
          rolDisplayName: 'Centro Coordinador Regional',
          entidadId: 1,
          entidadNombre: 'Nodo Regional Centro',
        };
        break;
      case 'april.sanchez':
        mockUser = {
          id: 3,
          username: 'april.sanchez',
          nombreCompleto: 'April Sanchez',
          rol: 'REPRESENTANTE_ONG',
          rolDisplayName: 'Representante ONG',
          entidadId: 1,
          entidadNombre: 'Cruz Roja Argentina',
        };
        break;
      case 'favio.riviera':
      default:
        mockUser = {
          id: 4,
          username: 'favio.riviera',
          nombreCompleto: 'Favio Riviera',
          rol: 'AUDITOR_DIRECTIVO',
          rolDisplayName: 'Auditor / Directivo',
          entidadId: 1,
          entidadNombre: 'Ministerio de Seguridad y Defensa',
        };
        break;
    }

    this.guardarUsuario(mockUser);
    return mockUser;
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
