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
  duracionConvocatoriaMinutos?: number | null;
  fechaAperturaConvocatoria?: string | null;
  fechaVencimientoConvocatoria?: string | null;
  fechaCierreConvocatoria?: string | null;
  motivoCierre?: 'TIEMPO_AGOTADO' | 'COBERTURA_COMPLETA' | null;
  horaServidor?: string;
  publicacionIntentos?: number | null;
  publicacionError?: string | null;
  avanceCoberturaEstado?: 'PENDIENTE' | 'CONFIRMADO' | null;
  avanceCoberturaError?: string | null;
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
  municipioNombre?: string;
  hectareasAfectadas?: number | null;
  milimetrosAgua?: number | null;
  magnitudRichter?: number | null;
}

export interface LotePayload {
  tipoRecurso: string;
  cantidadRequerida: number;
}
