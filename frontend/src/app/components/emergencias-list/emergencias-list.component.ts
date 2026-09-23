import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Emergencia } from '../../models/emergencia.model';
import { EmergenciaService } from '../../services/emergencia.service';

@Component({
  selector: 'app-emergencias-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './emergencias-list.component.html',
  styleUrls: ['./emergencias-list.component.scss'],
})
export class EmergenciasListComponent implements OnInit {
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  emergencias: Emergencia[] = [];
  loading = false;
  error = '';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.error = '';

    this.emergenciaService.listar().subscribe({
      next: (data) => {
        this.emergencias = data;
        this.loading = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'No se pudieron cargar las emergencias.';
        this.loading = false;
        this.changeDetector.detectChanges();
      },
    });
  }
}
