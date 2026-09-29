import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Emergencia } from '../../models/emergencia.model';
import { OfertaLocal } from '../../models/oferta.model';
import { EmergenciaService } from '../../services/emergencia.service';
import { OfertaService } from '../../services/oferta.service';

interface LoteDisponible {
  id: number | string;
  nombre: string;
  descripcion: string;
  demanda: string;
  unidad: string;
  icono: string;
}

@Component({
  selector: 'app-portal-ong',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './portal-ong.component.html',
  styleUrls: ['./portal-ong.component.scss'],
})
export class PortalOngComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly ofertaService = inject(OfertaService);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly cd = inject(ChangeDetectorRef);

  ofertas: OfertaLocal[] = [];
  emergenciaActiva?: Emergencia;
  lotesDisponibles: LoteDisponible[] = [];
  loadingConvocatoria = false;
  errorConvocatoria = '';
  editandoId: string | null = null;
  feedbackMensaje = '';
  feedbackTitulo = '';
  mostrarConfirmacion = false;
  ofertaPendienteDeEliminar: string | null = null;

  segundosRestantes = 3 * 3600 + 42 * 60 + 19;
  timerString = '03h 42m 19s';
  private timerInterval: ReturnType<typeof setInterval> | undefined;
  unidadActual = 'unidades';

  form = this.fb.group({
    loteNombre: ['', Validators.required],
    cantidadOfrecida: [2, [Validators.required, Validators.min(1)]],
    modalidad: ['Individual', Validators.required],
    ongAsociada: [''],
    tiempoLlegada: ['2 horas tras adjudicación', Validators.required],
    observaciones: [''],
  });

  ngOnInit(): void {
    this.iniciarTimer();
    this.cargarConvocatoriaActiva();
    this.ofertas = this.ofertaService.obtenerTodas();
    this.ofertaService.ofertas$.subscribe((lista) => {
      this.ofertas = lista;
      this.cd.detectChanges();
    });
    this.form.get('loteNombre')?.valueChanges.subscribe((nombre) => {
      const lote = this.lotesDisponibles.find((item) => item.nombre === nombre);
      if (lote) this.unidadActual = lote.unidad;
    });
  }

  ngOnDestroy(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  private cargarConvocatoriaActiva(): void {
    this.loadingConvocatoria = true;
    this.emergenciaService.listar('CONVOCATORIA_ABIERTA').subscribe({
      next: (emergencias) => {
        this.emergenciaActiva = emergencias[0];
        const lotesUnicos = new Map(
          (this.emergenciaActiva?.lotes ?? []).map((lote) => {
            const nombre = lote.tipoRecurso.replace(/(?: \(unidades\))+$/g, '');
            return [`${nombre}|${lote.cantidadRequerida}`, { ...lote, tipoRecurso: nombre }];
          })
        );
        this.lotesDisponibles = Array.from(lotesUnicos.values()).map((lote) => ({
          id: lote.id ?? `lote-${lote.tipoRecurso}`,
          nombre: lote.tipoRecurso,
          descripcion: `Recurso requerido para ${this.emergenciaActiva?.tipoEmergencia || 'la emergencia activa'}.`,
          demanda: `${lote.cantidadRequerida} unidades`,
          unidad: 'unidades',
          icono: this.obtenerIconoLote(lote.tipoRecurso),
        })) ?? [];
        const primerLote = this.lotesDisponibles[0];
        if (primerLote) {
          this.form.patchValue({ loteNombre: primerLote.nombre });
          this.unidadActual = primerLote.unidad;
        }
        this.loadingConvocatoria = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.errorConvocatoria = 'No se pudo cargar la convocatoria publicada. Verifique que el backend esté disponible.';
        this.loadingConvocatoria = false;
        this.cd.detectChanges();
      },
    });
  }

  private obtenerIconoLote(nombre: string): string {
    const nombreNormalizado = nombre.toLowerCase();
    if (nombreNormalizado.includes('alimento') || nombreNormalizado.includes('agua')) return 'lunch_dining';
    if (nombreNormalizado.includes('sanitari') || nombreNormalizado.includes('medic')) return 'medical_services';
    return 'volunteer_activism';
  }

  private iniciarTimer(): void {
    this.timerInterval = setInterval(() => {
      if (this.segundosRestantes <= 0) return;
      this.segundosRestantes--;
      const h = Math.floor(this.segundosRestantes / 3600);
      const m = Math.floor((this.segundosRestantes % 3600) / 60);
      const s = this.segundosRestantes % 60;
      this.timerString = `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`;
      this.cd.detectChanges();
    }, 1000);
  }

  get esConsorcio(): boolean {
    return this.form.get('modalidad')?.value === 'Consorcio';
  }

  seleccionarLoteCard(lote: LoteDisponible): void {
    this.form.patchValue({ loteNombre: lote.nombre });
    this.unidadActual = lote.unidad;
    document.getElementById('seccion-formulario-oferta')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const val = this.form.getRawValue();
    const lote = val.loteNombre ?? '';
    const cantidad = Number(val.cantidadOfrecida ?? 1);
    const modalidad = (val.modalidad as 'Individual' | 'Consorcio') ?? 'Individual';
    const oferta = {
      loteNombre: lote,
      cantidadOfrecida: cantidad,
      unidad: this.unidadActual,
      modalidad,
      ongAsociada: modalidad === 'Consorcio' ? (val.ongAsociada ?? '') : undefined,
      tiempoLlegada: val.tiempoLlegada ?? 'Inmediata',
      observaciones: val.observaciones ?? '',
    };

    if (this.editandoId) {
      this.ofertaService.actualizarOferta(this.editandoId, oferta);
      this.feedbackTitulo = '¡Oferta Rectificada con Éxito (Versión 2)!';
      this.feedbackMensaje = `Se actualizó la postulación para ${lote}.`;
      this.editandoId = null;
    } else {
      this.ofertaService.guardarOferta(oferta);
      this.feedbackTitulo = '¡Oferta Registrada Exitosamente!';
      this.feedbackMensaje = `La propuesta para ${lote} fue registrada para la convocatoria activa.`;
    }

    this.form.reset({
      loteNombre: this.lotesDisponibles[0]?.nombre ?? '',
      cantidadOfrecida: 1,
      modalidad: 'Individual',
      ongAsociada: '',
      tiempoLlegada: '2 horas tras adjudicación',
      observaciones: '',
    });
    this.cd.detectChanges();
  }

  iniciarEdicion(oferta: OfertaLocal): void {
    this.editandoId = oferta.id;
    this.form.patchValue({
      loteNombre: oferta.loteNombre,
      cantidadOfrecida: oferta.cantidadOfrecida,
      modalidad: oferta.modalidad,
      ongAsociada: oferta.ongAsociada || '',
      tiempoLlegada: oferta.tiempoLlegada,
      observaciones: oferta.observaciones || '',
    });
    this.unidadActual = oferta.unidad;
    document.getElementById('seccion-formulario-oferta')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    this.cd.detectChanges();
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.form.reset({
      loteNombre: this.lotesDisponibles[0]?.nombre ?? '',
      cantidadOfrecida: 1,
      modalidad: 'Individual',
      ongAsociada: '',
      tiempoLlegada: '2 horas tras adjudicación',
      observaciones: '',
    });
    this.cd.detectChanges();
  }

  eliminarOferta(id: string): void {
    this.ofertaPendienteDeEliminar = id;
    this.mostrarConfirmacion = true;
  }

  cerrarConfirmacion(): void {
    this.mostrarConfirmacion = false;
    this.ofertaPendienteDeEliminar = null;
  }

  confirmarEliminacion(): void {
    const id = this.ofertaPendienteDeEliminar;
    if (!id) return;
    this.ofertaService.eliminarOferta(id);
    if (this.editandoId === id) this.cancelarEdicion();
    this.cerrarConfirmacion();
    this.cd.detectChanges();
  }
}
