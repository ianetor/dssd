import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { OfertaService } from '../../services/oferta.service';
import { OfertaLocal } from '../../models/oferta.model';
import { EmergenciaService } from '../../services/emergencia.service';

interface LoteDisponible {
  id: string;
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
  editandoId: string | null = null;
  feedbackMensaje = '';
  feedbackTitulo = '';

  // Timer regresivo simulación Bonita BPM
  segundosRestantes = 3 * 3600 + 42 * 60 + 19;
  timerString = '03h 42m 19s';
  private timerInterval: any;

  lotesDisponibles: LoteDisponible[] = [
    {
      id: 'lote-1',
      nombre: 'Lote 1: Personal Sanitario',
      descripcion: 'Médicos y enfermeros de triaje para zonas inundadas.',
      demanda: '8 equipos',
      unidad: 'equipos',
      icono: 'medical_services',
    },
    {
      id: 'lote-2',
      nombre: 'Lote 2: Raciones de Alimento',
      descripcion: 'Alimentos no perecederos listos para consumo.',
      demanda: '2.500 raciones',
      unidad: 'raciones',
      icono: 'lunch_dining',
    },
    {
      id: 'lote-3',
      nombre: 'Lote 3: Kits Sanitarios',
      descripcion: 'Agua potable y elementos de higiene primaria.',
      demanda: '300 kits',
      unidad: 'kits',
      icono: 'sanitizer',
    },
  ];

  unidadActual = 'equipos';

  form = this.fb.group({
    loteNombre: ['Lote 1: Personal Sanitario', Validators.required],
    cantidadOfrecida: [2, [Validators.required, Validators.min(1)]],
    modalidad: ['Individual', Validators.required],
    ongAsociada: [''],
    tiempoLlegada: ['2 horas tras adjudicación', Validators.required],
    observaciones: [''],
  });

  ngOnInit(): void {
    this.iniciarTimer();
    this.ofertas = this.ofertaService.obtenerTodas();
    this.ofertaService.ofertas$.subscribe((lista) => {
      this.ofertas = lista;
      this.cd.detectChanges();
    });

    // Actualizar unidad cuando cambia el lote
    this.form.get('loteNombre')?.valueChanges.subscribe((nombre) => {
      const encontrado = this.lotesDisponibles.find((l) => l.nombre === nombre);
      if (encontrado) {
        this.unidadActual = encontrado.unidad;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }

  private iniciarTimer(): void {
    this.timerInterval = setInterval(() => {
      if (this.segundosRestantes > 0) {
        this.segundosRestantes--;
        const h = Math.floor(this.segundosRestantes / 3600);
        const m = Math.floor((this.segundosRestantes % 3600) / 60);
        const s = this.segundosRestantes % 60;
        this.timerString = `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`;
        this.cd.detectChanges();
      }
    }, 1000);
  }

  get esConsorcio(): boolean {
    return this.form.get('modalidad')?.value === 'Consorcio';
  }

  seleccionarLoteCard(lote: LoteDisponible): void {
    this.form.patchValue({ loteNombre: lote.nombre });
    this.unidadActual = lote.unidad;

    // Scroll suave hacia el formulario
    const el = document.getElementById('seccion-formulario-oferta');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.getRawValue();
    const lote = val.loteNombre ?? 'Lote 1: Personal Sanitario';
    const cantidad = Number(val.cantidadOfrecida ?? 1);
    const modalidad = (val.modalidad as 'Individual' | 'Consorcio') ?? 'Individual';
    const ongAsociada = val.ongAsociada ?? '';
    const tiempo = val.tiempoLlegada ?? 'Inmediata';
    const obs = val.observaciones ?? '';

    if (this.editandoId) {
      // Modificar / Gestión de Versiones
      this.ofertaService.actualizarOferta(this.editandoId, {
        loteNombre: lote,
        cantidadOfrecida: cantidad,
        unidad: this.unidadActual,
        modalidad,
        ongAsociada: modalidad === 'Consorcio' ? ongAsociada : undefined,
        tiempoLlegada: tiempo,
        observaciones: obs,
      });

      this.feedbackTitulo = '¡Oferta Rectificada con Éxito (Versión 2)!';
      this.feedbackMensaje = `Se ha actualizado la postulación para el ${lote} con ${cantidad} ${this.unidadActual}. La trazabilidad quedó registrada en la base local.`;
      this.editandoId = null;
    } else {
      // Nueva oferta
      this.ofertaService.guardarOferta({
        loteNombre: lote,
        cantidadOfrecida: cantidad,
        unidad: this.unidadActual,
        modalidad,
        ongAsociada: modalidad === 'Consorcio' ? ongAsociada : undefined,
        tiempoLlegada: tiempo,
        observaciones: obs,
      });

      this.feedbackTitulo = '¡Oferta Registrada Exitosamente!';
      this.feedbackMensaje = `La propuesta de ${cantidad} ${this.unidadActual} para el ${lote} (${modalidad}) fue registrada en la base local para la convocatoria activa.`;
    }

    // Resetear formulario
    this.form.reset({
      loteNombre: lote,
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

    // Scroll suave hacia el formulario
    const el = document.getElementById('seccion-formulario-oferta');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    this.cd.detectChanges();
  }

  cancelarEdicion(): void {
    this.editandoId = null;
    this.form.reset({
      loteNombre: 'Lote 1: Personal Sanitario',
      cantidadOfrecida: 1,
      modalidad: 'Individual',
      ongAsociada: '',
      tiempoLlegada: '2 horas tras adjudicación',
      observaciones: '',
    });
    this.cd.detectChanges();
  }

  eliminarOferta(id: string): void {
    if (confirm(`¿Confirma cancelar y retirar la oferta ${id}?`)) {
      this.ofertaService.eliminarOferta(id);
      if (this.editandoId === id) {
        this.cancelarEdicion();
      }
      this.cd.detectChanges();
    }
  }
}
