import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../enviroments/environment';

@Injectable({ providedIn: 'root' })
export class BonitaService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  health(): Observable<any> {
    return this.http.get(`${this.apiUrl}/bonita/health`);
  }

  procesos(): Observable<string> {
    return this.http.get(`${this.apiUrl}/bonita/procesos`, { responseType: 'text' });
  }

  tareas(): Observable<string> {
    return this.http.get(`${this.apiUrl}/bonita/tareas`, { responseType: 'text' });
  }

  casos(): Observable<string> {
    return this.http.get(`${this.apiUrl}/bonita/casos`, { responseType: 'text' });
  }

  usuarios(): Observable<string> {
    return this.http.get(`${this.apiUrl}/bonita/usuarios`, { responseType: 'text' });
  }
}