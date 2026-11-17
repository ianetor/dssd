import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { timeout } from 'rxjs';
import { environment } from '../../enviroments/environment';
import { AdjudicacionOng, EstadoEjecucion, ParticipacionEjecucion } from '../models/ejecucion.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class EjecucionService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly api = environment.apiUrl;

  /** Las operaciones de ONG se identifican igual que las ofertas: con el username autenticado. */
  private opcionesOng() {
    const username = this.auth.currentUser()?.username ?? '';
    return { headers: new HttpHeaders({ 'X-ONG-Username': username }), withCredentials: true };
  }

  // --- Centro Coordinador / Auditor ---
  monitorear(emergenciaId: number) {
    return this.http.get<EstadoEjecucion>(`${this.api}/coordinador/emergencias/${emergenciaId}/ejecucion`,
      { withCredentials: true }).pipe(timeout(15000));
  }

  cerrar(emergenciaId: number, forzar: boolean, motivo: string) {
    return this.http.post<EstadoEjecucion>(`${this.api}/coordinador/emergencias/${emergenciaId}/ejecucion/cierre`,
      { forzar, motivo }, { withCredentials: true }).pipe(timeout(30000));
  }

  // --- ONG ---
  adjudicaciones() {
    return this.http.get<AdjudicacionOng[]>(`${this.api}/ejecucion/adjudicaciones`, this.opcionesOng())
      .pipe(timeout(15000));
  }

  finalizar(emergenciaId: number, observaciones: string) {
    return this.http.post<ParticipacionEjecucion>(`${this.api}/ejecucion/emergencias/${emergenciaId}/finalizacion`,
      { observaciones }, this.opcionesOng()).pipe(timeout(15000));
  }
}
