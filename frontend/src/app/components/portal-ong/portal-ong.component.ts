import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { forkJoin, Subscription, timer } from 'rxjs';
import { EmergenciaService } from '../../services/emergencia.service';
import { OfertaService } from '../../services/oferta.service';
import { OfertaLocal } from '../../models/oferta.model';
import { Emergencia } from '../../models/emergencia.model';
import { TimerBonita } from '../../services/emergencia.service';

export interface LoteDisponible {
  id: string;
  loteId?: number;
  nombre: string;
  descripcion: string;
  demanda: string;
  cantidadRequerida: number;
  cantidadCubierta: number;
  cantidadFaltante: number;
  porcentajeCubierto: number;
  unidad: string;
  icono: string;
}

@Component({
  selector: 'app-portal-ong', standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './portal-ong.component.html', styleUrls: ['./portal-ong.component.scss'],
})
export class PortalOngComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly ofertaService = inject(OfertaService);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly route = inject(ActivatedRoute);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private carga?: Subscription;
  private timerSubscription?: Subscription;

  emergenciaSeleccionada: Emergencia | null = null;
  loadingEmergencia = false;
  guardando = false;
  error = '';
  ofertas: OfertaLocal[] = [];
  editandoId: string | null = null;
  feedbackMensaje = '';
  feedbackTitulo = '';
  mostrarConfirmacion = false;
  ofertaPendienteDeEliminar: string | null = null;
  lotesDisponibles: LoteDisponible[] = [];
  unidadActual = 'unidades';
  timerBonita: TimerBonita | null = null;
  timerBonitaError = '';
  tiempoRestante = 'Consultando Bonita...';

  form = this.fb.group({
    loteId: ['', Validators.required],
    cantidadOfrecida: [1, [Validators.required, Validators.min(1), Validators.pattern(/^[0-9]+$/)]],
    tiempoLlegada: ['2 horas tras adjudicación', Validators.required], observaciones: [''],
  });

  get loteSeleccionado(): LoteDisponible | undefined {
    return this.lotesDisponibles.find(l => String(l.loteId) === this.form.controls.loteId.value);
  }
  private loteEstaCompleto(lote: LoteDisponible): boolean {
    return lote.porcentajeCubierto >= 100 || lote.cantidadFaltante <= 0;
  }
  get cantidadMaxima(): number {
    const anterior = this.ofertas.find(o => o.id === this.editandoId);
    return (this.loteSeleccionado?.cantidadFaltante ?? 0) + (anterior?.cantidadOfrecida ?? 0);
  }
  get puedeOfertar(): boolean {
    return !this.loadingEmergencia && !this.guardando && !!this.loteSeleccionado
      && !this.loteEstaCompleto(this.loteSeleccionado)
      && this.emergenciaSeleccionada?.estado === 'CONVOCATORIA_ABIERTA';
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.cancelarEdicion();
      const id = params.get('id') || this.route.snapshot.queryParamMap.get('id');
      if (id) this.cargarEmergencia(Number(id));
    });
    this.form.controls.loteId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.unidadActual = this.loteSeleccionado?.unidad ?? 'unidades';
    });
  }

  cargarEmergencia(id: number): void {
    this.carga?.unsubscribe();
    this.loadingEmergencia = true;
    this.ofertas = [];
    this.emergenciaSeleccionada = null;
    this.lotesDisponibles = [];
    this.carga = forkJoin({
      emergencia: this.emergenciaService.obtenerPorId(id), ofertas: this.ofertaService.listar(id),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ emergencia, ofertas }) => {
        this.emergenciaSeleccionada = emergencia;
        this.ofertas = ofertas;
        this.cargarTimerBonita(emergencia.id);
        this.lotesDisponibles = (emergencia.lotes ?? []).map(lote => {
          const requerida = Number(lote.cantidadRequerida);
          const cubierta = Number(lote.cantidadCubierta);
          const unidad = this.inferirUnidad(lote.tipoRecurso);
          return {
            id: `lote-${lote.id}`, loteId: lote.id, nombre: lote.tipoRecurso,
            descripcion: `Lote solicitado para ${emergencia.tipoEmergencia}.`,
            demanda: `${requerida} ${unidad}`, cantidadRequerida: requerida,
            cantidadCubierta: cubierta, cantidadFaltante: Math.max(0, requerida - cubierta),
            porcentajeCubierto: requerida > 0 ? Math.min(100, Math.round(cubierta / requerida * 100)) : 0,
            unidad, icono: this.inferirIcono(lote.tipoRecurso),
          };
        });
        if (!this.loteSeleccionado || this.loteEstaCompleto(this.loteSeleccionado)) {
          const loteDisponible = this.lotesDisponibles.find(lote => !this.loteEstaCompleto(lote));
          this.form.patchValue({ loteId: String(loteDisponible?.loteId ?? '') });
        }
        this.loadingEmergencia = false;
        this.cd.detectChanges();
      },
      error: err => {
        this.loadingEmergencia = false;
        this.error = err.status === 401 ? 'La sesión venció. Vuelva a iniciar sesión.'
          : 'No se pudieron consultar los lotes y las ofertas en la base de datos. Reintente la carga.';
        this.cd.detectChanges();
      },
    });
  }

  private cargarTimerBonita(emergenciaId?: number): void {
    this.timerSubscription?.unsubscribe();
    this.timerBonita = null;
    this.timerBonitaError = '';
    this.tiempoRestante = 'Consultando Bonita...';
    if (!emergenciaId) return;

    this.emergenciaService.obtenerTimer(emergenciaId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: timerBonita => {
        this.timerBonita = timerBonita;
        if (!timerBonita.fechaVencimiento) {
          this.timerBonitaError = 'Bonita no devolvió la fecha de vencimiento del caso.';
          this.tiempoRestante = 'Sin fecha en Bonita';
          return;
        }
        this.timerSubscription = timer(0, 1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
          const restante = new Date(timerBonita.fechaVencimiento!).getTime() - Date.now();
          this.tiempoRestante = restante > 0 ? this.formatearRestante(restante) : 'Cerrada';
          this.cd.detectChanges();
        });
      },
      error: () => {
        this.timerBonitaError = 'No se pudo consultar el timer del caso en Bonita.';
        this.tiempoRestante = 'Timer no disponible';
        this.cd.detectChanges();
      },
    });
  }

  private formatearRestante(milisegundos: number): string {
    const totalSegundos = Math.floor(milisegundos / 1000);
    const horas = Math.floor(totalSegundos / 3600);
    const minutos = Math.floor((totalSegundos % 3600) / 60);
    const segundos = totalSegundos % 60;
    return `${String(horas).padStart(2, '0')}:${String(minutos).padStart(2, '0')}:${String(segundos).padStart(2, '0')}`;
  }

  private inferirUnidad(tipo: string): string {
    const match = tipo.match(/\(([^)]+)\)/);
    if (match && match[1]) {
      return match[1].trim();
    }
    const lower = tipo.toLowerCase();
    if (lower.includes('alimento') || lower.includes('racion')) return 'raciones';
    if (lower.includes('medico') || lower.includes('sanitario') || lower.includes('paramedico')) return 'equipos';
    if (lower.includes('kit') || lower.includes('higiene')) return 'kits';
    if (lower.includes('agua') || lower.includes('litro')) return 'litros';
    if (lower.includes('carpa') || lower.includes('refugio')) return 'unidades';
    return 'unidades';
  }

  private inferirIcono(tipo: string): string {
    const lower = tipo.toLowerCase();
    if (lower.includes('medico') || lower.includes('sanitario') || lower.includes('paramedico')) return 'medical_services';
    if (lower.includes('alimento') || lower.includes('comida') || lower.includes('racion')) return 'lunch_dining';
    if (lower.includes('kit') || lower.includes('higiene')) return 'sanitizer';
    if (lower.includes('agua')) return 'water_drop';
    if (lower.includes('carpa') || lower.includes('refugio')) return 'holiday_village';
    if (lower.includes('generador') || lower.includes('energia')) return 'bolt';
    return 'inventory_2';
  }


  seleccionarLoteCard(lote: LoteDisponible): void {
    if (this.editandoId || this.guardando || this.loteEstaCompleto(lote)) return;
    this.form.patchValue({ loteId: String(lote.loteId) });
    this.unidadActual = lote.unidad;
  }

  seleccionarLoteDesdeFormulario(loteId: string | null): void {
    const lote = this.lotesDisponibles.find(item => String(item.loteId) === loteId);
    if (lote && this.loteEstaCompleto(lote)) {
      this.form.patchValue({ loteId: '' }, { emitEvent: false });
      this.unidadActual = 'unidades';
    }
  }
  completarRestante(): void {
    if (this.cantidadMaxima > 0) this.form.patchValue({ cantidadOfrecida: this.cantidadMaxima });
  }

  onSubmit(): void {
    if (!this.puedeOfertar || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const val = this.form.getRawValue();
    const cantidad = Number(val.cantidadOfrecida);
    if (!Number.isInteger(cantidad) || cantidad < 1 || cantidad > this.cantidadMaxima) {
      this.error = `La cantidad debe ser un entero entre 1 y ${this.cantidadMaxima}.`; return;
    }
    const emergenciaId = this.emergenciaSeleccionada!.id!;
    const payload: Partial<OfertaLocal> = {
      emergenciaId, loteId: this.loteSeleccionado!.loteId, cantidadOfrecida: cantidad,
      unidad: this.unidadActual,
      tiempoLlegada: val.tiempoLlegada ?? '',
      observaciones: val.observaciones ?? '',
    };
    this.guardando = true;
    this.error = '';
    this.feedbackMensaje = '';
    const operacion = this.editandoId
      ? this.ofertaService.actualizarOferta(this.editandoId, payload)
      : this.ofertaService.guardarOferta(payload);
    operacion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: oferta => {
        this.guardando = false;
        this.feedbackTitulo = 'Oferta guardada';
        this.feedbackMensaje = `Oferta #${oferta.id} registrada en la base de datos.`;
        this.cancelarEdicion();
        this.cargarEmergencia(emergenciaId);
      },
      error: err => {
        this.guardando = false;
        this.error = err.error?.message ?? 'No se pudo guardar la oferta. No se confirmó ningún cambio.';
        if (err.status === 409) {
          this.cancelarEdicion();
          this.cargarEmergencia(emergenciaId);
        }
        this.cd.detectChanges();
      },
    });
  }

  iniciarEdicion(oferta: OfertaLocal): void {
    if (this.emergenciaSeleccionada?.estado !== 'CONVOCATORIA_ABIERTA' || this.guardando || oferta.estado === 'Retirada') return;
    this.editandoId = oferta.id;
    this.form.patchValue({ loteId: String(oferta.loteId), cantidadOfrecida: oferta.cantidadOfrecida,
      tiempoLlegada: oferta.tiempoLlegada, observaciones: oferta.observaciones || '' });
    this.form.controls.loteId.disable();
    this.unidadActual = oferta.unidad;
  }
  cancelarEdicion(): void {
    this.editandoId = null;
    this.form.controls.loteId.enable();
    const loteDisponible = this.lotesDisponibles.find(lote => !this.loteEstaCompleto(lote));
    this.form.reset({ loteId: String(loteDisponible?.loteId ?? ''), cantidadOfrecida: 1,
      tiempoLlegada: '2 horas tras adjudicación', observaciones: '' });
  }
  eliminarOferta(id: string): void {
    if (this.emergenciaSeleccionada?.estado !== 'CONVOCATORIA_ABIERTA' || this.guardando) return;
    this.ofertaPendienteDeEliminar = id;
    this.mostrarConfirmacion = true;
  }
  cerrarConfirmacion(): void {
    this.mostrarConfirmacion = false;
    this.ofertaPendienteDeEliminar = null;
  }
  confirmarEliminacion(): void {
    const id = this.ofertaPendienteDeEliminar;
    const emergenciaId = this.emergenciaSeleccionada?.id;
    if (this.emergenciaSeleccionada?.estado !== 'CONVOCATORIA_ABIERTA' || !id || !emergenciaId || this.guardando) return;
    this.guardando = true;
    this.error = '';
    this.feedbackMensaje = '';
    this.ofertaService.eliminarOferta(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.guardando = false;
        this.cerrarConfirmacion();
        this.cancelarEdicion();
        this.feedbackTitulo = 'Oferta retirada';
        this.feedbackMensaje = 'El retiro quedó registrado en la base de datos y se liberó su aporte al lote.';
        this.cargarEmergencia(emergenciaId);
      },
      error: err => {
        this.guardando = false;
        this.cerrarConfirmacion();
        this.error = err.error?.message ?? 'No se pudo retirar la oferta.';
        this.cd.detectChanges();
      },
    });
  }
}
