import { Emergencia } from './emergencia.model';
import { OfertaLocal } from './oferta.model';
export interface CapacidadNacional {
  tipoRecurso: string; cantidadSolicitada: number; cantidadDisponible: number;
  rolPermitido: 'PRINCIPAL' | 'APOYO' | 'NO_HABILITADA';
  nivel?: number | null; riesgoMaximoPermitido?: string | null; certificacion?: string | null; motivo?: string | null;
}
export interface OfertaEvaluada {
  oferta: OfertaLocal;
  perfil: { resultado?: string | null; ongNombre?: string | null; nivelHabilitacion?: number | null; capacidades: CapacidadNacional[] };
  clasificacion: string; motivoExclusion?: string | null;
}
export interface LoteCobertura {
  loteId: number; tipoRecurso: string; cantidadRequerida: number; cantidadPrincipal: number;
  cantidadApoyo: number; cantidadHabilitada: number; porcentajeCobertura: number; coberturaMinimaAlcanzada: boolean;
}
export interface SnapshotCobertura {
  emergencia: Emergencia;
  cobertura: { coberturaSuficiente: boolean; fechaValidacion: string; totalLotes: number; lotesConCoberturaMinima: number; lotes: LoteCobertura[] };
  ofertas: OfertaEvaluada[];
}
export interface DecisionCobertura {
  id: string; ronda: number; decision: 'REABRIR' | 'CANCELAR'; motivo: string; coordinador: string;
  fecha: string; duracionMinutos?: number | null; estado: string; intentos: number; error?: string | null;
}
export interface RevisionCobertura {
  emergenciaId: number; caseId: number; ronda: number; estado: string; compatible: boolean; puedeDecidir: boolean;
  evaluacion: SnapshotCobertura;
  historial: { ronda: number; fecha: string; depurada: boolean; evaluacion: SnapshotCobertura }[];
  decisiones: DecisionCobertura[];
}
export interface BandejaCobertura {
  contenido: { emergenciaId: number; caseId: number; ronda: number; estado: string; municipio: string; zona: string; gravedad: string; compatible: boolean; sincronizacion?: string | null }[];
  pagina: number; totalPaginas: number; totalElementos: number;
}
export interface SolicitudDecision { ronda: number; decision: 'REABRIR' | 'CANCELAR'; motivo: string; duracionMinutos?: number; }
