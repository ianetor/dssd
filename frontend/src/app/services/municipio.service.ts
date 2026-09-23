import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../helpers/environment';
import { Municipio } from '../models/municipio.model';

@Injectable({
  providedIn: 'root',
})
export class MunicipioService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/municipios`;

  listar(): Observable<Municipio[]> {
    return this.http.get<Municipio[]>(this.apiUrl);
  }
}
