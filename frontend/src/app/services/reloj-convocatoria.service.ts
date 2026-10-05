import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { Emergencia } from '../models/emergencia.model';

/** Hora del servidor más tiempo transcurrido, independiente del reloj del equipo. */
@Injectable({ providedIn: 'root' })
export class RelojConvocatoriaService {
  private readonly destroyRef = inject(DestroyRef);
  private referenciaServidor: number | null = null;
  private referenciaMonotona = 0;
  private iniciado = false;
  readonly ahora = signal<number | null>(null);

  sincronizar(horaServidor?: string): void {
    const hora = horaServidor ? Date.parse(horaServidor) : NaN;
    if (!Number.isFinite(hora)) return;
    const monotona = performance.now();
    const estimada = this.referenciaServidor == null ? hora
      : this.referenciaServidor + Math.max(0, monotona - this.referenciaMonotona);
    // Una respuesta anterior o la misma respuesta recibida por otro componente
    // no debe hacer retroceder el reloj ni reiniciar la cuenta regresiva.
    this.referenciaServidor = Math.max(hora, estimada);
    this.referenciaMonotona = monotona;
    this.actualizar();
    if (!this.iniciado) {
      this.iniciado = true;
      timer(1000, 1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.actualizar());
    }
  }

  private actualizar(): void {
    if (this.referenciaServidor != null) {
      this.ahora.set(this.referenciaServidor + Math.max(0, performance.now() - this.referenciaMonotona));
    }
  }

  segundosRestantes(emergencia: Emergencia | null | undefined): number | null {
    const ahora = this.ahora();
    const vencimiento = emergencia?.fechaVencimientoConvocatoria
      ? Date.parse(emergencia.fechaVencimientoConvocatoria) : NaN;
    if (ahora == null || !Number.isFinite(vencimiento)) return null;
    return Math.max(0, Math.ceil((vencimiento - ahora) / 1000));
  }
}
