import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { vi } from 'vitest';
import { PortalOngComponent } from './portal-ong.component';
import { EmergenciaService } from '../../services/emergencia.service';
import { OfertaService } from '../../services/oferta.service';

describe('Cierre de convocatoria en portal ONG', () => {
  const abierta = { id: 1, estado: 'CONVOCATORIA_ABIERTA', tipoEmergencia: 'INCENDIO',
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
});
