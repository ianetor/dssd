import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { EmergenciaService } from '../../services/emergencia.service';
import { Emergencia } from '../../models/emergencia.model';
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
  private readonly cd = inject(ChangeDetectorRef);
  private readonly authService = inject(AuthService);


  emergencias: Emergencia[] = [];
  isSubmitting = false;
  isLoadingList = false;
  errorMessage = '';
  successMessage = '';
  filtroEstado = 'TODAS';

  selectedEmergencia?: Emergencia;
  showModal = false;

  form = this.fb.group({
    tipoEmergencia: ['INUNDACION', Validators.required],
    nivelGravedad: ['ALTA', Validators.required],
    zonaAfectada: ['', [
      Validators.required,
      Validators.maxLength(255),
      Validators.pattern(/^[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ\s\/\-,\.]+$/)
    ]],
    descripcion: ['', [
      Validators.required,
      Validators.maxLength(1000)
    ]],
    hectareasAfectadas: [null as number | null, [Validators.min(0)]],
    milimetrosAgua: [null as number | null, [Validators.min(0)]],
    magnitudRichter: [null as number | null, [Validators.min(0)]],
    responsableNombre: ['Comandante Roberto Varela', [
      Validators.maxLength(255),
      Validators.pattern(/^[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ\s\.]+$/)
    ]],
    responsableTelefono: ['+54 336 442-9901', [Validators.maxLength(30)]],
  });

  ngOnInit(): void {
    this.cargarEmergencias();
  }

  get tipoActual(): string {
    return this.form.get('tipoEmergencia')?.value ?? 'INUNDACION';
  }

  get emergenciasFiltradas(): Emergencia[] {
    if (this.filtroEstado === 'TODAS') {
      return this.emergencias;
    }
    return this.emergencias.filter(
      (e) => e.estado?.toUpperCase() === this.filtroEstado.toUpperCase()
    );
  }

 

  onSubmit(): void {
    Object.keys(this.form.controls).forEach(key => {
    const control = this.form.get(key);
    if (control?.invalid) {
      console.log('Campo inválido:', key, control.errors);
    }
    });
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Por favor complete todos los campos obligatorios (*).';
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
    this.successMessage = '';

    this.emergenciaService.crear(payload).subscribe({
      next: (creada) => {
        this.isSubmitting = false;
        this.successMessage = `Declaración registrada con éxito. Se instanció el proceso en Bonita BPM para la emergencia #${creada.id ?? ''}.`;
        
        // Agregar a la lista localmente
        this.emergencias.unshift(creada);

        // Reset parcial conservando municipio y responsable
        this.form.patchValue({
          zonaAfectada: '',
          descripcion: '',
          hectareasAfectadas: null,
          milimetrosAgua: null,
          magnitudRichter: null,
        });

        this.cd.detectChanges();
      },
      error: () => {
        this.isSubmitting = false;
        this.errorMessage = 'No se pudo registrar la emergencia en el servidor.';
        this.cd.detectChanges();
      },
    });
  }
  
  abrirDetalle(emergencia: Emergencia): void {
    this.selectedEmergencia = emergencia;
    this.showModal = true;
  }

  cerrarDetalle(): void {
    this.showModal = false;
    this.selectedEmergencia = undefined;
  }

  cargarEmergencias(): void {
      this.isLoadingList = true;
      this.emergenciaService.listar().subscribe({
        next: (data) => {
          this.emergencias = data;
          this.isLoadingList = false;
          this.cd.detectChanges();
        },
        error: () => {
          this.isLoadingList = false;
          this.cd.detectChanges();
        },
      });
    }
}
