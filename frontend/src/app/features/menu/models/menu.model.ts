// MenuPlatoResponseDTO.
export interface MenuPlato {
  platoId: number;
  nombre: string;
  categoria: string;
}

// MenuResponseDTO: platos programados para una fecha y un servicio del local.
export interface MenuDia {
  id: number;
  localId: number;
  fecha: string;
  servicio: string;
  platos: MenuPlato[];
  version: number;
  atiende: boolean;
}

// MenuRequestDTO (PUT /menus): crea o reemplaza el menú de esa fecha y servicio.
export interface MenuRequest {
  fecha: string;
  servicio: string;
  platoIds: number[];
  version?: number;
}

// ClonarMenuRequestDTO. Sin servicios se clonan todos.
export interface ClonarMenuRequest {
  fechaOrigen: string;
  fechaDestino: string;
  servicios?: string[];
  versionesDestino?: Record<string, number>;
}

// CopiarCenaRequestDTO.
export interface CopiarCenaRequest {
  fecha: string;
  version?: number;
}

// Filtros de GET /menus.
export interface FiltroMenus {
  desde: string | null;
  hasta: string | null;
  servicio: string | null;
  localId: number | null;
}
