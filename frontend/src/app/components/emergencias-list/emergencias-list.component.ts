import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { RouterModule } from '@angular/router';
import { Emergencia } from '../../models/emergencia.model';
import { RolUsuario } from '../../models/auth.model';
import { EmergenciaService } from '../../services/emergencia.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-emergencias-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './emergencias-list.component.html',
  styleUrls: ['./emergencias-list.component.scss'],
})
export class EmergenciasListComponent implements OnInit {
  protected readonly emergenciaService = inject(EmergenciaService);
  protected readonly authService = inject(AuthService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  // Inputs para comportamiento compartido
  @Input() soloConvocatoriaAbierta?: boolean;
  @Input() modoSeleccion = false;
  @Input() emergenciaSeleccionadaId: number | null = null;
  @Input() mostrarHeader = true;
  @Input() titulo?: string;
  @Input() subtitulo?: string;

  // Outputs para emitir eventos al componente contenedor
  @Output() seleccionarEmergencia = new EventEmitter<Emergencia>();
  @Output() emergenciasCargadas = new EventEmitter<Emergencia[]>();

  emergencias: Emergencia[] = [];
  emergenciasFiltradas: Emergencia[] = [];
  loading = false;
  error = '';

  // Filtro activo para Coordinadores / Auditores
  filtroEstadoSeleccionado = 'TODAS';

  get userRole(): RolUsuario | null {
    return this.authService.userRole();
  }

  get esRepresentanteOng(): boolean {
    return this.userRole === 'REPRESENTANTE_ONG';
  }

  get esCoordinadorRegional(): boolean {
    return this.userRole === 'COORDINADOR_REGIONAL';
  }

  get esAuditorDirectivo(): boolean {
    return this.userRole === 'AUDITOR_DIRECTIVO';
  }

  get esOperadorMunicipal(): boolean {
    return this.userRole === 'OPERADOR_MUNICIPAL';
  }

  /**
   * Determina si se deben forzar únicamente convocatorias abiertas.
   * Si es REPRESENTANTE_ONG, es SIEMPRE estricto a CONVOCATORIA_ABIERTA.
   */
  get debeFiltrarSoloAbiertas(): boolean {
    if (this.soloConvocatoriaAbierta !== undefined) {
      return this.soloConvocatoriaAbierta;
    }
    return false;
  }

  ngOnInit(): void {
    this.cargarEmergencias();
  }

  cargarEmergencias(): void {
    this.loading = true;
    this.error = '';

    const parametroEstado = this.debeFiltrarSoloAbiertas ? 'CONVOCATORIA_ABIERTA' : undefined;

    this.emergenciaService.listar(parametroEstado).subscribe({
      next: (data) => {
        // Garantía estricta de regla de negocio: si el rol es REPRESENTANTE_ONG, SI O SI solo ve CONVOCATORIA_ABIERTA
        if (this.debeFiltrarSoloAbiertas) {
          this.emergencias = data.filter((e) => e.estado === 'CONVOCATORIA_ABIERTA');
        } else {
          this.emergencias = data;
        }

        this.aplicarFiltroTab();
        this.loading = false;
        this.emergenciasCargadas.emit(this.emergencias);
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'No se pudieron sincronizar las emergencias en tiempo real.';
        this.loading = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  cambiarFiltroTab(estadoTab: string): void {
    if (this.debeFiltrarSoloAbiertas) {
      return; // El representante ONG no puede ver otro estado
    }
    this.filtroEstadoSeleccionado = estadoTab;
    this.aplicarFiltroTab();
  }

  private aplicarFiltroTab(): void {
    if (this.debeFiltrarSoloAbiertas || this.filtroEstadoSeleccionado === 'TODAS') {
      this.emergenciasFiltradas = [...this.emergencias];
    } else {
      this.emergenciasFiltradas = this.emergencias.filter(
        (e) => e.estado === this.filtroEstadoSeleccionado
      );
    }
  }

  onSeleccionar(emg: Emergencia): void {
    this.emergenciaSeleccionadaId = emg.id ?? null;
    this.seleccionarEmergencia.emit(emg);
    this.changeDetector.detectChanges();
  }

  esConvocatoriaAbierta(emg: Emergencia): boolean {
    return emg.estado === 'CONVOCATORIA_ABIERTA';
  }

  obtenerIconoTipo(tipo: string): string {
    const t = tipo?.toUpperCase() || '';
    if (t.includes('INUNDA') || t.includes('AGUA')) return 'water_damage';
    if (t.includes('INCENDIO') || t.includes('FUEGO')) return 'local_fire_department';
    if (t.includes('TERREMOTO') || t.includes('SISMO')) return 'landslide';
    return 'warning';
  }

  obtenerClaseGravedad(gravedad: string): string {
    const g = gravedad?.toUpperCase() || '';
    switch (g) {
      case 'CRITICO':
      case 'CRÍTICO':
        return 'bg-red-100 text-red-800 border-red-200';
      case 'ALTO':
        return 'bg-orange-100 text-orange-800 border-orange-200';
      case 'MEDIO':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  }
}
