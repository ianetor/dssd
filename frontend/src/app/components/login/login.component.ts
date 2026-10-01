import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RolUsuario } from '../../models/auth.model';
import { getRutaPorRol } from '../../guards/auth.guard';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  username = 'anthony.nichols';
  password = 'bpm';
  errorMessage = '';
  serverUnavailable = false;
  isLoading = false;

  onSubmit(): void {
    if (!this.username.trim() || !this.password) {
      this.errorMessage = 'Por favor ingresa usuario y contraseña.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.serverUnavailable = false;

    this.authService.login({ username: this.username.trim(), password: this.password }).subscribe({
      next: (usuario) => {
        this.isLoading = false;
        this.redirigirSegunRol(usuario.rol);
      },
      error: (err) => {
        this.isLoading = false;
        this.serverUnavailable = true;
        if (err.status === 401) {
          this.errorMessage = 'Credenciales inválidas en Bonita BPM. Verifica usuario y contraseña.';
        } else if (err.status === 502 || err.status === 504 || err.status === 0) {
          this.errorMessage =
            'No se pudo conectar con el motor Bonita BPM / Backend local (puerto 8080). Reintente cuando el servidor esté disponible.';
        } else {
          this.errorMessage = err.error?.message || 'Error al autenticar contra Bonita.';
        }
      },
    });
  }

  // Pre-carga usuario y contraseña oficial de Bonita
  setDemoUser(usuario: string, pass: string): void {
    this.username = usuario;
    this.password = pass;
    this.errorMessage = '';
    this.serverUnavailable = false;
  }

  private redirigirSegunRol(rol: RolUsuario): void {
    this.router.navigate([getRutaPorRol(rol)]);
  }
}
