import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { timeout } from 'rxjs';
import { environment } from '../../enviroments/environment';
import { ResumenIndicadores, TiempoEtapa } from '../models/indicadores.model';

@Injectable({ providedIn: 'root' })
export class IndicadoresService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/directivo/indicadores`;

  /** El backend consulta el historial de cada caso en Bonita: puede tardar con muchos casos. */
  tiempoRevision() {
    return this.http.get<TiempoEtapa>(`${this.url}/tiempo-revision`, { withCredentials: true }).pipe(timeout(60000));
  }

  /** Estados y suministros: solo base local, responde rápido. */
  resumen() {
    return this.http.get<ResumenIndicadores>(`${this.url}/resumen`, { withCredentials: true }).pipe(timeout(15000));
  }
}
