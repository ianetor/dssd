import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { EmergenciaService } from '../../services/emergencia.service';
import { AuthService } from '../../services/auth.service';
import { EmergenciaPayload } from '../../models/emergencia.model';

@Component({
  selector: 'app-emergencia-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './emergencia-form.component.html',
  styleUrls: ['./emergencia-form.component.scss'],
})
export class EmergenciaFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  isSubmitting = false;
  errorMessage = '';

  form = this.fb.group({
    tipoEmergencia: ['INCENDIO', Validators.required],
    nivelGravedad: ['MEDIA', Validators.required],
    zonaAfectada: ['', Validators.required],
    descripcion: ['', Validators.required],
    hectareasAfectadas: [null as number | null],
    milimetrosAgua: [null as number | null],
    magnitudRichter: [null as number | null],
  });

  get tipoActual(): string {
    return this.form.get('tipoEmergencia')?.value ?? 'INCENDIO';
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Completá los campos obligatorios.';
      return;
    }

    const values = this.form.getRawValue();
    const tipoEmergencia = values.tipoEmergencia ?? 'INCENDIO';
    const currentUser = this.authService.currentUser();
    const municipioNombre = currentUser?.username ?? currentUser?.entidadNombre ?? '';

    // Se arma el payload con la información de la emergencia y el municipioNombre del usuario autenticado
    const payload: EmergenciaPayload = {
      tipoEmergencia,
      nivelGravedad: values.nivelGravedad ?? 'MEDIA',
      zonaAfectada: values.zonaAfectada ?? '',
      descripcion: values.descripcion ?? '',
      hectareasAfectadas:
        tipoEmergencia === 'INCENDIO' ? Number(values.hectareasAfectadas ?? 0) : null,
      milimetrosAgua:
        tipoEmergencia === 'INUNDACION' ? Number(values.milimetrosAgua ?? 0) : null,
      magnitudRichter:
        tipoEmergencia === 'TERREMOTO' ? Number(values.magnitudRichter ?? 0) : null,
      municipioNombre,
    };

    this.isSubmitting = true;
    this.errorMessage = '';

    this.emergenciaService.crear(payload).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.router.navigateByUrl('/');
      },
      error: () => {
        this.isSubmitting = false;
        this.errorMessage = 'No se pudo registrar la emergencia.';
      },
    });
  }
}