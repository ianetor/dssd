import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { OfertaLocal } from '../models/oferta.model';

@Injectable({
  providedIn: 'root',
})
export class OfertaService {
  private readonly storageKey = 'rescuesync_ofertas';
  private ofertasSubject = new BehaviorSubject<OfertaLocal[]>(this.cargarIniciales());
  ofertas$: Observable<OfertaLocal[]> = this.ofertasSubject.asObservable();

  private cargarIniciales(): OfertaLocal[] {
    const guardadas = localStorage.getItem(this.storageKey);
    if (guardadas) {
      try {
        return JSON.parse(guardadas);
      } catch (e) {
        // Ignorar error y usar defaults
      }
    }

    const iniciales: OfertaLocal[] = [
      {
        id: 'OF-001',
        loteNombre: 'Lote 1: Personal Sanitario',
        cantidadOfrecida: 2,
        unidad: 'equipos',
        modalidad: 'Individual',
        tiempoLlegada: 'Inmediata (menos de 1 hora)',
        observaciones: 'Dotación completa de paramédicos y ambulancia 4x4.',
        fechaHora: 'Hoy · 09:14 hs',
        estado: 'Registrada',
      },
      {
        id: 'OF-002',
        loteNombre: 'Lote 3: Kits Sanitarios',
        cantidadOfrecida: 120,
        unidad: 'kits',
        modalidad: 'Consorcio',
        ongAsociada: 'Cáritas Regional',
        tiempoLlegada: '2 horas tras adjudicación',
        observaciones: 'Aporte conjunto coordinado para centro de evacuados.',
        fechaHora: 'Hoy · 10:02 hs',
        estado: 'Registrada',
      },
    ];

    this.persistir(iniciales);
    return iniciales;
  }

  private persistir(lista: OfertaLocal[]): void {
    localStorage.setItem(this.storageKey, JSON.stringify(lista));
    this.ofertasSubject.next(lista);
  }

  obtenerTodas(): OfertaLocal[] {
    return this.ofertasSubject.value;
  }

  guardarOferta(oferta: Omit<OfertaLocal, 'id' | 'fechaHora' | 'estado'>): OfertaLocal {
    const lista = this.ofertasSubject.value;
    const now = new Date();
    const timeStr = `Hoy · ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')} hs`;
    const nextId = `OF-00${lista.length + 1}`;

    const nueva: OfertaLocal = {
      ...oferta,
      id: nextId,
      fechaHora: timeStr,
      estado: 'Registrada',
    };

    const actualizada = [nueva, ...lista];
    this.persistir(actualizada);
    return nueva;
  }

  actualizarOferta(id: string, cambios: Partial<OfertaLocal>): OfertaLocal | null {
    const lista = this.ofertasSubject.value;
    const index = lista.findIndex((o) => o.id === id);
    if (index === -1) return null;

    const actual = lista[index];
    const now = new Date();
    const timeStr = `Hoy · ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')} hs`;

    const editada: OfertaLocal = {
      ...actual,
      ...cambios,
      fechaHora: `${timeStr} (Modificado)`,
      estado: 'Rectificada / Versión 2',
    };

    lista[index] = editada;
    this.persistir([...lista]);
    return editada;
  }

  eliminarOferta(id: string): void {
    const filtrada = this.ofertasSubject.value.filter((o) => o.id !== id);
    this.persistir(filtrada);
  }
}
