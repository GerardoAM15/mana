// POST /auth/login (LoginRequestDTO).
export interface LoginRequest {
  correo: string;
  clave: string;
}

// Respuesta de POST /auth/login y GET /auth/me (LoginResponseDTO).
export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEnSegundos: number;
  usuarioId: number;
  nombreCompleto: string;
  rol: string;
  localId: number;
  localNombre: string;
  permisos: string[];
  // true si la contraseña con la que entró ya no cumple la política: debe cambiarla.
  debeCambiarClave?: boolean;
}

// Lo que se guarda en sessionStorage: el token y el instante (ms) en que vence.
export interface Sesion {
  token: string;
  usuarioId: number;
  nombreCompleto: string;
  rol: string;
  localId: number;
  localNombre: string;
  permisos: string[];
  expiraEn: number;
  debeCambiarClave?: boolean;
}

// POST /auth/recuperar (RecuperarClaveRequestDTO).
export interface RecuperarClaveRequest {
  correo: string;
}

// POST /auth/restablecer (RestablecerClaveRequestDTO).
export interface RestablecerClaveRequest {
  token: string;
  nuevaClave: string;
}

// POST /auth/cambiar-clave (CambiarClaveRequestDTO). Devuelve un LoginResponse con token nuevo.
export interface CambiarClaveRequest {
  claveActual: string;
  nuevaClave: string;
}

// MensajeResponseDTO.
export interface MensajeResponse {
  mensaje: string;
}
