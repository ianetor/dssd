import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { retry, timeout } from 'rxjs/operators';
import { environment } from '../../enviroments/environment';
import { Emergencia, EmergenciaPayload, LotePayload } from '../models/emergencia.model';

@Injectable({
  providedIn: 'root',
})
export class EmergenciaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/emergencias`;

  listar(estado?: string): Observable<Emergencia[]> {
    const url = estado ? `${this.apiUrl}?estado=${encodeURIComponent(estado)}` : this.apiUrl;
    return this.http.get<Emergencia[]>(url).pipe(
      timeout({ first: 5000 }),
      retry({ count: 2, delay: 1000 })
    );
  }

  obtenerPorId(id: number): Observable<Emergencia> {
    return this.http.get<Emergencia>(`${this.apiUrl}/${id}`);
  }

  crear(payload: EmergenciaPayload): Observable<Emergencia> {
    return this.http.post<Emergencia>(this.apiUrl, payload);
  }

  publicarLotes(id: number, lotes: LotePayload[]): Observable<Emergencia> {
    return this.http.post<Emergencia>(`${this.apiUrl}/${id}/lotes`, lotes);
  }
}
