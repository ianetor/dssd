import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, Subject, catchError, exhaustMap, map, merge, switchMap, tap, timer } from 'rxjs';
import { RevisionCoberturaService } from '../../services/revision-cobertura.service';
import { AuthService } from '../../services/auth.service';
import { BandejaCobertura, RevisionCobertura, SnapshotCobertura } from '../../models/revision-cobertura.model';
import { TextoCodigoPipe } from '../../pipes/texto-codigo.pipe';

@Component({
  selector: 'app-cobertura-insuficiente', standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, TextoCodigoPipe],
  templateUrl: './cobertura-insuficiente.component.html', styleUrl: './cobertura-insuficiente.component.scss',
})
export class CoberturaInsuficienteComponent {
  private readonly api = inject(RevisionCoberturaService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly bandeja = signal<BandejaCobertura | null>(null);
  readonly revision = signal<RevisionCobertura | null>(null);
  readonly error = signal(''); readonly aviso = signal(''); readonly enviando = signal(false);
  readonly errorDecision = signal('');
  readonly confirmacion = signal<'REABRIR' | 'CANCELAR' | null>(null);
  readonly cargando = signal(true);
  private readonly refrescar = new Subject<void>();
  private id: number | null = null;
  private rondaRegistrada: number | null = null;
  pagina = 0;
  readonly form = this.fb.nonNullable.group({
    motivo: ['', [Validators.required, Validators.maxLength(1000), Validators.pattern(/\S/)]],
    duracionMinutos: [1, [Validators.required, Validators.min(1), Validators.max(2147483647), Validators.pattern(/^\d+$/)]],
  });

  constructor() {
    this.route.paramMap.pipe(switchMap(params => {
      this.id = params.has('id') ? Number(params.get('id')) : null;
      this.rondaRegistrada = null; this.errorDecision.set('');
      this.revision.set(null); this.bandeja.set(null); this.error.set(''); this.aviso.set('');
      this.cargando.set(true); this.pagina = 0; this.form.reset({ motivo: '', duracionMinutos: 1 });
      return merge(timer(0, 5000), this.refrescar).pipe(exhaustMap(() => this.cargar()));
    }), takeUntilDestroyed(this.destroy)).subscribe();
  }
  private cargar() {
    const solicitud: Observable<unknown> = this.id === null
      ? this.api.listar(this.pagina).pipe(tap(datos => this.bandeja.set(datos)))
      : this.api.detalle(this.id).pipe(tap(datos => {
          const inicial = this.revision() === null || this.revision()?.ronda !== datos.ronda
            || this.revision()?.evaluacion.cobertura.fechaValidacion !== datos.evaluacion.cobertura.fechaValidacion;
          this.revision.set(this.rondaRegistrada === datos.ronda ? { ...datos, puedeDecidir: false } : datos);
          if (inicial) this.form.reset({ motivo: '', duracionMinutos: datos.evaluacion.emergencia.duracionConvocatoriaMinutos ?? 1 });
        }));
    return solicitud.pipe(map(() => { this.cargando.set(false); this.error.set(''); }), catchError((e: HttpErrorResponse) => {
      this.cargando.set(false); this.mostrarError(e); return EMPTY;
    }));
  }
  cambiarPagina(delta: number) { this.pagina += delta; this.refrescar.next(); }
  ofertasDelLote(snapshot: SnapshotCobertura, loteId: number) { return snapshot.ofertas.filter(item => Number(item.oferta.loteId) === loteId); }
  minimo(cantidad: number) { return Math.ceil(cantidad / 2); }
  porcentajeVisual(porcentaje: number) { return Math.min(100, Math.max(0, porcentaje)); }
  faltante(minimo: number, cubierta: number) { return Math.max(0, minimo - cubierta); }
  preparar(accion: 'REABRIR' | 'CANCELAR') {
    this.form.markAllAsTouched();
    if (!this.revision()?.puedeDecidir || this.enviando() || this.form.controls.motivo.invalid
        || accion === 'REABRIR' && this.form.controls.duracionMinutos.invalid) return;
    this.confirmacion.set(accion);
    this.errorDecision.set('');
  }
  confirmar() {
    const actual = this.revision(); const accion = this.confirmacion();
    if (!actual || !accion || this.enviando()) return;
    this.enviando.set(true); this.error.set('');
    this.api.decidir(actual.emergenciaId, { ronda: actual.ronda, decision: accion,
      motivo: this.form.controls.motivo.value.trim(),
      ...(accion === 'REABRIR' ? { duracionMinutos: Number(this.form.controls.duracionMinutos.value) } : {}),
    }).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.enviando.set(false); this.confirmacion.set(null);
        this.rondaRegistrada = actual.ronda;
        this.revision.set({ ...actual, puedeDecidir: false });
        this.aviso.set('Decisión registrada. Esperando confirmación de Bonita.'); this.refrescar.next();
      }, error: (e: HttpErrorResponse) => {
        this.enviando.set(false); this.confirmacion.set(null); this.mostrarError(e, true); this.refrescar.next();
      },
    });
  }
  private mostrarError(e: HttpErrorResponse, decision = false) {
    if (e.status === 401) { this.auth.limpiarSesion(); return; }
    (decision ? this.errorDecision : this.error).set(e.status === 403 ? 'Solo el Centro Coordinador puede acceder a esta sección.'
      : e.error?.message || (e.status === 409 ? 'La ronda cambió o ya tiene una decisión. Se actualizarán los datos.'
      : 'No se pudo completar la consulta. Se volverá a consultar automáticamente.'));
  }
}
