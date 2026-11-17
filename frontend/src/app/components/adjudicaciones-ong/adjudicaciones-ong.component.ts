import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, exhaustMap, merge, tap, timer } from 'rxjs';
import { AdjudicacionOng } from '../../models/ejecucion.model';
import { TextoCodigoPipe } from '../../pipes/texto-codigo.pipe';
import { AuthService } from '../../services/auth.service';
import { EjecucionService } from '../../services/ejecucion.service';

/** Notificaciones de adjudicación de la ONG y marcado de actividades finalizadas. */
@Component({
  selector: 'app-adjudicaciones-ong', standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TextoCodigoPipe],
  templateUrl: './adjudicaciones-ong.component.html', styleUrl: './adjudicaciones-ong.component.scss',
})
export class AdjudicacionesOngComponent {
  private readonly api = inject(EjecucionService);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly refrescar = new Subject<void>();

  readonly lista = signal<AdjudicacionOng[] | null>(null);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly enviando = signal<number | null>(null);
  /** Emergencia cuya finalización se está confirmando. */
  readonly confirmando = signal<number | null>(null);
  observaciones: Record<number, string> = {};

  constructor() {
    merge(timer(0, 10000), this.refrescar).pipe(
      exhaustMap(() => this.api.adjudicaciones().pipe(
        tap(datos => { this.lista.set(datos); this.error.set(''); }),
        catchError((e: HttpErrorResponse) => { this.error.set(this.mensaje(e)); return EMPTY; }),
      )), takeUntilDestroyed(this.destroy)).subscribe();
  }

  enCurso(a: AdjudicacionOng) {
    return a.participacion.estado === 'EN_CURSO' && a.estadoEmergencia === 'EN_EJECUCION';
  }

  finalizar(a: AdjudicacionOng) {
    if (this.enviando() !== null) return;
    this.enviando.set(a.emergenciaId); this.error.set(''); this.aviso.set('');
    this.api.finalizar(a.emergenciaId, (this.observaciones[a.emergenciaId] ?? '').trim())
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: () => {
          this.enviando.set(null); this.confirmando.set(null);
          this.aviso.set(`Actividades de la emergencia #${a.emergenciaId} informadas como finalizadas.`);
          this.refrescar.next();
        },
        error: (e: HttpErrorResponse) => {
          this.enviando.set(null); this.confirmando.set(null); this.error.set(this.mensaje(e)); this.refrescar.next();
        },
      });
  }

  private mensaje(e: HttpErrorResponse) {
    if (e.status === 401) { this.auth.limpiarSesion(); return ''; }
    return e.error?.message || e.error?.detail
      || (e.status === 409 ? 'La emergencia ya no admite cambios de ejecución.'
        : 'No se pudo completar la operación. Intentá nuevamente.');
  }
}
