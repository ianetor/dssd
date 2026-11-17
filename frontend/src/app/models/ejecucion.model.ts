export type EstadoParticipacion = 'EN_CURSO' | 'FINALIZADA';

export interface RecursoComprometido {
  tipoRecurso: string;
  cantidad: number;
  unidad?: string | null;
}

export interface ParticipacionEjecucion {
  ong: string;
  estado: EstadoParticipacion;
  fechaInicio: string;
  fechaFinalizacion?: string | null;
  observaciones?: string | null;
  recursos: RecursoComprometido[];
}

/** Tablero de monitoreo del Centro Coordinador. */
export interface EstadoEjecucion {
  emergenciaId: number;
  caseId: number;
  estado: 'EN_EJECUCION' | 'CIERRE_PENDIENTE' | 'FINALIZADA';
  zonaAfectada: string;
  nivelGravedad: string;
  fechaInicioEjecucion?: string | null;
  fechaCierreEjecucion?: string | null;
  coordinadorCierre?: string | null;
  motivoCierreEjecucion?: string | null;
  totalOngs: number;
  ongsFinalizadas: number;
  puedeCerrar: boolean;
  participaciones: ParticipacionEjecucion[];
}

/** Notificación de adjudicación que ve cada ONG. */
export interface AdjudicacionOng {
  emergenciaId: number;
  estadoEmergencia: string;
  zonaAfectada: string;
  nivelGravedad: string;
  descripcion: string;
  participacion: ParticipacionEjecucion;
}
