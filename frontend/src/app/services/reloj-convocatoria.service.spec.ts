import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { RelojConvocatoriaService } from './reloj-convocatoria.service';
import { Emergencia } from '../models/emergencia.model';

describe('Reloj de convocatoria', () => {
  const hora = '2026-10-04T12:00:00Z';
  const emergencia: Emergencia = { estado: 'CONVOCATORIA_ABIERTA',
    fechaVencimientoConvocatoria: '2026-10-04T12:01:00Z',
    tipoEmergencia: 'INCENDIO', nivelGravedad: 'ALTO', zonaAfectada: 'Norte', descripcion: 'Prueba', lotes: [] };
  let monotona: number;
  let reloj: RelojConvocatoriaService;

  beforeEach(() => {
    vi.useFakeTimers(); monotona = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => monotona);
    reloj = TestBed.inject(RelojConvocatoriaService);
  });
  afterEach(() => {
    TestBed.resetTestingModule(); vi.restoreAllMocks(); vi.useRealTimers();
  });

  it('usa la hora del servidor aunque el reloj del equipo cambie', () => {
    vi.setSystemTime(new Date('2030-01-01'));
    reloj.sincronizar(hora);
    expect(reloj.segundosRestantes(emergencia)).toBe(60);
    monotona = 10000; vi.advanceTimersByTime(1000);
    vi.setSystemTime(new Date('2000-01-01'));
    expect(reloj.segundosRestantes(emergencia)).toBe(50);
  });

  it('resincronizar con una respuesta vieja no reinicia el plazo', () => {
    reloj.sincronizar(hora);
    monotona = 30000; vi.advanceTimersByTime(1000);
    reloj.sincronizar(hora);
    expect(reloj.segundosRestantes(emergencia)).toBe(30);
    reloj.sincronizar('2026-10-04T11:59:00Z');
    expect(reloj.segundosRestantes(emergencia)).toBe(30);
  });

  it('reconstruye el tiempo restante al volver a cargar la página', () => {
    reloj.sincronizar('2026-10-04T12:00:40Z');
    expect(reloj.segundosRestantes(emergencia)).toBe(20);
  });

  it('recalcula después de un intervalo demorado y nunca muestra valores negativos', () => {
    reloj.sincronizar(hora);
    monotona = 90000; vi.advanceTimersByTime(1000);
    expect(reloj.segundosRestantes(emergencia)).toBe(0);
  });

  it('no inventa un plazo si faltan la hora o el vencimiento', () => {
    expect(reloj.segundosRestantes(emergencia)).toBeNull();
    reloj.sincronizar('fecha inválida');
    expect(reloj.segundosRestantes(emergencia)).toBeNull();
    reloj.sincronizar(hora);
    expect(reloj.segundosRestantes({ ...emergencia, fechaVencimientoConvocatoria: null })).toBeNull();
  });
});
