import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, exhaustMap, merge, switchMap, tap, timer } from 'rxjs';
import { EstadoEjecucion } from '../../models/ejecucion.model';
import { TextoCodigoPipe } from '../../pipes/texto-codigo.pipe';
import { AuthService } from '../../services/auth.service';
import { EjecucionService } from '../../services/ejecucion.service';

/** Tarea "Monitorear despliegue y ejecucion": tablero del Centro Coordinador (el auditor solo consulta). */
@Component({
  selector: 'app-ejecucion-monitor', standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, TextoCodigoPipe],
  templateUrl: './ejecucion-monitor.component.html', styleUrl: './ejecucion-monitor.component.scss',
})
export class EjecucionMonitorComponent {
  private readonly api = inject(EjecucionService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly refrescar = new Subject<void>();
  private id = 0;

  readonly datos = signal<EstadoEjecucion | null>(null);
  readonly cargando = signal(true);
  readonly enviando = signal(false);
  readonly error = signal('');
  readonly errorCierre = signal('');
  readonly aviso = signal('');
  readonly confirmando = signal(false);
  readonly esCoordinador = computed(() => this.auth.userRole() === 'COORDINADOR_REGIONAL');
  readonly motivo = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1000)] });

  constructor() {
    this.route.paramMap.pipe(switchMap(params => {
      this.id = Number(params.get('id'));
      this.datos.set(null); this.cargando.set(true); this.error.set(''); this.aviso.set(''); this.errorCierre.set('');
      return merge(timer(0, 5000), this.refrescar).pipe(exhaustMap(() => this.api.monitorear(this.id).pipe(
        tap(d => { this.datos.set(d); this.cargando.set(false); this.error.set(''); }),
        catchError((e: HttpErrorResponse) => { this.cargando.set(false); this.error.set(this.mensaje(e)); return EMPTY; }),
      )));
    }), takeUntilDestroyed(this.destroy)).subscribe();
  }

  pendientes(d: EstadoEjecucion) { return d.totalOngs - d.ongsFinalizadas; }
  porcentaje(d: EstadoEjecucion) { return d.totalOngs ? Math.round(100 * d.ongsFinalizadas / d.totalOngs) : 0; }

  /** Con ONGs en curso el cierre es forzado y exige motivo (misma regla que valida el backend). */
  puedeEnviar(d: EstadoEjecucion) {
    if (this.enviando() || this.motivo.invalid) return false;
    return d.estado === 'CIERRE_PENDIENTE' || d.puedeCerrar || this.motivo.value.trim().length > 0;
  }

  pedirConfirmacion(d: EstadoEjecucion) {
    this.errorCierre.set('');
    if (this.puedeEnviar(d)) this.confirmando.set(true);
  }

  cerrar(d: EstadoEjecucion) {
    if (this.enviando()) return;
    this.enviando.set(true); this.confirmando.set(false); this.errorCierre.set('');
    this.api.cerrar(d.emergenciaId, !d.puedeCerrar, this.motivo.value.trim())
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: actualizado => {
          this.enviando.set(false); this.datos.set(actualizado);
          this.aviso.set('Monitoreo finalizado. Bonita está liberando los recursos en el Sistema Nacional.');
          this.refrescar.next();
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(false); this.errorCierre.set(this.mensaje(e)); this.refrescar.next();
        },
      });
  }

  private mensaje(e: HttpErrorResponse) {
    if (e.status === 401) { this.auth.limpiarSesion(); return ''; }
    if (e.status === 403) return 'No tiene acceso al monitoreo de esta emergencia.';
    return e.error?.message || e.error?.detail
      || (e.status === 409 ? 'La emergencia todavía no está en ejecución o su estado cambió.'
        : 'No se pudo completar la operación. Se volverá a consultar automáticamente.');
  }
}
