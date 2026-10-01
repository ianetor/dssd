export interface OfertaLocal {
  /** ID de la oferta (viene como string del backend ya que se serializa Long como string) */
  id: string;
  emergenciaId?: number;
  /** ID del lote al que aplica la oferta */
  loteId?: number | string;
  /** Nombre / tipo de recurso del lote */
  loteNombre?: string;
  cantidadOfrecida: number;
  unidad: string;
  tiempoLlegada: string;
  observaciones?: string;
  fechaHora?: string;
  estado: 'Registrada' | 'Rectificada' | 'Validada' | 'Retirada';
  /** Username de la ONG que realizó la oferta */
  ongLider?: string;
}
