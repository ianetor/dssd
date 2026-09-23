export interface LoteResponse {
  id?: number;
  tipoRecurso: string;
  cantidadRequerida: number;
  cantidadCubierta: number;
}

export interface Emergencia {
  id?: number;
  tipoEmergencia: string;
  nivelGravedad: string;
  zonaAfectada: string;
  descripcion: string;
  estado: string;
  municipioId?: number | null;
  municipioNombre?: string;
  hectareasAfectadas?: number | null;
  milimetrosAgua?: number | null;
  magnitudRichter?: number | null;
  lotes: LoteResponse[];
}

export interface EmergenciaPayload {
  tipoEmergencia: string;
  nivelGravedad: string;
  zonaAfectada: string;
  descripcion: string;
  municipioId?: number | null;
  hectareasAfectadas?: number | null;
  milimetrosAgua?: number | null;
  magnitudRichter?: number | null;
}

export interface LotePayload {
  tipoRecurso: string;
  cantidadRequerida: number;
}
