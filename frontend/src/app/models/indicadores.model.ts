export interface MedicionEtapa {
  emergenciaId: number;
  caseId: number;
  tipoEmergencia: string;
  municipio: string;
  inicio: string;
  fin: string;
  segundos: number;
}

export interface TiempoPorTipo {
  tipoEmergencia: string;
  promedioSegundos: number;
  cantidad: number;
}

/** Tiempo de una etapa del proceso: base local (qué emergencia) + historial de Bonita (cuánto duró). */
export interface TiempoEtapa {
  etapa: string;
  promedioSegundos: number;
  minimoSegundos: number;
  maximoSegundos: number;
  cantidad: number;
  casosSinDatos: number;
  porTipo: TiempoPorTipo[];
  mediciones: MedicionEtapa[];
}

export interface EstadoCantidad {
  estado: string;
  cantidad: number;
}

/** Unidades comprometidas en emergencias que entraron en ejecución, por mes (yyyy-MM) y recurso. */
export interface SuministroMes {
  mes: string;
  tipoRecurso: string;
  unidad: string;
  cantidad: number;
}

/** Indicadores de la base local. */
export interface ResumenIndicadores {
  estados: EstadoCantidad[];
  suministros: SuministroMes[];
}
