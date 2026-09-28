export interface OfertaLocal {
  id: string;
  loteId?: number | string;
  loteNombre: string;
  cantidadOfrecida: number;
  unidad: string;
  modalidad: 'Individual' | 'Consorcio';
  ongAsociada?: string;
  tiempoLlegada: string;
  observaciones?: string;
  fechaHora: string;
  estado: 'Registrada' | 'Rectificada / Versión 2' | 'Validada';
}
