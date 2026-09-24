export type RolUsuario =
  | 'OPERADOR_MUNICIPAL'
  | 'COORDINADOR_REGIONAL'
  | 'REPRESENTANTE_ONG'
  | 'AUDITOR_DIRECTIVO';

export interface UsuarioAutenticado {
  id: number;
  username: string;
  nombreCompleto: string;
  rol: RolUsuario;
  rolDisplayName: string;
  entidadId?: number;
  entidadNombre?: string;
}

export interface LoginCredentials {
  username: string;
  password: string;
}
