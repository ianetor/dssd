import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EmergenciaDetailComponent } from './emergencia-detail.component';
import { EmergenciaService } from '../../services/emergencia.service';
import { Emergencia } from '../../models/emergencia.model';

describe('Publicación de convocatoria', () => {
  const emergencia: Emergencia = {
    id: 1, tipoEmergencia: 'INCENDIO', estado: 'REGISTRADA', nivelGravedad: 'ALTO',
    zonaAfectada: 'Norte', descripcion: 'Prueba', lotes: [],
  };
  let servicio: { publicarLotes: ReturnType<typeof vi.fn>; obtenerPorId: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    servicio = { publicarLotes: vi.fn(), obtenerPorId: vi.fn() };
    TestBed.configureTestingModule({
      imports: [EmergenciaDetailComponent],
      providers: [provideRouter([]), { provide: EmergenciaService, useValue: servicio },
        { provide: ActivatedRoute, useValue: { paramMap: EMPTY } }],
    });
  });

  function crear() {
    const fixture = TestBed.createComponent(EmergenciaDetailComponent);
    const c = fixture.componentInstance;
    c.emergencia = { ...emergencia };
    c.lotesDesglosados = [{ nombre: 'Agua', cantidad: 100, unidad: 'litros', prioridad: 'Alta' }];
    return { fixture, c };
  }

  it('convierte horas a minutos y no muestra éxito mientras esté pendiente', () => {
    const { fixture, c } = crear();
    servicio.publicarLotes.mockReturnValue(of({ ...emergencia, estado: 'PUBLICACION_PENDIENTE', duracionConvocatoriaMinutos: 120 }));
    c.duracionCantidad = 2;
    c.publicarConvocatoriaBonita();
    expect(servicio.publicarLotes).toHaveBeenCalledWith(1, [{ tipoRecurso: 'Agua (litros)', cantidadRequerida: 100 }], 120);
    expect(c.publicacionPendiente).toBe(true);
    expect(c.publicadoExitoso).toBe(false);
    expect(c.edicionBloqueada).toBe(true);
    fixture.destroy();
  });

  it('admite un minuto y rechaza valores fraccionarios o fuera de rango', () => {
    const { fixture, c } = crear();
    c.duracionUnidad = 'minutos'; c.duracionCantidad = 1;
    expect(c.duracionValida).toBe(true);
    for (const invalida of [0, -1, 1.5, 2147483648]) {
      c.duracionCantidad = invalida;
      c.publicarConvocatoriaBonita();
      expect(c.duracionValida).toBe(false);
    }
    expect(servicio.publicarLotes).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('conserva el borrador y muestra el error cuando falla la publicación', () => {
    const { fixture, c } = crear();
    servicio.publicarLotes.mockReturnValue(throwError(() => ({ status: 503 })));
    c.publicarConvocatoriaBonita();
    expect(c.publicadoExitoso).toBe(false);
    expect(c.emergencia?.estado).toBe('REGISTRADA');
    expect(c.lotesDesglosados.length).toBe(1);
    expect(c.error).toContain('No se pudo confirmar');
    expect(c.isSubmitting).toBe(false);
    fixture.destroy();
  });

  it('recupera duración y fechas persistidas al cargar una convocatoria publicada', () => {
    const { fixture, c } = crear();
    const fecha = '2026-10-01T18:00:00Z';
    servicio.obtenerPorId.mockReturnValue(of({ ...emergencia, estado: 'CONVOCATORIA_ABIERTA',
      duracionConvocatoriaMinutos: 15, fechaVencimientoConvocatoria: fecha }));
    c.cargar(1);
    expect(c.duracionMinutos).toBe(15);
    expect(c.emergencia?.fechaVencimientoConvocatoria).toBe(fecha);
    expect(c.publicadoExitoso).toBe(true);
    expect(c.edicionBloqueada).toBe(true);
    fixture.destroy();
  });

  it('retoma el seguimiento al recargar una publicación pendiente y lo detiene al confirmarse', () => {
    vi.useFakeTimers();
    const { fixture, c } = crear();
    try {
      servicio.obtenerPorId.mockReturnValueOnce(of({ ...emergencia, estado: 'PUBLICACION_PENDIENTE', duracionConvocatoriaMinutos: 1 }))
        .mockReturnValue(of({ ...emergencia, estado: 'CONVOCATORIA_ABIERTA', duracionConvocatoriaMinutos: 1 }));
      c.cargar(1);
      expect(c.publicacionPendiente).toBe(true);
      vi.advanceTimersByTime(2000);
      expect(c.publicadoExitoso).toBe(true);
      vi.advanceTimersByTime(15000);
      expect(servicio.obtenerPorId).toHaveBeenCalledTimes(2);
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });
});
