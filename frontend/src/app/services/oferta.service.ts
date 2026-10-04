import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../enviroments/environment';
import { OfertaLocal } from '../models/oferta.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class OfertaService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly url = `${environment.apiUrl}/ofertas`;

  /** Construye los headers con el username del usuario autenticado para identificar la ONG. */
  private headers(): HttpHeaders {
    const username = this.authService.currentUser()?.username ?? '';
    return new HttpHeaders({ 'X-ONG-Username': username });
  }

  private options() {
    return { headers: this.headers(), withCredentials: true };
  }

  /** Lista las ofertas propias, opcionalmente filtradas por emergencia. */
  listar(emergenciaId?: number): Observable<OfertaLocal[]> {
    return this.http.get<OfertaLocal[]>(this.url, {
      ...this.options(),
      params: emergenciaId == null ? {} : { emergenciaId },
    });
  }

  /** Registra una nueva oferta en la base de datos. */
  guardarOferta(oferta: Partial<OfertaLocal>): Observable<OfertaLocal> {
    return this.http.post<OfertaLocal>(this.url, oferta, this.options());
  }

  /** Rectifica una oferta existente. */
  actualizarOferta(id: string, oferta: Partial<OfertaLocal>): Observable<OfertaLocal> {
    return this.http.put<OfertaLocal>(`${this.url}/${id}`, oferta, this.options());
  }

  /** Retira (elimina lógicamente) una oferta propia. */
  eliminarOferta(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`, this.options());
  }
}
