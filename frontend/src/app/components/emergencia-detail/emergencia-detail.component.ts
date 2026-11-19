import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subscription, timer, catchError, exhaustMap, timeout } from 'rxjs';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmergenciaService } from '../../services/emergencia.service';
import { Emergencia, LotePayload } from '../../models/emergencia.model';
import { ConvocatoriaTimerComponent } from '../convocatoria-timer/convocatoria-timer.component';
import { RelojConvocatoriaService } from '../../services/reloj-convocatoria.service';
import { CatalogoNacionalService, RecursoNacional } from '../../services/catalogo-nacional.service';

/** Resultado de comparar la cantidad pedida con la disponibilidad nacional. */
export interface EvaluacionDisponibilidad {
  nivel: 'ok' | 'aviso' | 'bloqueo';
  mensaje: string;
}

interface LoteItem {
  nombre: string;
  cantidad: number;
  unidad: string;
  prioridad: 'Urgente' | 'Alta' | 'Media';
  descripcion?: string;
}

@Component({
  selector: 'app-emergencia-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, ConvocatoriaTimerComponent],
  templateUrl: './emergencia-detail.component.html',
  styleUrls: ['./emergencia-detail.component.scss'],
})
export class EmergenciaDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly emergenciaService = inject(EmergenciaService);
  private readonly fb = inject(FormBuilder);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloj = inject(RelojConvocatoriaService);
  private readonly catalogoNacional = inject(CatalogoNacionalService);

  // Catálogo del Sistema Nacional: los lotes usan nombres que la validación nacional reconoce.
  catalogo: RecursoNacional[] = [];
  catalogoEstado: 'cargando' | 'ok' | 'error' = 'cargando';
  /** Texto libre: respaldo si el Sistema Nacional no responde o el recurso no está en el catálogo. */
  nombreManual = false;
  private seguimiento?: Subscription;

  emergencia?: Emergencia;
  listaEmergencias: Emergencia[] = [];
  loading = false;
  error = '';
  isSubmitting = false;
  publicadoExitoso = false;
  mostrarConfirmacion = false;
  lotePendienteDeEliminar: number | null = null;

  // Lotes listados localmente para desglose antes de publicar
  lotesDesglosados: LoteItem[] = [];

  // Configuración de Convocatoria para Bonita BPM
  duracionCantidad = 4;
  duracionUnidad: 'minutos' | 'horas' = 'horas';

  get duracionMinutos(): number {
    return Number(this.duracionCantidad) * (this.duracionUnidad === 'horas' ? 60 : 1);
  }

  get duracionValida(): boolean {
    return Number.isInteger(Number(this.duracionCantidad)) && Number(this.duracionCantidad) > 0
      && Number.isSafeInteger(this.duracionMinutos) && this.duracionMinutos <= 2147483647;
  }

  get publicacionPendiente(): boolean { return this.emergencia?.estado === 'PUBLICACION_PENDIENTE'; }
  get convocatoriaCerrada(): boolean { return this.emergencia?.estado === 'CONVOCATORIA_CERRADA'; }
  private get requiereSeguimiento(): boolean {
    return this.publicacionPendiente || this.emergencia?.avanceCoberturaEstado === 'PENDIENTE'
      || (this.emergencia?.estado === 'CONVOCATORIA_ABIERTA'
      && !!this.emergencia.fechaVencimientoConvocatoria);
  }
  get edicionBloqueada(): boolean {
    return this.loading || this.isSubmitting || !this.emergencia || this.emergencia.estado !== 'REGISTRADA';
  }

  private recibirEmergencia(emergencia: Emergencia): void {
    this.reloj.sincronizar(emergencia.horaServidor);
    this.emergencia = emergencia;
    this.publicadoExitoso = emergencia.estado === 'CONVOCATORIA_ABIERTA';
    if (emergencia.duracionConvocatoriaMinutos != null) {
      this.duracionCantidad = emergencia.duracionConvocatoriaMinutos;
      this.duracionUnidad = 'minutos';
    }
    if (!this.requiereSeguimiento) this.seguimiento?.unsubscribe();
  }

  private seguirPublicacion(id: number): void {
    this.seguimiento?.unsubscribe();
    if (!this.requiereSeguimiento) return;
    this.seguimiento = timer(2000, 5000).pipe(
      exhaustMap(() => this.emergenciaService.obtenerPorId(id).pipe(
        timeout(10000),
        catchError(() => {
          this.error = 'No se pudo consultar el estado de la convocatoria. Se volverá a intentar automáticamente.';
          this.cd.detectChanges();
          return EMPTY;
        }),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(emergencia => {
      this.error = '';
      this.recibirEmergencia(emergencia);
      this.cd.detectChanges();
    });
  }

  loteForm = this.fb.group({
    nombre: ['', [
      Validators.required,
      Validators.maxLength(255),
      Validators.pattern(/^[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ\s\/\-,\.()]+$/)
    ]],
    cantidad: [1, [Validators.required, Validators.min(1)]],
    unidad: ['unidades', [Validators.required, Validators.maxLength(50)]],
    prioridad: ['Alta', Validators.required],
    descripcion: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.cargarCatalogo();
    this.loteForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cd.detectChanges());
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.seguimiento?.unsubscribe();
      const idParam = params.get('id');
      if (idParam) {
        this.cargar(Number(idParam));
      } else {
        this.cargarUltimaOLista();
      }
    });
  }

  cargar(id: number): void {
    this.loading = true;
    this.error = '';
    this.emergenciaService.obtenerPorId(id).subscribe({
      next: (emergencia) => {
        this.recibirEmergencia(emergencia);
        this.inicializarLotesDesdeEmergencia(emergencia);
        this.seguirPublicacion(id);
        this.loading = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.error = `No se pudo cargar la emergencia #${id}. Recargá la página para reintentar.`;
        this.emergencia = undefined;
        this.loading = false;
        this.cd.detectChanges();
      },
    });
  }

  cargarUltimaOLista(): void {
    this.loading = true;
    this.emergenciaService.listar().subscribe({
      next: (list) => {
        this.listaEmergencias = list;
        if (list && list.length > 0) {
          this.recibirEmergencia(list[0]);
          this.inicializarLotesDesdeEmergencia(list[0]);
          this.seguirPublicacion(list[0].id!);
        } else {
          this.emergencia = undefined;
          this.error = 'No hay emergencias disponibles para publicar.';
        }
        this.loading = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.emergencia = undefined;
        this.error = 'No se pudieron cargar las emergencias. Recargá la página para reintentar.';
        this.loading = false;
        this.cd.detectChanges();
      },
    });
  }

  inicializarLotesDesdeEmergencia(emg: Emergencia): void {
    this.lotesDesglosados = [];
    if (emg.lotes && emg.lotes.length > 0) {
      emg.lotes.forEach((l) => {
        this.lotesDesglosados.push({
          nombre: l.tipoRecurso,
          cantidad: l.cantidadRequerida,
          unidad: 'unidades',
          prioridad: 'Alta',
          descripcion: `Lote asignado a ${emg.tipoEmergencia}`,
        });
      });
    }
  }

  cargarCatalogo(): void {
    this.catalogoEstado = 'cargando';
    this.catalogoNacional.recursos().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (recursos) => {
        this.catalogo = recursos;
        this.catalogoEstado = 'ok';
        this.nombreManual = recursos.length === 0;
        this.cd.detectChanges();
      },
      error: () => {
        // Sin catálogo no se bloquea al coordinador: puede escribir el nombre a mano.
        this.catalogo = [];
        this.catalogoEstado = 'error';
        this.nombreManual = true;
        this.cd.detectChanges();
      },
    });
  }

  alternarNombreManual(): void {
    this.nombreManual = !this.nombreManual;
    this.loteForm.get('nombre')?.reset('');
  }

  /** Recurso del catálogo que coincide con el nombre elegido, si existe. */
  get recursoSeleccionado(): RecursoNacional | undefined {
    const nombre = (this.loteForm.value.nombre ?? '').trim().toLowerCase();
    return nombre ? this.catalogo.find((r) => r.tipoRecurso.trim().toLowerCase() === nombre) : undefined;
  }

  /** Evaluación de la cantidad cargada contra el recurso elegido; null si no hay nada que comparar. */
  get disponibilidadLote(): EvaluacionDisponibilidad | null {
    const recurso = this.recursoSeleccionado;
    const cantidad = Number(this.loteForm.value.cantidad);
    if (!recurso || !Number.isFinite(cantidad) || cantidad < 1) return null;
    return this.evaluarDisponibilidad(cantidad, recurso.cantidadDisponible);
  }

  /**
   * Política del formulario de lotes: qué hacer cuando el coordinador pide una cantidad
   * frente a lo que hay disponible a nivel nacional.
   *  - 'ok': no se muestra nada.
   *  - 'aviso': se muestra el mensaje pero se permite agregar el lote.
   *  - 'bloqueo': se muestra el mensaje y no se permite agregar el lote.
   */
  evaluarDisponibilidad(cantidad: number, disponible: number): EvaluacionDisponibilidad {
    if (cantidad <= disponible) return { nivel: 'ok', mensaje: '' };
    // Mismo umbral que la validación de cobertura del backend: al menos el 50 % del lote.
    const minimo = Math.ceil(cantidad / 2);
    // Nunca se bloquea: lo comprometido en otras emergencias puede liberarse antes de que cierre la convocatoria.
    if (disponible <= 0) {
      return {
        nivel: 'aviso',
        mensaje: 'Hoy no hay unidades disponibles a nivel nacional. Podés publicar el lote igual: '
          + 'se cubrirá si otras emergencias liberan recursos antes del cierre.',
      };
    }
    if (disponible < minimo) {
      return {
        nivel: 'aviso',
        mensaje: `Hay ${disponible} disponibles y el mínimo para validar el lote es ${minimo}: `
          + 'con la disponibilidad actual no alcanzaría la cobertura mínima.',
      };
    }
    return {
      nivel: 'aviso',
      mensaje: `Hay ${disponible} disponibles: el lote podría cubrirse solo en parte (mínimo ${minimo}).`,
    };
  }

  agregarLote(): void {
    if (this.edicionBloqueada) return;
    if (this.loteForm.invalid) {
      this.loteForm.markAllAsTouched();
      return;
    }
    if (this.disponibilidadLote?.nivel === 'bloqueo') return;

    const val = this.loteForm.value;
    const nombreNuevo = (val.nombre ?? '').trim();

    // Validar que no exista ya un lote con el mismo nombre (case-insensitive)
    const yaExiste = this.lotesDesglosados.some(
      (l) => l.nombre.trim().toLowerCase() === nombreNuevo.toLowerCase()
    );
    if (yaExiste) {
      this.loteForm.get('nombre')?.setErrors({ duplicado: true });
      this.loteForm.markAllAsTouched();
      return;
    }

    const nuevo: LoteItem = {
      nombre: nombreNuevo,
      cantidad: Number(val.cantidad ?? 1),
      unidad: val.unidad ?? 'unidades',
      prioridad: (val.prioridad as 'Urgente' | 'Alta' | 'Media') ?? 'Alta',
      descripcion: val.descripcion || 'Lote incorporado por el Centro Coordinador Regional.',
    };

    this.lotesDesglosados.push(nuevo);
    this.loteForm.reset({
      nombre: '',
      cantidad: 1,
      unidad: 'unidades',
      prioridad: 'Alta',
      descripcion: '',
    });
    this.cd.detectChanges();
  }

  eliminarLote(index: number): void {
    if (this.edicionBloqueada) return;
    const lote = this.lotesDesglosados[index];
    if (!lote) {
      return;
    }

    this.lotePendienteDeEliminar = index;
    this.mostrarConfirmacion = true;
  }

  cerrarConfirmacion(): void {
    this.mostrarConfirmacion = false;
    this.lotePendienteDeEliminar = null;
  }

  confirmarEliminacion(): void {
    if (this.edicionBloqueada) return;
    const index = this.lotePendienteDeEliminar;
    if (index === null) {
      return;
    }

    this.lotesDesglosados.splice(index, 1);
    this.cerrarConfirmacion();
    this.cd.detectChanges();
  }

  editarCantidadLote(index: number): void {
    if (this.edicionBloqueada) return;
    const lote = this.lotesDesglosados[index];
    const nuevaCant = prompt(`Ingrese nueva cantidad para '${lote.nombre}':`, String(lote.cantidad));
    if (nuevaCant && !isNaN(Number(nuevaCant)) && Number(nuevaCant) > 0) {
      lote.cantidad = Number(nuevaCant);
      this.cd.detectChanges();
    }
  }

  publicarConvocatoriaBonita(): void {
    if (this.edicionBloqueada) return;
    if (!this.duracionValida) {
      this.error = 'Ingresá una duración entera positiva, hasta 2147483647 minutos.';
      return;
    }
    if (!this.emergencia?.id) {
      this.error = 'No hay una emergencia seleccionada para publicar.';
      return;
    }

    if (this.lotesDesglosados.length === 0) {
      this.error = 'Debe definir al menos un lote de necesidades antes de publicar.';
      return;
    }

    this.isSubmitting = true;
    this.error = '';

    const payload: LotePayload[] = this.lotesDesglosados.map((l) => ({
      tipoRecurso: `${l.nombre} (${l.unidad})`,
      cantidadRequerida: l.cantidad,
    }));

    this.emergenciaService.publicarLotes(this.emergencia.id, payload, this.duracionMinutos).subscribe({
      next: (actualizada) => {
        this.recibirEmergencia(actualizada);
        this.isSubmitting = false;
        this.seguirPublicacion(actualizada.id!);
        this.cd.detectChanges();
      },
      error: (err) => {
        this.isSubmitting = false;
        this.publicadoExitoso = false;
        this.error = err.status === 409
          ? 'La emergencia ya tiene una publicación registrada. Recargá para consultar su estado.'
          : 'No se pudo confirmar la solicitud de publicación. Podés reintentar sin duplicar los lotes.';
        this.cd.detectChanges();
      },
    });
  }

}
