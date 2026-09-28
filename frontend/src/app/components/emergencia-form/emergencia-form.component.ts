import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { EmergenciaService } from '../../services/emergencia.service';
import { MunicipioService } from '../../services/municipio.service';
import { Emergencia } from '../../models/emergencia.model';
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
  private readonly cd = inject(ChangeDetectorRef);

  municipios: Municipio[] = [];
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
    municipioId: [null as number | null, Validators.required],
    zonaAfectada: ['', Validators.required],
    descripcion: ['', Validators.required],
    hectareasAfectadas: [null as number | null],
    milimetrosAgua: [null as number | null],
    magnitudRichter: [null as number | null],
    responsableNombre: ['Comandante Roberto Varela'],
    responsableTelefono: ['+54 336 442-9901'],
  });

  ngOnInit(): void {
    this.cargarMunicipios();
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

  cargarMunicipios(): void {
    this.municipioService.listar().subscribe({
      next: (municipios) => {
        this.municipios = municipios;
        if (municipios.length > 0 && !this.form.value.municipioId) {
          this.form.patchValue({ municipioId: municipios[0].id });
        }
        this.cd.detectChanges();
      },
      error: () => {
        // Fallback local en caso de que backend esté offline o vacío
        this.municipios = [
          { id: 1, nombre: 'Municipio de San Nicolás' },
          { id: 2, nombre: 'Municipio de Ramallo' },
          { id: 3, nombre: 'Municipio de San Pedro' },
        ];
        this.form.patchValue({ municipioId: 1 });
        this.cd.detectChanges();
      },
    });
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

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'Por favor complete todos los campos obligatorios (*).';
      return;
    }

    const values = this.form.getRawValue();
    const tipo = values.tipoEmergencia ?? 'INUNDACION';

    const payload = {
      tipoEmergencia: tipo,
      nivelGravedad: values.nivelGravedad ?? 'ALTA',
      zonaAfectada: values.zonaAfectada ?? '',
      descripcion: values.descripcion ?? '',
      municipioId: values.municipioId ? Number(values.municipioId) : null,
      hectareasAfectadas:
        tipo === 'INCENDIO' ? Number(values.hectareasAfectadas ?? 0) : null,
      milimetrosAgua:
        tipo === 'INUNDACION' ? Number(values.milimetrosAgua ?? 0) : null,
      magnitudRichter:
        tipo === 'TERREMOTO' ? Number(values.magnitudRichter ?? 0) : null,
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

  getMunicipioNombre(id?: number | null): string {
    if (!id) return 'San Nicolás';
    const m = this.municipios.find((item) => item.id === id);
    return m ? m.nombre : `Municipio #${id}`;
  }
}
