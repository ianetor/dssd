import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ConvocatoriaTimerComponent } from './convocatoria-timer.component';

describe('Cuenta regresiva de convocatoria', () => {
  const abierta = { id: 1, estado: 'CONVOCATORIA_ABIERTA', horaServidor: '2026-10-04T12:00:00Z',
    fechaVencimientoConvocatoria: '2026-10-04T12:00:02Z' };
  let monotona: number;
  beforeEach(() => {
    vi.useFakeTimers(); monotona = 0;
    vi.spyOn(performance, 'now').mockImplementation(() => monotona);
    TestBed.configureTestingModule({ imports: [ConvocatoriaTimerComponent] });
  });
  afterEach(() => {
    TestBed.resetTestingModule(); vi.restoreAllMocks(); vi.useRealTimers();
  });

  it('avisa una vez al llegar a cero y espera la confirmación del backend', () => {
    const fixture = TestBed.createComponent(ConvocatoriaTimerComponent);
    const vencido = vi.fn();
    fixture.componentInstance.vencido.subscribe(vencido);
    fixture.componentRef.setInput('emergencia', abierta);
    fixture.detectChanges();
    expect(fixture.componentInstance.tiempo()).toBe('00:00:02');
    monotona = 2000; vi.advanceTimersByTime(2000); fixture.detectChanges();
    expect(vencido).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('Esperando confirmación');
    fixture.componentRef.setInput('emergencia', { ...abierta }); fixture.detectChanges();
    expect(vencido).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('muestra días para plazos de más de 24 horas', () => {
    const fixture = TestBed.createComponent(ConvocatoriaTimerComponent);
    fixture.componentRef.setInput('emergencia', { ...abierta, fechaVencimientoConvocatoria: '2026-10-06T15:04:05Z' });
    fixture.detectChanges();
    expect(fixture.componentInstance.tiempo()).toBe('2 d 03:04:05');
    fixture.destroy();
  });

  it('respeta un cierre confirmado aunque reste tiempo', () => {
    const fixture = TestBed.createComponent(ConvocatoriaTimerComponent);
    const vencido = vi.fn(); fixture.componentInstance.vencido.subscribe(vencido);
    fixture.componentRef.setInput('emergencia', { ...abierta, estado: 'CONVOCATORIA_CERRADA' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Convocatoria cerrada');
    expect(fixture.nativeElement.textContent).not.toContain('00:00:02');
    expect(vencido).not.toHaveBeenCalled(); fixture.destroy();
  });
});
