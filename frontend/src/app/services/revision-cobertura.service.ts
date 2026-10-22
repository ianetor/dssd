import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { environment } from '../../enviroments/environment';
import { BandejaCobertura, RevisionCobertura, SolicitudDecision, DecisionCobertura } from '../models/revision-cobertura.model';

@Injectable({ providedIn: 'root' })
export class RevisionCoberturaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/coordinador`;
  listar(pagina = 0) {
    return this.http.get<BandejaCobertura>(`${this.url}/cobertura-insuficiente`, { params: { pagina, cantidad: 20 }, withCredentials: true }).pipe(timeout(15000));
  }
  detalle(id: number) {
    return this.http.get<RevisionCobertura>(`${this.url}/emergencias/${id}/cobertura-insuficiente`, { withCredentials: true }).pipe(timeout(15000));
  }
  decidir(id: number, body: SolicitudDecision) {
    return this.http.post<DecisionCobertura>(`${this.url}/emergencias/${id}/decisiones-cobertura`, body, { withCredentials: true }).pipe(timeout(30000));
  }
}
