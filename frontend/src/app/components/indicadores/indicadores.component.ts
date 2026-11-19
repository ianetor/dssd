import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { ResumenIndicadores, TiempoEtapa } from '../../models/indicadores.model';
import { TextoCodigoPipe } from '../../pipes/texto-codigo.pipe';
import { AuthService } from '../../services/auth.service';
import { IndicadoresService } from '../../services/indicadores.service';

/** Colores fijos por estado: el mismo estado tiene siempre el mismo color. */
const COLOR_ESTADO: Record<string, string> = {
  REGISTRADA: '#94a3b8',
  PUBLICACION_PENDIENTE: '#cbd5e1',
  CONVOCATORIA_ABIERTA: '#3b82f6',
  CONVOCATORIA_CERRADA: '#1e3e62',
  EVALUACION_INSUFICIENTE: '#f59e0b',
  REAPERTURA_PENDIENTE: '#fbbf24',
  CANCELACION_PENDIENTE: '#fca5a5',
  CANCELADA: '#dc2626',
  EN_EJECUCION: '#ea580c',
  CIERRE_PENDIENTE: '#fdba74',
  FINALIZADA: '#10b981',
};
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** Dashboard del Auditor / Directivo: estados, suministros (base local) y tiempo de revisión (Bonita). */
@Component({
  selector: 'app-indicadores', standalone: true,
  imports: [CommonModule, RouterLink, BaseChartDirective, TextoCodigoPipe],
  templateUrl: './indicadores.component.html', styleUrl: './indicadores.component.scss',
})
export class IndicadoresComponent {
  private readonly api = inject(IndicadoresService);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);

  // --- Base local: estados y suministros ---
  readonly resumen = signal<ResumenIndicadores | null>(null);
  readonly cargandoResumen = signal(true);
  readonly errorResumen = signal('');
  readonly recursoElegido = signal('');

  /** Cada recurso con su unidad: no se suman litros con personas. */
  readonly recursos = computed(() => {
    const vistos = new Map<string, { clave: string; nombre: string; unidad: string }>();
    for (const s of this.resumen()?.suministros ?? []) {
      const clave = `${s.tipoRecurso}|${s.unidad}`;
      if (!vistos.has(clave)) vistos.set(clave, { clave, nombre: s.tipoRecurso, unidad: s.unidad });
    }
    return [...vistos.values()].sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  readonly totalEmergencias = computed(() => (this.resumen()?.estados ?? []).reduce((t, e) => t + e.cantidad, 0));

  readonly graficoEstados = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const estados = this.resumen()?.estados ?? [];
    return {
      labels: estados.map((e) => this.nombreEstado(e.estado)),
      datasets: [{
        data: estados.map((e) => e.cantidad),
        backgroundColor: estados.map((e) => COLOR_ESTADO[e.estado] ?? '#64748b'),
        borderWidth: 2, borderColor: '#ffffff',
      }],
    };
  });

  readonly opcionesEstados: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '60%',
    plugins: {
      legend: { position: 'right', labels: { boxWidth: 12, padding: 12 } },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const total = this.totalEmergencias();
            const pct = total ? Math.round((100 * ctx.parsed) / total) : 0;
            return ` ${ctx.parsed} ${ctx.parsed === 1 ? 'emergencia' : 'emergencias'} (${pct} %)`;
          },
        },
      },
    },
  };

  readonly graficoSuministros = computed<ChartConfiguration<'bar'>['data']>(() => {
    const filas = this.resumen()?.suministros ?? [];
    const meses = [...new Set(filas.map((s) => s.mes))].sort();
    const elegido = this.recursoElegido();
    return {
      labels: meses.map((m) => this.nombreMes(m)),
      datasets: [{
        data: meses.map((m) => filas
          .filter((s) => s.mes === m && `${s.tipoRecurso}|${s.unidad}` === elegido)
          .reduce((t, s) => t + s.cantidad, 0)),
        backgroundColor: '#1e3e62', hoverBackgroundColor: '#ea580c', borderRadius: 6, maxBarThickness: 72,
      }],
    };
  });

  readonly opcionesSuministros: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { label: (ctx) => ` ${ctx.parsed.y} ${this.unidadElegida()}` } },
    },
    scales: {
      y: { beginAtZero: true, ticks: { precision: 0 }, title: { display: true, text: 'Unidades comprometidas' } },
      x: { grid: { display: false } },
    },
  };

  // --- Bonita: tiempo de "Revisar Emergencia y Generar Lotes" ---
  readonly datos = signal<TiempoEtapa | null>(null);
  readonly cargando = signal(true);
  readonly error = signal('');
  grafico: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };

  readonly opciones: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const tipo = this.datos()?.porTipo.at(ctx.dataIndex);
            const casos = tipo ? ` · ${tipo.cantidad} ${tipo.cantidad === 1 ? 'caso' : 'casos'}` : '';
            return `${this.duracion((ctx.parsed.y ?? 0) * 60)}${casos}`;
          },
        },
      },
    },
    scales: {
      y: { beginAtZero: true, title: { display: true, text: 'Minutos promedio' } },
      x: { grid: { display: false } },
    },
  };

  constructor() {
    this.cargar();
  }

  /** Las dos fuentes se cargan por separado: si Bonita no responde, los datos locales se ven igual. */
  cargar() {
    this.cargarResumen();
    this.cargarTiempos();
  }

  private cargarResumen() {
    this.cargandoResumen.set(true);
    this.errorResumen.set('');
    this.api.resumen().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (r) => {
        this.resumen.set(r);
        // Conserva el recurso elegido si sigue existiendo; si no, el primero.
        const claves = this.recursos().map((x) => x.clave);
        if (!claves.includes(this.recursoElegido())) this.recursoElegido.set(claves.at(0) ?? '');
        this.cargandoResumen.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargandoResumen.set(false);
        this.errorResumen.set(this.mensaje(e, 'No se pudieron calcular los indicadores de la base local.'));
      },
    });
  }

  private cargarTiempos() {
    this.cargando.set(true);
    this.error.set('');
    this.api.tiempoRevision().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.grafico = {
          labels: d.porTipo.map((t) => t.tipoEmergencia.charAt(0) + t.tipoEmergencia.slice(1).toLowerCase()),
          datasets: [{
            data: d.porTipo.map((t) => Math.round((t.promedioSegundos / 60) * 100) / 100),
            backgroundColor: '#1e3e62', hoverBackgroundColor: '#ea580c', borderRadius: 6, maxBarThickness: 72,
          }],
        };
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.mensaje(e, 'No se pudo consultar el historial de Bonita.'));
      },
    });
  }

  private mensaje(e: HttpErrorResponse, generico: string): string {
    if (e.status === 401) { this.auth.limpiarSesion(); return ''; }
    if (e.status === 403) return 'Solo el personal directivo puede ver los indicadores.';
    if (e.status === 404) return 'El servidor no tiene este indicador. Reiniciá el backend para tomar los cambios.';
    return generico;
  }

  elegirRecurso(clave: string) {
    this.recursoElegido.set(clave);
  }

  unidadElegida(): string {
    return this.recursos().find((r) => r.clave === this.recursoElegido())?.unidad ?? '';
  }

  /** "2026-11" → "nov 2026". */
  nombreMes(mes: string): string {
    const [anio, m] = mes.split('-');
    return `${MESES.at(Number(m) - 1) ?? m} ${anio}`;
  }

  nombreEstado(estado: string): string {
    const texto = estado.replace(/_/g, ' ').toLowerCase();
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  /** 95 → "1 min 35 s"; 7260 → "2 h 1 min". */
  duracion(segundos: number): string {
    const s = Math.round(segundos);
    if (s < 60) return `${s} s`;
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
    if (h > 0) return `${h} h ${m} min`;
    return r > 0 ? `${m} min ${r} s` : `${m} min`;
  }

  // --- Exportación ---
  exportarEstadosCsv() {
    const r = this.resumen();
    if (r) this.csv([['estado', 'cantidad'], ...r.estados.map((e) => [e.estado, e.cantidad])], 'estados-emergencias.csv');
  }

  exportarSuministrosCsv() {
    const r = this.resumen();
    if (r) this.csv([['mes', 'recurso', 'unidad', 'cantidad'],
      ...r.suministros.map((s) => [s.mes, s.tipoRecurso, s.unidad, s.cantidad])], 'suministros-por-mes.csv');
  }

  exportarCsv() {
    const d = this.datos();
    if (!d) return;
    this.csv([
      ['emergencia', 'caso_bonita', 'tipo', 'municipio', 'inicio', 'fin', 'segundos'],
      ...d.mediciones.map((m) => [m.emergenciaId, m.caseId, m.tipoEmergencia, m.municipio, m.inicio, m.fin, m.segundos]),
    ], 'tiempo-revision.csv');
  }

  /** Todos los indicadores en un solo archivo estructurado. */
  exportarJson() {
    const todo = { generado: new Date().toISOString(), ...this.resumen(), tiempoRevision: this.datos() };
    this.descargar(new Blob([JSON.stringify(todo, null, 2)], { type: 'application/json' }), 'indicadores.json');
  }

  private csv(filas: (string | number)[][], nombre: string) {
    const texto = filas.map((f) => f.map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n');
    // BOM para que Excel abra bien las tildes.
    this.descargar(new Blob(['﻿' + texto], { type: 'text/csv;charset=utf-8' }), nombre);
  }

  private descargar(blob: Blob, nombre: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    URL.revokeObjectURL(url);
  }
}
