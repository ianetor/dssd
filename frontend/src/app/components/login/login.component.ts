import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  errorMessage = '';
  isLoading = false;

  onSubmit(): void {
    if (!this.username.trim() || !this.password) {
      this.errorMessage = 'Por favor ingresa usuario y contraseña.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login({ username: this.username.trim(), password: this.password }).subscribe({
      next: (usuario) => {
        this.isLoading = false;
        // Redirigir según el rol
        if (usuario.rol === 'OPERADOR_MUNICIPAL') {
          this.router.navigate(['/emergencias']);
        } else if (usuario.rol === 'COORDINADOR_REGIONAL') {
          this.router.navigate(['/emergencias']);
        } else {
          this.router.navigate(['/']);
        }
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = 'Credenciales inválidas. Verifica tu usuario y contraseña en Bonita BPM.';
        } else if (err.status === 502) {
          this.errorMessage = 'No se pudo conectar con el motor Bonita BPM. Verifica que Bonita Studio esté iniciado en el puerto configurado.';
        } else {
          this.errorMessage = err.error?.message || 'Error al iniciar sesión. Intenta nuevamente.';
        }
      },
    });
  }

  // Permite rellenar rápidamente el formulario con las credenciales de prueba configuradas en Bonita
  setDemoUser(usuario: string, pass: string): void {
    this.username = usuario;
    this.password = pass;
    this.errorMessage = '';
  }
}
