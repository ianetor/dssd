import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Emergencia } from '../../models/emergencia.model';
import { EmergenciaService } from '../../services/emergencia.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent implements OnInit {
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  emergencias: Emergencia[] = [];
  loading = true;
  error = '';

  get activas(): number {
    return this.emergencias.filter((emergencia) => emergencia.estado === 'ACTIVA').length;
  }

  get registradas(): number {
    return this.emergencias.filter((emergencia) => emergencia.estado === 'REGISTRADA').length;
  }

  get recientes(): Emergencia[] {
    return this.emergencias.slice(-3).reverse();
  }

  ngOnInit(): void {
    this.emergenciaService.listar().subscribe({
      next: (emergencias) => {
        this.emergencias = emergencias;
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
