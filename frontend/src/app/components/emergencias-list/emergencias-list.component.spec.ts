import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EmergenciasListComponent } from './emergencias-list.component';
import { EmergenciaService } from '../../services/emergencia.service';
import { OfertaService } from '../../services/oferta.service';
import { AuthService } from '../../services/auth.service';
import { RolUsuario } from '../../models/auth.model';

describe('Filtros de emergencias por rol', () => {
  const data = ['CONVOCATORIA_ABIERTA', 'CONVOCATORIA_CERRADA', 'CONVOCATORIA_CERRADA',
    'REGISTRADA', 'PUBLICACION_PENDIENTE'].map((estado, i) => ({
    id: i + 1, estado, tipoEmergencia: 'INCENDIO', nivelGravedad: 'ALTO',
    zonaAfectada: 'Norte', descripcion: 'Prueba', lotes: [],
  }));
  let rol: RolUsuario;
  let ofertas: { listar: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    rol = 'REPRESENTANTE_ONG';
    ofertas = { listar: vi.fn(() => of([
      { emergenciaId: 1, estado: 'Registrada' },
      { emergenciaId: 2, estado: 'Retirada' },
      { emergenciaId: 4, estado: 'Registrada' },
    ])) };
    TestBed.configureTestingModule({
      imports: [EmergenciasListComponent],
      providers: [provideRouter([]),
        { provide: AuthService, useValue: { userRole: () => rol } },
        { provide: EmergenciaService, useValue: { listar: vi.fn(() => of(data)) } },
        { provide: OfertaService, useValue: ofertas }],
    });
  });

  it('la ONG solo dispone de abiertas, cerradas y participación', () => {
    const fixture = TestBed.createComponent(EmergenciasListComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(c.emergencias.map(e => e.id)).toEqual([1, 2, 3]);
    expect(c.emergenciasFiltradas.map(e => e.id)).toEqual([1]);
    const filtros = Array.from(fixture.nativeElement.querySelectorAll('[aria-pressed]')) as HTMLElement[];
    expect(filtros.map(f => f.textContent?.trim())).toEqual([
      'Convocatoria abierta', 'Convocatoria cerrada', 'Convocatorias con mi participación',
    ]);
    filtros[1].click();
    expect(c.emergenciasFiltradas.map(e => e.id)).toEqual([2, 3]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('CONVOCATORIA CERRADA');
    expect(fixture.nativeElement.textContent).not.toContain('CONVOCATORIA_CERRADA');
    c.cambiarFiltroTab('REGISTRADA');
    c.cambiarFiltroTab('TODAS');
    expect(c.filtroEstadoSeleccionado).toBe('CONVOCATORIA_CERRADA');
    fixture.destroy();
  });

  it('participación incluye abiertas y cerradas con ofertas propias, incluso retiradas', () => {
    const fixture = TestBed.createComponent(EmergenciasListComponent);
    const c = fixture.componentInstance;
    c.soloConvocatoriaAbierta = true;
    fixture.detectChanges();
    c.cambiarFiltroTab('PARTICIPACION');
    expect(c.emergenciasFiltradas.map(e => e.id)).toEqual([1, 2]);
    expect(ofertas.listar).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('el coordinador conserva los filtros generales sin consultar ofertas de ONG', () => {
    rol = 'COORDINADOR_REGIONAL';
    const fixture = TestBed.createComponent(EmergenciasListComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    expect(c.emergencias).toHaveLength(5);
    c.cambiarFiltroTab('REGISTRADA');
    expect(c.emergenciasFiltradas.map(e => e.id)).toEqual([4]);
    expect(ofertas.listar).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('si falla la consulta de ofertas informa el error en lugar de inventar participación', () => {
    ofertas.listar.mockReturnValue(throwError(() => ({ status: 500 })));
    const fixture = TestBed.createComponent(EmergenciasListComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.error).toContain('No se pudieron sincronizar');
    expect(fixture.componentInstance.loading).toBe(false);
    fixture.destroy();
  });
});
