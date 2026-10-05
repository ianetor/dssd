import { Component, computed, effect, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Emergencia } from '../../models/emergencia.model';
import { RelojConvocatoriaService } from '../../services/reloj-convocatoria.service';

@Component({
  selector: 'app-convocatoria-timer', standalone: true, imports: [CommonModule],
  template: `
    <div class="rounded-lg border border-slate-200 bg-slate-50 p-3 text-slate-700" [class.contador-abierto]="abierta()">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-[11px] font-semibold uppercase">{{ titulo() }}</p>
        <span *ngIf="enVivo()" class="etiqueta-live">
          <span class="indicador-live" aria-hidden="true"></span>
          EN VIVO
        </span>
      </div>
      <p *ngIf="abierta()" class="tiempo text-xl font-mono font-bold tabular-nums">{{ tiempo() }}</p>
      <p *ngIf="abierta() && segundos() === 0" class="text-xs mt-1">
        Plazo terminado. Esperando confirmación del cierre.
      </p>
      <p *ngIf="abierta() && segundos() === null" class="text-xs mt-1">No se pudo determinar el tiempo restante.</p>
      <p *ngIf="emergencia().fechaVencimientoConvocatoria" class="text-xs mt-1">
        Vence: {{ emergencia().fechaVencimientoConvocatoria | date:'dd/MM/yyyy HH:mm:ss' }}
      </p>
    </div>
  `,
  styles: `
    .contador-abierto { border-color: #fecaca; background: #fff1f2; }
    .contador-abierto .tiempo { color: #dc2626; }
    .etiqueta-live {
      display: inline-flex; align-items: center; gap: 7px;
      color: #dc2626; font-size: 10px; font-weight: 800; letter-spacing: .08em;
    }
    .indicador-live {
      width: 8px; height: 8px; flex-shrink: 0; border-radius: 50%;
      background: #ef4444; animation: pulso-live 1.4s ease-in-out infinite;
    }
    @keyframes pulso-live {
      0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgb(239 68 68 / 35%); }
      50% { opacity: .35; box-shadow: 0 0 0 4px rgb(239 68 68 / 0%); }
    }
    @media (prefers-reduced-motion: reduce) { .indicador-live { animation: none; } }
  `,
})
export class ConvocatoriaTimerComponent {
  readonly emergencia = input.required<Emergencia>();
  readonly vencido = output<void>();
  private readonly reloj = inject(RelojConvocatoriaService);
  private notificado: string | null = null;
  readonly abierta = computed(() => this.emergencia().estado === 'CONVOCATORIA_ABIERTA');
  readonly segundos = computed(() => this.reloj.segundosRestantes(this.emergencia()));
  readonly enVivo = computed(() => this.abierta() && (this.segundos() ?? 0) > 0);
  readonly titulo = computed(() => {
    switch (this.emergencia().estado) {
      case 'CONVOCATORIA_ABIERTA': return 'Tiempo restante';
      case 'CONVOCATORIA_CERRADA': return 'Convocatoria cerrada';
      case 'PUBLICACION_PENDIENTE': return 'Publicación pendiente';
      default: return 'Convocatoria aún no publicada';
    }
  });
  readonly tiempo = computed(() => {
    const segundos = this.segundos();
    if (segundos == null) return '—';
    const dias = Math.floor(segundos / 86400);
    const horas = Math.floor(segundos / 3600) % 24;
    const minutos = Math.floor(segundos / 60) % 60;
    const hhmmss = [horas, minutos, segundos % 60].map(n => String(n).padStart(2, '0')).join(':');
    return dias > 0 ? `${dias} d ${hhmmss}` : hhmmss;
  });

  constructor() {
    effect(() => {
      const e = this.emergencia();
      this.reloj.sincronizar(e.horaServidor);
    });
    effect(() => {
      const e = this.emergencia();
      const clave = `${e.id}:${e.fechaVencimientoConvocatoria}`;
      if (this.abierta() && this.segundos() === 0 && this.notificado !== clave) {
        this.notificado = clave;
        this.vencido.emit();
      }
    });
  }
}
