import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { EmergenciaService } from '../../services/emergencia.service';
import { MunicipioService } from '../../services/municipio.service';
import { Municipio } from '../../models/municipio.model';

@Component({
  selector: 'app-emergencia-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './emergencia-form.component.html',
  styleUrls: ['./emergencia-form.component.scss'],
})
export class EmergenciaFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly municipioService = inject(MunicipioService);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly router = inject(Router);

  municipios: Municipio[] = [];
  isSubmitting = false;
  errorMessage = '';

  form = this.fb.group({
    tipoEmergencia: ['INCENDIO', Validators.required],
    nivelGravedad: ['MEDIA', Validators.required],
    zonaAfectada: ['', Validators.required],
    descripcion: ['', Validators.required],
    municipioId: [null as number | null],
    hectareasAfectadas: [null as number | null],
    milimetrosAgua: [null as number | null],
    magnitudRichter: [null as number | null],
  });

  ngOnInit(): void {
    this.cargarMunicipios();
  }

  get tipoActual(): string {
    return this.form.get('tipoEmergencia')?.value ?? 'INCENDIO';
  }

  cargarMunicipios(): void {
    this.municipioService.listar().subscribe({
      next: (municipios) => {
        this.municipios = municipios;
        if (municipios.length > 0) {
          this.form.patchValue({ municipioId: municipios[0].id });
        }
      },
      error: () => {
        this.errorMessage = 'No se pudieron cargar los municipios.';
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Completá los campos obligatorios.';
      return;
    }

    const values = this.form.getRawValue();
    const tipoEmergencia = values.tipoEmergencia ?? 'INCENDIO';
    const nivelGravedad = values.nivelGravedad ?? 'MEDIA';
    const zonaAfectada = values.zonaAfectada ?? '';
    const descripcion = values.descripcion ?? '';

    const payload = {
      tipoEmergencia,
      nivelGravedad,
      zonaAfectada,
      descripcion,
      municipioId: values.municipioId ?? null,
      hectareasAfectadas:
        tipoEmergencia === 'INCENDIO' ? Number(values.hectareasAfectadas ?? 0) : null,
      milimetrosAgua:
        tipoEmergencia === 'INUNDACION' ? Number(values.milimetrosAgua ?? 0) : null,
      magnitudRichter:
        tipoEmergencia === 'TERREMOTO' ? Number(values.magnitudRichter ?? 0) : null,
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
