import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { timeout } from 'rxjs';
import { environment } from '../../enviroments/environment';

/** Tipo de recurso del Sistema Nacional con su disponibilidad global. */
export interface RecursoNacional {
  tipoRecurso: string;
  cantidadTotal: number;
  cantidadBloqueada: number;
  cantidadDisponible: number;
  ongsAportantes: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogoNacionalService {
  private readonly http = inject(HttpClient);

  /**
   * El backend consulta al Sistema Nacional con sus credenciales; el navegador nunca las ve.
   * La espera es larga porque el cloud gratuito tarda cerca de un minuto en despertar.
   */
  recursos() {
    return this.http.get<RecursoNacional[]>(`${environment.apiUrl}/coordinador/catalogo-recursos`,
      { withCredentials: true }).pipe(timeout(80000));
  }
}
