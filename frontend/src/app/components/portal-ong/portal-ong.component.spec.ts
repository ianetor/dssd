import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { PortalOngComponent } from './portal-ong.component';
import { EmergenciaService } from '../../services/emergencia.service';
import { OfertaService } from '../../services/oferta.service';

describe('Cierre de convocatoria en portal ONG', () => {
  const abierta = { id: 1, estado: 'CONVOCATORIA_ABIERTA', tipoEmergencia: 'INCENDIO',
    horaServidor: '2026-10-04T12:00:00Z', fechaVencimientoConvocatoria: '2026-10-04T13:00:00Z',
    lotes: [{ id: 2, tipoRecurso: 'Agua (litros)', cantidadRequerida: 100, cantidadCubierta: 20 }] };
  const oferta = { id: '3', loteId: 2, emergenciaId: 1, cantidadOfrecida: 20,
    estado: 'Registrada', unidad: 'litros', tiempoLlegada: '2 horas' };
  let emergencias: { obtenerPorId: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    emergencias = { obtenerPorId: vi.fn() };
    TestBed.configureTestingModule({
      imports: [PortalOngComponent],
      providers: [provideRouter([]), { provide: EmergenciaService, useValue: emergencias },
        { provide: OfertaService, useValue: { listar: vi.fn(() => of([oferta])) } },
        { provide: ActivatedRoute, useValue: { paramMap: EMPTY } }],
    });
  });

  it('cancela la edición al cerrarse y conserva las ofertas para consulta', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PortalOngComponent);
    const c = fixture.componentInstance;
    try {
      emergencias.obtenerPorId.mockReturnValueOnce(of(abierta))
        .mockReturnValue(of({ ...abierta, estado: 'CONVOCATORIA_CERRADA' }));
      c.cargarEmergencia(1);
      c.iniciarEdicion(c.ofertas[0]);
      expect(c.editandoId).toBe('3');
      vi.advanceTimersByTime(5000);
      expect(c.convocatoriaCerrada).toBe(true);
      expect(c.editandoId).toBeNull();
      expect(c.puedeOfertar).toBe(false);
      expect(c.ofertas).toHaveLength(1);
      c.iniciarEdicion(c.ofertas[0]);
      c.eliminarOferta('3');
      expect(c.editandoId).toBeNull();
      expect(c.mostrarConfirmacion).toBe(false);
      vi.advanceTimersByTime(15000);
      expect(emergencias.obtenerPorId).toHaveBeenCalledTimes(2);
    } finally { fixture.destroy(); vi.useRealTimers(); }
  });

  it('al abrir una convocatoria cerrada muestra sus ofertas sin iniciar seguimiento', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PortalOngComponent);
    const c = fixture.componentInstance;
    try {
      emergencias.obtenerPorId.mockReturnValue(of({ ...abierta, estado: 'CONVOCATORIA_CERRADA' }));
      c.cargarEmergencia(1);
      expect(c.convocatoriaCerrada).toBe(true);
      expect(c.ofertas).toHaveLength(1);
      expect(c.puedeOfertar).toBe(false);
      vi.advanceTimersByTime(15000);
      expect(emergencias.obtenerPorId).toHaveBeenCalledTimes(1);
    } finally { fixture.destroy(); vi.useRealTimers(); }
  });

  it('cierra antes del vencimiento y sigue el avance pendiente hasta confirmar adjudicación', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PortalOngComponent);
    const c = fixture.componentInstance;
    try {
      const cerrada = { ...abierta, estado: 'CONVOCATORIA_CERRADA',
        motivoCierre: 'COBERTURA_COMPLETA', avanceCoberturaEstado: 'PENDIENTE' };
      emergencias.obtenerPorId.mockReturnValueOnce(of(cerrada))
        .mockReturnValue(of({ ...cerrada, avanceCoberturaEstado: 'CONFIRMADO' }));
      c.cargarEmergencia(1);
      fixture.detectChanges();
      expect(c.convocatoriaCerrada).toBe(true);
      expect(c.plazoAgotado).toBe(false);
      expect(c.puedeOfertar).toBe(false);
      expect(c.ofertas).toHaveLength(1);
      expect(fixture.nativeElement.textContent).toContain('todos los lotes están cubiertos');
      vi.advanceTimersByTime(5000);
      expect(c.emergenciaSeleccionada?.avanceCoberturaEstado).toBe('CONFIRMADO');
      vi.advanceTimersByTime(15000);
      expect(emergencias.obtenerPorId).toHaveBeenCalledTimes(2);
    } finally { fixture.destroy(); vi.useRealTimers(); }
  });

  it('bloquea acciones al vencer aunque el backend todavía informe abierta', () => {
    vi.useFakeTimers();
    let monotona = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => monotona);
    const fixture = TestBed.createComponent(PortalOngComponent);
    const c = fixture.componentInstance;
    try {
      emergencias.obtenerPorId.mockReturnValue(of({ ...abierta,
        fechaVencimientoConvocatoria: '2026-10-04T12:00:02Z' }));
      c.cargarEmergencia(1);
      c.iniciarEdicion(c.ofertas[0]);
      expect(c.editandoId).toBe('3');
      monotona = 2000; vi.advanceTimersByTime(2000); fixture.detectChanges();
      expect(c.emergenciaSeleccionada?.estado).toBe('CONVOCATORIA_ABIERTA');
      expect(c.plazoAgotado).toBe(true);
      expect(c.recepcionHabilitada).toBe(false);
      expect(c.editandoId).toBeNull();
      c.onSubmit(); c.eliminarOferta('3');
      expect(c.mostrarConfirmacion).toBe(false);
      expect(c.ofertas).toHaveLength(1);
    } finally { fixture.destroy(); vi.restoreAllMocks(); vi.useRealTimers(); }
  });
});
