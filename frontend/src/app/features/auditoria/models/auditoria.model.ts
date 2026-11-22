// GET /api/v1/auditoria (AuditoriaEventoResponseDTO).
export type AccionAuditoria = 'ANULAR_VENTA' | 'ANULAR_CONTRATO' | 'ANULAR_CONSUMO' | 'CERRAR_CAJA';

export const ACCIONES_AUDITORIA: { valor: AccionAuditoria; etiqueta: string }[] = [
  { valor: 'ANULAR_VENTA', etiqueta: 'Anular venta' },
  { valor: 'ANULAR_CONTRATO', etiqueta: 'Anular contrato' },
  { valor: 'ANULAR_CONSUMO', etiqueta: 'Anular consumo' },
  { valor: 'CERRAR_CAJA', etiqueta: 'Cerrar caja' },
];

export interface EventoAuditoria {
  id: number;
  fecha: string;
  usuario: string;
  accion: AccionAuditoria;
  entidad: string;
  entidadId: number;
  localId: number;
  detalle: string;
  requestId: string | null;
}

// localId null = todos los locales (solo el dueño); el backend fuerza el propio al administrador.
export interface FiltroAuditoria {
  localId: number | null;
  accion: AccionAuditoria | null;
  usuario: string | null;
  desde: string | null;
  hasta: string | null;
}
