import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { EmergenciaService } from '../../services/emergencia.service';
import { Emergencia } from '../../models/emergencia.model';

@Component({
  selector: 'app-emergencia-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './emergencia-detail.component.html',
  styleUrls: ['./emergencia-detail.component.scss'],
})
export class EmergenciaDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly fb = inject(FormBuilder);
  private readonly changeDetector = inject(ChangeDetectorRef);

  emergencia?: Emergencia;
  loading = false;
  error = '';
  isSubmitting = false;

  loteForm = this.fb.group({
    tipoRecurso: ['', Validators.required],
    cantidadRequerida: [1, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number): void {
    this.loading = true;
    this.emergenciaService.obtenerPorId(id).subscribe({
      next: (emergencia) => {
        this.emergencia = emergencia;
        this.loading = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'No se pudo cargar la emergencia.';
        this.loading = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  submitLote(): void {
    if (!this.emergencia || !this.emergencia.id) {
      return;
    }

    if (this.loteForm.invalid) {
      this.loteForm.markAllAsTouched();
      this.error = 'Completá el tipo y la cantidad del lote.';
      return;
    }

    const values = this.loteForm.getRawValue();
    this.isSubmitting = true;
    this.error = '';

    this.emergenciaService
      .publicarLotes(this.emergencia.id, [
        {
          tipoRecurso: values.tipoRecurso ?? '',
          cantidadRequerida: Number(values.cantidadRequerida ?? 1),
        },
      ])
      .subscribe({
        next: (actualizada) => {
          this.emergencia = actualizada;
          this.loteForm.reset({ tipoRecurso: '', cantidadRequerida: 1 });
          this.isSubmitting = false;
        },
        error: () => {
          this.error = 'No se pudo publicar el lote.';
          this.isSubmitting = false;
        },
      });
  }
}
