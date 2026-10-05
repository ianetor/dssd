import { CommonModule } from '@angular/common';
import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
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
import { OfertaService } from '../../services/oferta.service';
import { TextoCodigoPipe } from '../../pipes/texto-codigo.pipe';
import { forkJoin, of, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RelojConvocatoriaService } from '../../services/reloj-convocatoria.service';

@Component({
  selector: 'app-emergencias-list',
  standalone: true,
  imports: [CommonModule, RouterModule, TextoCodigoPipe],
  templateUrl: './emergencias-list.component.html',
  styleUrls: ['./emergencias-list.component.scss'],
})
export class EmergenciasListComponent implements OnInit {
  protected readonly emergenciaService = inject(EmergenciaService);
  protected readonly authService = inject(AuthService);
  private readonly ofertaService = inject(OfertaService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloj = inject(RelojConvocatoriaService);

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
  private participaciones = new Set<number>();
  private cargando = false;

  get filtrosEstado(): { valor: string; etiqueta: string }[] {
    if (this.esRepresentanteOng) {
      return [
        { valor: 'CONVOCATORIA_ABIERTA', etiqueta: 'Convocatoria abierta' },
        { valor: 'CONVOCATORIA_CERRADA', etiqueta: 'Convocatoria cerrada' },
        { valor: 'PARTICIPACION', etiqueta: 'Convocatorias con mi participación' },
      ];
    }
    return [
      { valor: 'TODAS', etiqueta: 'Todas' },
      { valor: 'CONVOCATORIA_ABIERTA', etiqueta: 'Convocatoria abierta' },
      { valor: 'REGISTRADA', etiqueta: 'Registradas' },
      { valor: 'CONVOCATORIA_CERRADA', etiqueta: 'Convocatoria cerrada' },
      { valor: 'PUBLICACION_PENDIENTE', etiqueta: 'Pendientes' },
    ];
  }

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

  /** Las ONGs disponen siempre de abiertas, cerradas y participación. */
  get debeFiltrarSoloAbiertas(): boolean {
    return !this.esRepresentanteOng && this.soloConvocatoriaAbierta === true;
  }

  ngOnInit(): void {
    this.cargarEmergencias();
    timer(5000, 5000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!this.cargando && this.emergencias.some(e => e.estado === 'CONVOCATORIA_ABIERTA' || e.avanceCoberturaEstado === 'PENDIENTE')) {
        this.cargarEmergencias(true);
      }
    });
  }

  cargarEmergencias(enSegundoPlano = false): void {
    if (this.cargando) return;
    this.cargando = true;
    this.loading = !enSegundoPlano;
    this.error = '';
    if (this.esRepresentanteOng && !this.filtrosEstado.some(f => f.valor === this.filtroEstadoSeleccionado)) {
      this.filtroEstadoSeleccionado = 'CONVOCATORIA_ABIERTA';
    }

    const parametroEstado = this.debeFiltrarSoloAbiertas ? 'CONVOCATORIA_ABIERTA' : undefined;

    forkJoin({
      emergencias: this.emergenciaService.listar(parametroEstado),
      ofertas: this.esRepresentanteOng ? this.ofertaService.listar() : of([]),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ emergencias: data, ofertas }) => {
        data.forEach(e => this.reloj.sincronizar(e.horaServidor));
        this.participaciones = new Set(ofertas.flatMap(o => o.emergenciaId == null ? [] : [o.emergenciaId]));
        if (this.esRepresentanteOng) {
          this.emergencias = data.filter(e => ['CONVOCATORIA_ABIERTA', 'CONVOCATORIA_CERRADA'].includes(e.estado));
        } else if (this.debeFiltrarSoloAbiertas) {
          this.emergencias = data.filter((e) => e.estado === 'CONVOCATORIA_ABIERTA');
        } else {
          this.emergencias = data;
        }

        this.aplicarFiltroTab();
        this.loading = false;
        this.cargando = false;
        this.emergenciasCargadas.emit(this.emergencias);
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'No se pudieron sincronizar las emergencias en tiempo real.';
        this.loading = false;
        this.cargando = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  cambiarFiltroTab(estadoTab: string): void {
    if (this.debeFiltrarSoloAbiertas) {
      return;
    }
    if (!this.filtrosEstado.some(f => f.valor === estadoTab)) return;
    this.filtroEstadoSeleccionado = estadoTab;
    this.aplicarFiltroTab();
  }

  private aplicarFiltroTab(): void {
    if (this.esRepresentanteOng && this.filtroEstadoSeleccionado === 'PARTICIPACION') {
      this.emergenciasFiltradas = this.emergencias.filter(e => e.id != null && this.participaciones.has(e.id));
    } else if (this.debeFiltrarSoloAbiertas || this.filtroEstadoSeleccionado === 'TODAS') {
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

  identificarEmergencia(_: number, emg: Emergencia): number | Emergencia {
    return emg.id ?? emg;
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
